package es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoritosDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoritoLocalEntity item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FavoritoLocalEntity> items);

    @Query("SELECT * FROM favoritos_local ORDER BY sincronizado DESC, createdAt DESC, nombre ASC")
    LiveData<List<FavoritoLocalEntity>> getAllLive();

    @Query("DELETE FROM favoritos_local WHERE songKey = :songKey")
    void deleteBySongKey(String songKey);

    @Query("DELETE FROM favoritos_local WHERE sincronizado = 1")
    void deleteAllSynced();

    @Query("UPDATE favoritos_local SET rutaArchivo = :rutaNueva WHERE rutaArchivo = :rutaAntigua")
    void actualizarRutaCancion(String rutaAntigua, String rutaNueva);

    @Query("UPDATE favoritos_local SET rutaArchivo = :rutaNueva, nombre = :nombre, artista = :artista, album = :album WHERE rutaArchivo = :rutaAntigua")
    void actualizarCancion(String rutaAntigua, String rutaNueva, String nombre, String artista, String album);
}