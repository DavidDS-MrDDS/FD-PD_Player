package es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "canciones_ocultas")
public class CancionOcultaEntity {

    @PrimaryKey
    @NonNull
    private String rutaArchivo;

    @NonNull
    private String nombre;

    @NonNull
    private String artista;

    @NonNull
    private String album;

    private long createdAt;

    public CancionOcultaEntity(@NonNull String rutaArchivo,
                               @NonNull String nombre,
                               @NonNull String artista,
                               @NonNull String album,
                               long createdAt) {
        this.rutaArchivo = rutaArchivo;
        this.nombre = nombre;
        this.artista = artista;
        this.album = album;
        this.createdAt = createdAt;
    }

    @NonNull
    public String getRutaArchivo() {
        return rutaArchivo;
    }

    @NonNull
    public String getNombre() {
        return nombre;
    }

    @NonNull
    public String getArtista() {
        return artista;
    }

    @NonNull
    public String getAlbum() {
        return album;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}