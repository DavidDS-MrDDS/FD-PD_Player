package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.Manifest;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.adapters.CancionesAdapter;
import es.iesagora.fd_pdplayer.almacenamientoInterno.CancionesRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionesBinding;
import es.iesagora.fd_pdplayer.models.Cancion;

public class CancionesFragment extends Fragment {

    private FragmentCancionesBinding binding;
    private CancionesRepository repository;
    private CancionesAdapter adapter;

    private ListasViewModel listasViewModel;
    private FavoriteUploadRepository favoriteUploadRepository;
    private CancionesOcultasRepository cancionesOcultasRepository;

    private List<ListaEntity> listasActuales = new ArrayList<>();
    private List<Cancion> listaCancionesTodas = new ArrayList<>();
    private List<Cancion> listaCanciones = new ArrayList<>();

    private boolean ordenarMasNuevasPrimero = true;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCancionesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        listasViewModel = new ViewModelProvider(requireActivity()).get(ListasViewModel.class);
        favoriteUploadRepository = new FavoriteUploadRepository(requireActivity().getApplication());

        repository = new CancionesRepository(requireContext());
        cancionesOcultasRepository = new CancionesOcultasRepository(requireContext());

        listasViewModel.obtenerListas().observe(getViewLifecycleOwner(), listas -> {
            listasActuales = listas != null ? listas : new ArrayList<>();
        });

        ActivityResultLauncher<String> permisoAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        cargarCanciones();
                    } else {
                        Toast.makeText(requireContext(),
                                "Permiso denegado, no se pueden mostrar canciones",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );

        adapter = new CancionesAdapter(requireContext(), new ArrayList<>(), new CancionesAdapter.Listener() {
            @Override
            public void onOpcionesCancion(View anchor, Cancion cancion) {
                mostrarMenuCancion(anchor, cancion);
            }

            @Override
            public void onClickCancion(Cancion cancion) {
                abrirCancion(cancion);
            }
        });

        binding.recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 1));
        binding.recyclerView.setAdapter(adapter);

        binding.btnSort.setOnClickListener(v -> {
            ordenarMasNuevasPrimero = !ordenarMasNuevasPrimero;
            cargarCanciones();

            String mensaje = ordenarMasNuevasPrimero
                    ? "Ordenadas por más nuevas"
                    : "Ordenadas por más antiguas";

            Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show();
        });

        configurarBuscador();

        String permiso;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permiso = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permiso = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permiso)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cargarCanciones();
        } else {
            permisoAudioLauncher.launch(permiso);
        }
    }

    private void configurarBuscador() {
        binding.btnRecargarCanciones.setOnClickListener(v -> cargarCanciones());

        binding.etBuscadorCanciones.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> aplicarFiltroCanciones();
                searchHandler.postDelayed(searchRunnable, 250);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void abrirCancion(Cancion cancion) {
        int posicion = listaCanciones.indexOf(cancion);

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);
        bundle.putSerializable("listaCanciones", new ArrayList<>(listaCanciones));
        bundle.putInt("posicion", posicion);

        NavHostFragment.findNavController(this)
                .navigate(R.id.cancionFragment, bundle);
    }

    private void mostrarMenuCancion(View anchor, Cancion cancion) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_cancion);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.action_add_to_favorites) {
                subirAFavoritos(cancion);
                return true;
            }

            if (itemId == R.id.action_add_to_list) {
                mostrarDialogSeleccionLista(cancion);
                return true;
            }

            if (itemId == R.id.action_hide_song) {
                confirmarOcultarCancion(cancion);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void subirAFavoritos(Cancion cancion) {
        favoriteUploadRepository.subirCancionAFavoritos(cancion, new FavoriteUploadRepository.SimpleCallback() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mostrarDialogSeleccionLista(Cancion cancion) {
        if (listasActuales == null || listasActuales.isEmpty()) {
            Toast.makeText(requireContext(), "Primero crea una lista", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] nombres = new String[listasActuales.size()];
        for (int i = 0; i < listasActuales.size(); i++) {
            nombres[i] = listasActuales.get(i).getNombre();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Añadir a lista")
                .setItems(nombres, (dialog, which) -> {
                    ListaEntity listaSeleccionada = listasActuales.get(which);
                    listasViewModel.anadirCancionALista(listaSeleccionada.getId(), cancion);
                    Toast.makeText(requireContext(), "Añadida a " + listaSeleccionada.getNombre(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarOcultarCancion(Cancion cancion) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Ocultar canción")
                .setMessage("¿Quieres ocultar \"" + cancion.getNombre() + "\"?\n\nNo aparecerá en canciones ni en listas, pero seguirá en favoritos.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Ocultar", (dialog, which) -> ocultarCancion(cancion))
                .show();
    }

    private void ocultarCancion(Cancion cancion) {
        cancionesOcultasRepository.ocultarCancion(cancion);
        listasViewModel.quitarCancionDeTodasLasListas(cancion.getRutaArchivo());

        eliminarPorRuta(listaCancionesTodas, cancion.getRutaArchivo());
        aplicarFiltroCanciones();

        Toast.makeText(requireContext(), "Canción ocultada", Toast.LENGTH_SHORT).show();
    }

    private void cargarCanciones() {
        listaCancionesTodas = repository.getCancionesPorFecha(ordenarMasNuevasPrimero);
        aplicarFiltroCanciones();

        if (ordenarMasNuevasPrimero) {
            binding.btnSort.setText("Más nuevas");
        } else {
            binding.btnSort.setText("Más antiguas");
        }
    }

    private void aplicarFiltroCanciones() {
        String busqueda = binding.etBuscadorCanciones.getText() != null
                ? binding.etBuscadorCanciones.getText().toString().trim()
                : "";

        listaCanciones = filtrarCanciones(listaCancionesTodas, busqueda);
        adapter.establecerLista(listaCanciones);

        binding.tvCount.setText("Canciones disponibles [" + listaCanciones.size() + "]");

        if (listaCanciones.isEmpty()) {
            binding.recyclerView.setVisibility(View.GONE);
            binding.tvMensajeCanciones.setVisibility(View.VISIBLE);

            if (TextUtils.isEmpty(busqueda)) {
                binding.tvMensajeCanciones.setText("No hay canciones disponibles");
            } else {
                binding.tvMensajeCanciones.setText("No se encontraron canciones con esa búsqueda");
            }
        } else {
            binding.recyclerView.setVisibility(View.VISIBLE);
            binding.tvMensajeCanciones.setVisibility(View.GONE);
        }
    }

    private List<Cancion> filtrarCanciones(List<Cancion> canciones, String texto) {
        List<Cancion> resultado = new ArrayList<>();

        if (canciones == null) {
            return resultado;
        }

        if (TextUtils.isEmpty(texto)) {
            resultado.addAll(canciones);
            return resultado;
        }

        String filtro = texto.toLowerCase();

        for (Cancion cancion : canciones) {
            String nombre = safe(cancion.getNombre()).toLowerCase();
            String artista = safe(cancion.getArtista()).toLowerCase();
            String album = safe(cancion.getAlbum()).toLowerCase();

            if (nombre.contains(filtro) || artista.contains(filtro) || album.contains(filtro)) {
                resultado.add(cancion);
            }
        }

        return resultado;
    }

    private void eliminarPorRuta(List<Cancion> canciones, String rutaArchivo) {
        if (canciones == null || rutaArchivo == null) return;

        for (int i = canciones.size() - 1; i >= 0; i--) {
            Cancion c = canciones.get(i);

            if (rutaArchivo.equals(c.getRutaArchivo())) {
                canciones.remove(i);
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }

        binding = null;
    }
}