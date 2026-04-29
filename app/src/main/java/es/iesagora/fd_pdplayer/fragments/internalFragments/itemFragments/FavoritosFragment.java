package es.iesagora.fd_pdplayer.fragments.internalFragments.itemFragments;

import android.os.Bundle;
import android.view.View;
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

    private int estadoFavoritos = FavoriteUploadRepository.ESTADO_FAVORITOS_IDLE;
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
                Toast.makeText(requireContext(), item.getNombre(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(FavoriteItem item) {
                if (!sesionIniciada) {
                    Toast.makeText(requireContext(), "Inicia sesión para eliminar favoritos", Toast.LENGTH_SHORT).show();
                    return;
                }

                favoriteUploadRepository.eliminarFavorito(item.getSongKey(), new FavoriteUploadRepository.SimpleCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
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
            binding.tvTitulo.setText("Descargando favoritos...");
            favoriteUploadRepository.sincronizarFavoritosDelUsuario(session.token, session.username);
        });

        favoriteUploadRepository.getEstadoFavoritosLive().observe(getViewLifecycleOwner(), estado -> {
            estadoFavoritos = estado != null ? estado : FavoriteUploadRepository.ESTADO_FAVORITOS_IDLE;

            if (!sesionIniciada) {
                return;
            }

            if (estadoFavoritos == FavoriteUploadRepository.ESTADO_FAVORITOS_CARGANDO) {
                binding.tvTitulo.setText("Cargando favoritos...");
                return;
            }

            if (estadoFavoritos == FavoriteUploadRepository.ESTADO_FAVORITOS_DESCARGANDO) {
                binding.tvTitulo.setText("Descargando favoritos...");
                return;
            }

            mostrarFavoritosLocales(ultimosLocales);
        });

        favoritosLocalRepository.getFavoritosLive().observe(getViewLifecycleOwner(), locales -> {
            ultimosLocales = locales != null ? locales : new ArrayList<>();

            if (!sesionIniciada) {
                return;
            }

            if (estadoFavoritos != FavoriteUploadRepository.ESTADO_FAVORITOS_IDLE) {
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}