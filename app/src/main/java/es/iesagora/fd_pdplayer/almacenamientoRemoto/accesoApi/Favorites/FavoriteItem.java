package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;

import com.google.gson.annotations.SerializedName;

public class FavoriteItem {

    private int id;

    @SerializedName("user_id")
    private int userId;

    @SerializedName("song_key")
    private String songKey;

    private String nombre;
    private String artista;
    private String album;

    @SerializedName("ruta_archivo")
    private String rutaArchivo;

    @SerializedName("storage_path")
    private String storagePath;

    @SerializedName("created_at")
    private String createdAt;

    private String username;

    public FavoriteItem() {
    }

    public FavoriteItem(int id, int userId, String songKey, String nombre, String artista, String album,
                        String rutaArchivo, String storagePath, String createdAt, String username) {
        this.id = id;
        this.userId = userId;
        this.songKey = songKey;
        this.nombre = nombre;
        this.artista = artista;
        this.album = album;
        this.rutaArchivo = rutaArchivo;
        this.storagePath = storagePath;
        this.createdAt = createdAt;
        this.username = username;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getSongKey() {
        return songKey;
    }

    public String getNombre() {
        return nombre;
    }

    public String getArtista() {
        return artista;
    }

    public String getAlbum() {
        return album;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setSongKey(String songKey) {
        this.songKey = songKey;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setArtista(String artista) {
        this.artista = artista;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}