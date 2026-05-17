package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;
import java.util.List;

public class FavoriteListResponse {
    private List<FavoriteItem> songs;

    public FavoriteListResponse(List<FavoriteItem> songs) {
        this.songs = songs;
    }

    public List<FavoriteItem> getSongs() { return songs; }
}
