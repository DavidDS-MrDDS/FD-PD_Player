package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;

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

    @FormUrlEncoded
    @POST("api/favorites/get-upload-url")
    Call<UploadUrlResponse> getUploadUrl(
            @Header("Authorization") String token,
            @Field("song_key") String songKey
    );

    @PUT
    Call<Void> uploadFileToSupabase(
            @Url String url,
            @Header("cache-control") String cacheControl,
            @Header("content-type") String contentType,
            @Header("x-upsert") String upsert,
            @Body RequestBody audioFile
    );

    @FormUrlEncoded
    @POST("api/favorites/confirm-upload")
    Call<AddFavoriteResponse> confirmUpload(
            @Header("Authorization") String token,
            @Field("song_key") String songKey,
            @Field("nombre") String nombre,
            @Field("artista") String artista,
            @Field("album") String album
    );

    @DELETE("api/favorites/{songKey}")
    Call<MessageResponse> deleteFavorite(
            @Header("Authorization") String token,
            @Path("songKey") String songKey
    );
}