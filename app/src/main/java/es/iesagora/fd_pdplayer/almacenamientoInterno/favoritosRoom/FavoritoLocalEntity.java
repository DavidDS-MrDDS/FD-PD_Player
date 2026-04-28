package es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favoritos_local")
public class FavoritoLocalEntity {

    @PrimaryKey
    @NonNull
    private String songKey;

    private int userId;

    @NonNull
    private String nombre;

    @NonNull
    private String artista;

    @NonNull
    private String album;

    @NonNull
    private String rutaArchivo;

    @NonNull
    private String storagePath;

    @NonNull
    private String username;

    @NonNull
    private String createdAt;

    private boolean sincronizado;

    public FavoritoLocalEntity(@NonNull String songKey,
                               int userId,
                               @NonNull String nombre,
                               @NonNull String artista,
                               @NonNull String album,
                               @NonNull String rutaArchivo,
                               @NonNull String storagePath,
                               @NonNull String username,
                               @NonNull String createdAt,
                               boolean sincronizado) {
        this.songKey = songKey;
        this.userId = userId;
        this.nombre = nombre;
        this.artista = artista;
        this.album = album;
        this.rutaArchivo = rutaArchivo;
        this.storagePath = storagePath;
        this.username = username;
        this.createdAt = createdAt;
        this.sincronizado = sincronizado;
    }

    @NonNull
    public String getSongKey() {
        return songKey;
    }

    public int getUserId() {
        return userId;
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

    @NonNull
    public String getRutaArchivo() {
        return rutaArchivo;
    }

    @NonNull
    public String getStoragePath() {
        return storagePath;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isSincronizado() {
        return sincronizado;
    }
}