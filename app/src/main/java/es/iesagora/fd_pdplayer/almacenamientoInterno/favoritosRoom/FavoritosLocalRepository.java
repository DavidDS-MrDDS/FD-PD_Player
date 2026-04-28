package es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;

public class FavoritosLocalRepository {

    private final FavoritosDao dao;
    private final LiveData<List<FavoritoLocalEntity>> favoritosLive;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FavoritosLocalRepository(Application application) {
        dao = FavoritosDatabase.getInstance(application).favoritosDao();
        favoritosLive = dao.getAllLive();
    }

    public LiveData<List<FavoritoLocalEntity>> getFavoritosLive() {
        return favoritosLive;
    }

    public void insertOrReplace(FavoritoLocalEntity item) {
        executor.execute(() -> dao.insert(item));
    }

    public void deleteBySongKey(String songKey) {
        executor.execute(() -> dao.deleteBySongKey(songKey));
    }

    public void replaceAllForCurrentUser(List<FavoriteItem> remotos, String fallbackUsername) {
        executor.execute(() -> {
            dao.deleteAllSynced();

            List<FavoritoLocalEntity> items = new ArrayList<>();
            if (remotos != null) {
                for (FavoriteItem item : remotos) {
                    items.add(new FavoritoLocalEntity(
                            item.getSongKey() != null ? item.getSongKey() : "",
                            item.getUserId(),
                            item.getNombre() != null ? item.getNombre() : "",
                            item.getArtista() != null ? item.getArtista() : "",
                            item.getAlbum() != null ? item.getAlbum() : "",
                            item.getRutaArchivo() != null ? item.getRutaArchivo() : "",
                            item.getStoragePath() != null ? item.getStoragePath() : "",
                            item.getUsername() != null && !item.getUsername().isEmpty() ? item.getUsername() : fallbackUsername,
                            item.getCreatedAt() != null ? item.getCreatedAt() : "",
                            true
                    ));
                }
            }

            dao.insertAll(items);
        });
    }
}