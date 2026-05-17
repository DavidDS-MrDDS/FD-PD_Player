package es.iesagora.fd_pdplayer.parteVisual.fragments.internalFragments.listFragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.funcionamiento.otros.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.FavoritosRemotosAdapter;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritoLocalEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthViewModel;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentFavoritosBinding;

public class FavoritosFragment extends Fragment {

    private FragmentFavoritosBinding binding;
    private FavoritosRemotosAdapter adapter;
    private FavoritosLocalRepository favoritosLocalRepository;
    private FavoriteUploadRepository favoriteUploadRepository;
    private AuthViewModel authViewModel;

    private boolean sesionIniciada = false;

    private List<FavoritoLocalEntity> ultimosLocales = new ArrayList<>();

    public FavoritosFragment() {
        super(es.iesagora.fd_pdplayer.R.layout.fragment_favoritos);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentFavoritosBinding.bind(view);

        favoritosLocalRepository = new FavoritosLocalRepository(requireActivity().getApplication());
        favoriteUploadRepository = new FavoriteUploadRepository(requireActivity().getApplication());
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        adapter = new FavoritosRemotosAdapter(false, true, new FavoritosRemotosAdapter.Listener() {
            @Override
            public void onClick(FavoriteItem item) {
                if (binding != null) {
                    VentanasApp.mostrarMensaje(binding.getRoot(), safe(item.getNombre()));
                }
            }

            @Override
            public void onDelete(FavoriteItem item) {
                if (!sesionIniciada) {
                    if (binding != null) {
                        VentanasApp.mostrarMensaje(binding.getRoot(), "Inicia sesión para eliminar favoritos");
                    }
                    return;
                }

                confirmarEliminarFavorito(item);
            }
        });

        binding.recyclerFavoritos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerFavoritos.setAdapter(adapter);

        authViewModel.getCurrentSession().observe(getViewLifecycleOwner(), session -> {
            sesionIniciada = session != null && session.token != null && !session.token.isEmpty();

            if (!sesionIniciada) {
                binding.tvTitulo.setText("Inicia sesión para ver tus favoritos");
                binding.recyclerFavoritos.setVisibility(View.GONE);
                adapter.setItems(new ArrayList<>());
                return;
            }

            binding.recyclerFavoritos.setVisibility(View.VISIBLE);

            // Muestra los favoritos guardados localmente, sin intentar descargarlos automáticamente.
            mostrarFavoritosLocales(ultimosLocales);
        });

        favoritosLocalRepository.getFavoritosLive().observe(getViewLifecycleOwner(), locales -> {
            ultimosLocales = locales != null ? locales : new ArrayList<>();

            if (!sesionIniciada) {
                return;
            }

            mostrarFavoritosLocales(ultimosLocales);
        });
    }

    private void mostrarFavoritosLocales(List<FavoritoLocalEntity> locales) {
        List<FavoriteItem> items = new ArrayList<>();

        if (locales != null) {
            for (FavoritoLocalEntity local : locales) {
                FavoriteItem item = new FavoriteItem();
                item.setSongKey(local.getSongKey());
                item.setUserId(local.getUserId());
                item.setNombre(local.getNombre());
                item.setArtista(local.getArtista());
                item.setAlbum(local.getAlbum());
                item.setRutaArchivo(local.getRutaArchivo());
                item.setStoragePath(local.getStoragePath());
                item.setUsername(local.getUsername());
                item.setCreatedAt(local.getCreatedAt());
                items.add(item);
            }
        }

        binding.tvTitulo.setText("Favoritos (" + items.size() + ")");
        adapter.setItems(items);
    }

    private void confirmarEliminarFavorito(FavoriteItem item) {
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "BorrarFavorito",
                "Eliminar favorito",
                "¿Quieres eliminar \"" + safe(item.getNombre()) + "\" de tus favoritos?\n\nLa canción no se borrará del dispositivo.",
                "Eliminar",
                () -> eliminarFavorito(item)
        );
    }

    private void eliminarFavorito(FavoriteItem item) {
        favoriteUploadRepository.eliminarFavorito(item.getSongKey(), new FavoriteUploadRepository.SimpleCallback() {
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}