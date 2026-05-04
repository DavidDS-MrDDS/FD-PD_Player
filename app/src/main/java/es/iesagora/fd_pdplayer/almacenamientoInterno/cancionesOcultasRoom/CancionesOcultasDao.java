package es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CancionesOcultasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void ocultarCancion(CancionOcultaEntity cancion);

    @Query("DELETE FROM canciones_ocultas WHERE rutaArchivo = :rutaArchivo")
    void desocultarCancion(String rutaArchivo);

    @Query("SELECT * FROM canciones_ocultas ORDER BY createdAt DESC")
    LiveData<List<CancionOcultaEntity>> obtenerOcultasLive();

    @Query("SELECT rutaArchivo FROM canciones_ocultas")
    List<String> obtenerRutasOcultasSync();
}