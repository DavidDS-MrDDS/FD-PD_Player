package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;
import java.util.List;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface FavoritesApiService {
    @GET("api/favorites")
    Call<FavoriteListResponse> getMyFavorites(@Header("Authorization") String token);

    @GET("api/public/favorites")
    Call<FavoriteListResponse> getPublicFavorites(
            @Header("Authorization") String token,
            @Query("search") String search,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @Multipart
    @POST("api/favorites/upload")
    Call<AddFavoriteResponse> uploadFavorite(
            @Header("Authorization") String token,
            @Part("song_key") RequestBody songKey,
            @Part("nombre") RequestBody nombre,
            @Part("artista") RequestBody artista,
            @Part("album") RequestBody album,
            @Part MultipartBody.Part audio
    );

    @DELETE("api/favorites/{songKey}")
    Call<MessageResponse> deleteFavorite(
            @Header("Authorization") String token,
            @Path("songKey") String songKey
    );
}
