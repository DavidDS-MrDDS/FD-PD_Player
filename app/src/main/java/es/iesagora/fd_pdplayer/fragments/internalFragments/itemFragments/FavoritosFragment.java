package es.iesagora.fd_pdplayer.fragments.internalFragments.itemFragments;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.adapters.FavoritosRemotosAdapter;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritoLocalEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentFavoritosBinding;

public class FavoritosFragment extends Fragment {

    private FragmentFavoritosBinding binding;
    private FavoritosRemotosAdapter adapter;
    private FavoritosLocalRepository favoritosLocalRepository;
    private FavoriteUploadRepository favoriteUploadRepository;
    private AuthViewModel authViewModel;

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
                Toast.makeText(requireContext(), item.getNombre(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(FavoriteItem item) {
                favoriteUploadRepository.eliminarFavorito(item.getSongKey(), new FavoriteUploadRepository.SimpleCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        favoriteUploadRepository.sincronizarFavoritosDelUsuario();
                    }

                    @Override
                    public void onError(String message) {
                        new android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Error al eliminar favorito")
                                .setMessage(message)
                                .setPositiveButton("Aceptar", null)
                                .show();
                    }
                });
            }
        });

        binding.recyclerFavoritos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerFavoritos.setAdapter(adapter);

        authViewModel.getCurrentSession().observe(getViewLifecycleOwner(), session -> {
            if (session != null && session.token != null && !session.token.isEmpty()) {
                favoriteUploadRepository.sincronizarFavoritosDelUsuario();
            }
        });

        favoritosLocalRepository.getFavoritosLive().observe(getViewLifecycleOwner(), this::mostrarFavoritosLocales);
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
}