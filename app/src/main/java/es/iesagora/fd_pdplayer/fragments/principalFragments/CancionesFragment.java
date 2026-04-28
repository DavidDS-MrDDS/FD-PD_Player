package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.Manifest;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
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

    private List<ListaEntity> listasActuales = new ArrayList<>();
    private List<Cancion> listaCanciones = new ArrayList<>();

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

        repository = new CancionesRepository(requireContext());

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

    private void abrirCancion(Cancion cancion) {
        int posicion = listaCanciones.indexOf(cancion);

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);
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

    private void cargarCanciones() {
        listaCanciones = repository.getCanciones();
        adapter.establecerLista(listaCanciones);
        binding.tvCount.setText("ALL SONGS (" + (listaCanciones != null ? listaCanciones.size() : 0) + ")");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}