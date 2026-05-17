package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;

public class AddFavoriteResponse {

    private String message;
    private FavoriteItem favorite;

    public AddFavoriteResponse(String message, FavoriteItem favorite) {
        this.message = message;
        this.favorite = favorite;
    }

    public String getMessage() {
        return message;
    }

    public FavoriteItem getFavorite() {
        return favorite;
    }
}