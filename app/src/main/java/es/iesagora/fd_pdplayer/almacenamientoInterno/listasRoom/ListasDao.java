package es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface ListasDao {

    @Insert
    void insertarLista(ListaEntity lista);

    @Query("DELETE FROM lista WHERE id = :listaId")
    void borrarListaPorId(int listaId);

    @Query("SELECT * FROM lista ORDER BY nombre ASC")
    LiveData<List<ListaEntity>> obtenerListas();

    @Transaction
    @Query("SELECT * FROM lista WHERE id = :listaId LIMIT 1")
    LiveData<ListaCanciones> obtenerListaConCanciones(int listaId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertarCancionEnLista(CancionEnListaEntity cancion);

    @Query("DELETE FROM cancion_en_lista WHERE listaId = :listaId AND rutaArchivo = :ruta")
    void quitarCancionDeLista(int listaId, String ruta);

    @Query("DELETE FROM cancion_en_lista WHERE rutaArchivo = :ruta")
    void quitarCancionDeTodasLasListas(String ruta);

    @Query("UPDATE cancion_en_lista SET rutaArchivo = :rutaNueva WHERE rutaArchivo = :rutaAntigua")
    void actualizarRutaCancion(String rutaAntigua, String rutaNueva);
}