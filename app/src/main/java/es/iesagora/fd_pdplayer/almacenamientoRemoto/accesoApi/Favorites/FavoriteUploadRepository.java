package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.List;

import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritoLocalEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionRepository;
import es.iesagora.fd_pdplayer.models.Cancion;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteUploadRepository {

    public interface SimpleCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private final FavoritesApiService apiService;
    private final SessionRepository sessionRepository;
    private final FavoritosLocalRepository favoritosLocalRepository;

    public FavoriteUploadRepository(Application application) {
        apiService = ApiClient.getFavoritesApiService();
        sessionRepository = new SessionRepository(application);
        favoritosLocalRepository = new FavoritosLocalRepository(application);
    }

    public void subirCancionAFavoritos(@NonNull Cancion cancion, @NonNull SimpleCallback callback) {
        SessionEntity session = sessionRepository.getSessionSync();

        if (session == null || TextUtils.isEmpty(session.token)) {
            callback.onError("Debes iniciar sesión para subir favoritos.");
            return;
        }

        File file = new File(cancion.getRutaArchivo());
        String songKey = generarSongKey(cancion);
        String album = cancion.getAlbum() != null ? cancion.getAlbum() : "";

        if (!file.exists()) {
            guardarSoloLocal(cancion, session, songKey);
            callback.onError(
                    "No se encontró el archivo de audio.\n\n" +
                            "La canción se ha añadido solo en favoritos locales.\n" +
                            "No se ha podido sincronizar con el servidor."
            );
            return;
        }

        RequestBody rbSongKey = RequestBody.create(songKey, MediaType.parse("text/plain"));
        RequestBody rbNombre = RequestBody.create(cancion.getNombre(), MediaType.parse("text/plain"));
        RequestBody rbArtista = RequestBody.create(cancion.getArtista(), MediaType.parse("text/plain"));
        RequestBody rbAlbum = RequestBody.create(album, MediaType.parse("text/plain"));

        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        MultipartBody.Part audioPart = MultipartBody.Part.createFormData("audio", file.getName(), fileBody);

        apiService.uploadFavorite("Bearer " + session.token, rbSongKey, rbNombre, rbArtista, rbAlbum, audioPart)
                .enqueue(new Callback<AddFavoriteResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AddFavoriteResponse> call,
                                           @NonNull Response<AddFavoriteResponse> response) {
                        if (!response.isSuccessful()) {
                            String errorMsg = "No se pudo subir el favorito al servidor.\n";
                            errorMsg += "Código HTTP: " + response.code();

                            try {
                                if (response.errorBody() != null) {
                                    String serverBody = response.errorBody().string();
                                    if (serverBody != null && !serverBody.trim().isEmpty()) {
                                        errorMsg += "\n\nRespuesta del servidor:\n" + serverBody;
                                    }
                                }
                            } catch (Exception e) {
                                errorMsg += "\n\nNo se pudo leer el cuerpo del error: " + e.getMessage();
                            }

                            guardarSoloLocal(cancion, session, songKey);
                            errorMsg += "\n\nLa canción se ha añadido solo en favoritos locales.";
                            callback.onError(errorMsg);
                            return;
                        }

                        if (response.body() == null || response.body().getFavorite() == null) {
                            guardarSoloLocal(cancion, session, songKey);
                            callback.onError(
                                    "Respuesta vacía o inválida del servidor.\n\n" +
                                            "La canción se ha añadido solo en favoritos locales."
                            );
                            return;
                        }

                        FavoriteItem remote = response.body().getFavorite();
                        favoritosLocalRepository.insertOrReplace(mapToLocal(remote, session.username));

                        callback.onSuccess(
                                response.body().getMessage() != null
                                        ? response.body().getMessage()
                                        : "Añadido a favoritos y sincronizado correctamente."
                        );
                    }

                    @Override
                    public void onFailure(@NonNull Call<AddFavoriteResponse> call, @NonNull Throwable t) {
                        guardarSoloLocal(cancion, session, songKey);

                        String detail = t.getMessage() != null ? t.getMessage() : "Error de red desconocido.";
                        callback.onError(
                                "Error de red al subir el favorito.\n\n" +
                                        detail +
                                        "\n\nLa canción se ha añadido solo en favoritos locales."
                        );
                    }
                });
    }

    public void eliminarFavorito(@NonNull String songKey, @NonNull SimpleCallback callback) {
        SessionEntity session = sessionRepository.getSessionSync();

        if (session == null || TextUtils.isEmpty(session.token)) {
            favoritosLocalRepository.deleteBySongKey(songKey);
            callback.onError(
                    "No hay sesión activa.\n\n" +
                            "El favorito se ha eliminado solo en local."
            );
            return;
        }

        apiService.deleteFavorite("Bearer " + session.token, songKey)
                .enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MessageResponse> call,
                                           @NonNull Response<MessageResponse> response) {
                        if (!response.isSuccessful()) {
                            favoritosLocalRepository.deleteBySongKey(songKey);

                            String errorMsg = "No se pudo eliminar el favorito del servidor.\n";
                            errorMsg += "Código HTTP: " + response.code();

                            try {
                                if (response.errorBody() != null) {
                                    String body = response.errorBody().string();
                                    if (body != null && !body.trim().isEmpty()) {
                                        errorMsg += "\n\nRespuesta del servidor:\n" + body;
                                    }
                                }
                            } catch (Exception e) {
                                errorMsg += "\n\nNo se pudo leer el cuerpo del error: " + e.getMessage();
                            }

                            errorMsg += "\n\nEl favorito se ha eliminado solo en local.";
                            callback.onError(errorMsg);
                            return;
                        }

                        favoritosLocalRepository.deleteBySongKey(songKey);

                        String message = response.body() != null && response.body().getMessage() != null
                                ? response.body().getMessage()
                                : "Favorito eliminado.";

                        callback.onSuccess(message);
                    }

                    @Override
                    public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                        favoritosLocalRepository.deleteBySongKey(songKey);

                        String detail = t.getMessage() != null ? t.getMessage() : "Error de red desconocido.";
                        callback.onError(
                                "Error de red al eliminar el favorito remoto.\n\n" +
                                        detail +
                                        "\n\nEl favorito se ha eliminado solo en local."
                        );
                    }
                });
    }

    public void sincronizarFavoritosDelUsuario() {
        SessionEntity session = sessionRepository.getSessionSync();
        if (session == null || TextUtils.isEmpty(session.token)) return;

        apiService.getMyFavorites("Bearer " + session.token).enqueue(new Callback<FavoriteListResponse>() {
            @Override
            public void onResponse(@NonNull Call<FavoriteListResponse> call,
                                   @NonNull Response<FavoriteListResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                favoritosLocalRepository.replaceAllForCurrentUser(response.body().getSongs(), session.username);
            }

            @Override
            public void onFailure(@NonNull Call<FavoriteListResponse> call, @NonNull Throwable t) {
            }
        });
    }

    private void guardarSoloLocal(@NonNull Cancion cancion, @NonNull SessionEntity session, @NonNull String songKey) {
        FavoritoLocalEntity local = new FavoritoLocalEntity(
                songKey,
                session.userId,
                safe(cancion.getNombre()),
                safe(cancion.getArtista()),
                safe(cancion.getAlbum()),
                safe(cancion.getRutaArchivo()),
                "",
                safe(session.username),
                String.valueOf(System.currentTimeMillis()),
                false
        );

        favoritosLocalRepository.insertOrReplace(local);
    }

    private String generarSongKey(Cancion c) {
        String base = (safe(c.getNombre()) + "|" + safe(c.getArtista()) + "|" + safe(c.getAlbum()))
                .toLowerCase()
                .trim();
        return String.valueOf(base.hashCode()).replace("-", "song_");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private FavoritoLocalEntity mapToLocal(FavoriteItem item, String defaultUsername) {
        return new FavoritoLocalEntity(
                item.getSongKey() != null ? item.getSongKey() : "",
                item.getUserId(),
                item.getNombre() != null ? item.getNombre() : "",
                item.getArtista() != null ? item.getArtista() : "",
                item.getAlbum() != null ? item.getAlbum() : "",
                item.getRutaArchivo() != null ? item.getRutaArchivo() : "",
                item.getStoragePath() != null ? item.getStoragePath() : "",
                item.getUsername() != null && !item.getUsername().isEmpty() ? item.getUsername() : defaultUsername,
                item.getCreatedAt() != null ? item.getCreatedAt() : "",
                true
        );
    }
}
