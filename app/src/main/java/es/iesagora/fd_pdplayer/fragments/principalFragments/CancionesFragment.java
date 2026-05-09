package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.Manifest;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.MainActivity;
import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.CancionesRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionesBinding;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.CancionesAdapter;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

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

    private ActivityResultLauncher<String> permisoAudioLauncher;

    private static final int ORDEN_MAS_NUEVO = 0;
    private static final int ORDEN_MAS_ANTIGUO = 1;
    private static final int ORDEN_A_Z = 2;
    private static final int ORDEN_Z_A = 3;

    private int modoOrden = ORDEN_MAS_NUEVO;

    private static final String NOMBRE_CARPETA_ORGANIZADA = "FD-PD_Player_Canciones";

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final ExecutorService executorCanciones = Executors.newSingleThreadExecutor();
    private int versionCarga = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permisoAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        cargarCanciones();
                    } else {
                        Toast.makeText(
                                requireContext(),
                                "Permiso denegado, no se pueden mostrar canciones",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

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

        binding.btnSort.setOnClickListener(v -> mostrarMenuOrdenCanciones());

        configurarBuscador();

        esperarPermisoNotificacionesYComprobarAudio();
    }

    private void esperarPermisoNotificacionesYComprobarAudio() {
        if (!isAdded() || binding == null) return;

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).ejecutarCuandoPermisosInicialesTerminen(
                    this::comprobarPermisoAudioYCargar
            );
        } else {
            comprobarPermisoAudioYCargar();
        }
    }

    private void comprobarPermisoAudioYCargar() {
        if (binding == null || !isAdded()) return;

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

    private void mostrarMenuOrdenCanciones() {
        String[] opciones = {
                "Ordenar de más nuevo a más viejo",
                "Ordenar de más viejo a más nuevo",
                "Ordenar alfabéticamente A -> Z",
                "Ordenar alfabéticamente Z -> A"
        };

        VentanasApp.mostrarMenu(
                requireContext(),
                "OrdenCanciones",
                "Ordenar canciones",
                "Elige cómo quieres ordenar la lista.",
                opciones,
                (posicion, texto) -> {
                    modoOrden = posicion;
                    cargarCanciones();
                    VentanasApp.mostrarMensaje(binding.getRoot(), texto);
                }
        );
    }

    private void abrirCancion(Cancion cancion) {
        if (listaCanciones == null || listaCanciones.isEmpty()) {
            Toast.makeText(requireContext(), "No hay canciones disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        int posicion = listaCanciones.indexOf(cancion);

        if (posicion < 0) {
            posicion = 0;
        }

        ReproductorApp.getInstance().liberar();

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);
        bundle.putSerializable("listaCanciones", new ArrayList<>(listaCanciones));
        bundle.putInt("posicion", posicion);

        NavHostFragment.findNavController(this)
                .navigate(R.id.cancionFragment, bundle);
    }

    private void mostrarMenuCancion(View anchor, Cancion cancion) {
        String[] opciones = {
                "Modificar canción",
                "Añadir a favoritos",
                "Añadir a lista",
                "Ocultar canción"
        };

        VentanasApp.mostrarMenu(
                requireContext(),
                "MenuCancion",
                "Opciones de canción",
                cancion.getNombre(),
                opciones,
                (posicion, texto) -> {
                    if (posicion == 0) {
                        abrirModificarCancion(cancion);
                    } else if (posicion == 1) {
                        subirAFavoritos(cancion);
                    } else if (posicion == 2) {
                        mostrarDialogSeleccionLista(cancion);
                    } else if (posicion == 3) {
                        confirmarOcultarCancion(cancion);
                    }
                }
        );
    }

    private void subirAFavoritos(Cancion cancion) {
        favoriteUploadRepository.subirCancionAFavoritos(cancion, new FavoriteUploadRepository.SimpleCallback() {
            @Override
            public void onSuccess(String message) {
                if (binding != null) {
                    VentanasApp.mostrarMensaje(binding.getRoot(), message);
                }
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    VentanasApp.mostrarMensaje(binding.getRoot(), message);
                }
            }
        });
    }

    private void mostrarDialogSeleccionLista(Cancion cancion) {
        if (listasActuales == null || listasActuales.isEmpty()) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "Primero crea una lista");
            return;
        }

        String[] nombres = new String[listasActuales.size()];
        for (int i = 0; i < listasActuales.size(); i++) {
            nombres[i] = listasActuales.get(i).getNombre();
        }

        VentanasApp.mostrarMenu(
                requireContext(),
                "MenuCancion_Listas",
                "Añadir a lista",
                cancion.getNombre(),
                nombres,
                (posicion, texto) -> {
                    ListaEntity listaSeleccionada = listasActuales.get(posicion);
                    listasViewModel.anadirCancionALista(listaSeleccionada.getId(), cancion);
                    VentanasApp.mostrarMensaje(binding.getRoot(), "Añadida a " + listaSeleccionada.getNombre());
                }
        );
    }

    private void abrirModificarCancion(Cancion cancion) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "No se encontró la ruta de la canción");
            return;
        }

        if (!estaEnCarpetaOrganizada(cancion.getRutaArchivo())) {
            VentanasApp.mostrarMensaje(
                    binding.getRoot(),
                    "Solo se pueden modificar canciones organizadas."
            );
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);

        NavHostFragment.findNavController(this)
                .navigate(R.id.modificarCancionFragment, bundle);
    }

    private void confirmarOcultarCancion(Cancion cancion) {
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "OcultarCancion",
                "Ocultar canción",
                "¿Quieres ocultar \"" + cancion.getNombre() + "\"?\n\nNo aparecerá en canciones ni en listas, pero seguirá en favoritos.",
                "Ocultar",
                () -> ocultarCancion(cancion)
        );
    }

    private void ocultarCancion(Cancion cancion) {
        cancionesOcultasRepository.ocultarCancion(cancion);
        listasViewModel.quitarCancionDeTodasLasListas(cancion.getRutaArchivo());

        eliminarPorRuta(listaCancionesTodas, cancion.getRutaArchivo());
        aplicarFiltroCanciones();

        VentanasApp.mostrarMensaje(binding.getRoot(), "Canción ocultada");
    }

    private void cargarCanciones() {
        if (binding == null) return;

        final int versionActual = ++versionCarga;

        setCargandoCanciones(true);

        executorCanciones.execute(() -> {
            List<Cancion> cancionesCargadas;

            if (modoOrden == ORDEN_MAS_NUEVO) {
                cancionesCargadas = repository.getCancionesPorFecha(true);
            } else if (modoOrden == ORDEN_MAS_ANTIGUO) {
                cancionesCargadas = repository.getCancionesPorFecha(false);
            } else {
                cancionesCargadas = repository.getCancionesPorFecha(true);
                ordenarListaAlfabeticamente(cancionesCargadas, modoOrden == ORDEN_A_Z);
            }

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (versionActual != versionCarga) return;

                listaCancionesTodas = cancionesCargadas != null ? cancionesCargadas : new ArrayList<>();

                aplicarFiltroCanciones();
                actualizarDescripcionOrden();
                setCargandoCanciones(false);
            });
        });
    }

    private void setCargandoCanciones(boolean cargando) {
        if (binding == null) return;

        binding.btnRecargarCanciones.setEnabled(!cargando);
        binding.btnSort.setEnabled(!cargando);

        binding.btnRecargarCanciones.setAlpha(cargando ? 0.55f : 1f);
        binding.btnSort.setAlpha(cargando ? 0.55f : 1f);
    }

    private void ordenarListaAlfabeticamente(List<Cancion> canciones, boolean ascendente) {
        if (canciones == null) return;

        Collections.sort(canciones, (c1, c2) -> {
            int resultado = safe(c1.getNombre()).compareToIgnoreCase(safe(c2.getNombre()));

            if (resultado == 0) {
                resultado = safe(c1.getArtista()).compareToIgnoreCase(safe(c2.getArtista()));
            }

            if (resultado == 0) {
                resultado = safe(c1.getAlbum()).compareToIgnoreCase(safe(c2.getAlbum()));
            }

            return ascendente ? resultado : -resultado;
        });
    }

    private void actualizarDescripcionOrden() {
        if (binding == null) return;

        if (modoOrden == ORDEN_MAS_NUEVO) {
            binding.btnSort.setContentDescription("Orden actual: de más nuevo a más viejo");
        } else if (modoOrden == ORDEN_MAS_ANTIGUO) {
            binding.btnSort.setContentDescription("Orden actual: de más viejo a más nuevo");
        } else if (modoOrden == ORDEN_A_Z) {
            binding.btnSort.setContentDescription("Orden actual: alfabéticamente A a Z");
        } else {
            binding.btnSort.setContentDescription("Orden actual: alfabéticamente Z a A");
        }
    }

    private void aplicarFiltroCanciones() {
        if (binding == null) return;

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

    private boolean estaEnCarpetaOrganizada(String rutaArchivo) {
        if (TextUtils.isEmpty(rutaArchivo)) {
            return false;
        }

        String rutaNormalizada = rutaArchivo.replace("\\", "/").toLowerCase();
        String carpetaNormalizada = "/" + NOMBRE_CARPETA_ORGANIZADA.toLowerCase() + "/";

        return rutaNormalizada.contains(carpetaNormalizada);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        versionCarga++;

        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }

        if (adapter != null) {
            adapter.liberar();
        }

        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorCanciones.shutdownNow();
    }
}