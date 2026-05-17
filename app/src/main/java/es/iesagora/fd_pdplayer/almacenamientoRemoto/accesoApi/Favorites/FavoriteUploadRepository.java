package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;

import android.app.Application;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritoLocalEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionRepository;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteUploadRepository {

    public interface SimpleCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private final Application application;
    private final FavoritesApiService apiService;
    private final SessionRepository sessionRepository;
    private final FavoritosLocalRepository favoritosLocalRepository;
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static final int ESTADO_FAVORITOS_IDLE = 0;
    public static final int ESTADO_FAVORITOS_CARGANDO = 1;
    public static final int ESTADO_FAVORITOS_DESCARGANDO = 2;

    private static final MutableLiveData<Integer> estadoFavoritos = new MutableLiveData<>(ESTADO_FAVORITOS_IDLE);

    public FavoriteUploadRepository(Application application) {
        this.application = application;
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

        if (!file.exists()) {
            guardarSoloLocal(cancion, session, songKey);
            callback.onError("No se encontró el archivo. La canción se ha añadido solo en local.");
            return;
        }

        String token = "Bearer " + session.token;

        apiService.getUploadUrl(token, songKey).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<UploadUrlResponse> call,
                                   @NonNull Response<UploadUrlResponse> response) {

                if (!response.isSuccessful() || response.body() == null || TextUtils.isEmpty(response.body().getSignedUrl())) {
                    guardarSoloLocal(cancion, session, songKey);
                    callback.onError("No se pudo obtener la URL de subida.");
                    return;
                }

                subirArchivoDirectoASupabase(
                        response.body().getSignedUrl(),
                        file,
                        () -> confirmarSubida(cancion, session, songKey, token, callback),
                        error -> {
                            guardarSoloLocal(cancion, session, songKey);
                            callback.onError("Error al subir a Supabase: " + error);
                        }
                );
            }

            @Override
            public void onFailure(@NonNull Call<UploadUrlResponse> call, @NonNull Throwable t) {
                guardarSoloLocal(cancion, session, songKey);
                callback.onError("Error de conexión al preparar la subida.");
            }
        });
    }

    private interface UploadSuccess {
        void run();
    }

    private interface UploadError {
        void run(String error);
    }

    private void subirArchivoDirectoASupabase(@NonNull String signedUrl,
                                              @NonNull File file,
                                              @NonNull UploadSuccess onSuccess,
                                              @NonNull UploadError onError) {

        RequestBody audioBody = RequestBody.create(file, MediaType.parse("audio/mpeg"));

        apiService.uploadFileToSupabase(
                signedUrl,
                "max-age=3600",
                "audio/mpeg",
                "true",
                audioBody
        ).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    onSuccess.run();
                } else {
                    onError.run("Código HTTP: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                onError.run(t.getMessage() != null ? t.getMessage() : "Error desconocido.");
            }
        });
    }

    private void confirmarSubida(@NonNull Cancion cancion,
                                 @NonNull SessionEntity session,
                                 @NonNull String songKey,
                                 @NonNull String token,
                                 @NonNull SimpleCallback callback) {

        apiService.confirmUpload(
                token,
                songKey,
                safe(cancion.getNombre()),
                safe(cancion.getArtista()),
                safe(cancion.getAlbum())
        ).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<AddFavoriteResponse> call,
                                   @NonNull Response<AddFavoriteResponse> response) {

                if (!response.isSuccessful() || response.body() == null || response.body().getFavorite() == null) {
                    guardarSoloLocal(cancion, session, songKey);
                    callback.onError("El archivo se subió, pero no se pudo guardar el favorito.");
                    return;
                }

                FavoriteItem remote = response.body().getFavorite();

                favoritosLocalRepository.insertOrReplace(new FavoritoLocalEntity(
                        songKey,
                        session.userId,
                        safe(cancion.getNombre()),
                        safe(cancion.getArtista()),
                        safe(cancion.getAlbum()),
                        safe(cancion.getRutaArchivo()),
                        safe(remote.getStoragePath()),
                        safe(session.username),
                        safe(remote.getCreatedAt()),
                        true
                ));

                callback.onSuccess(
                        response.body().getMessage() != null
                                ? response.body().getMessage()
                                : "Canción añadida a favoritos."
                );
            }

            @Override
            public void onFailure(@NonNull Call<AddFavoriteResponse> call, @NonNull Throwable t) {
                guardarSoloLocal(cancion, session, songKey);
                callback.onError("Error de conexión al guardar el favorito.");
            }
        });
    }

    public void eliminarFavorito(@NonNull String songKey, @NonNull SimpleCallback callback) {
        SessionEntity session = sessionRepository.getSessionSync();

        if (session == null || TextUtils.isEmpty(session.token)) {
            callback.onError("Debes iniciar sesión para eliminar favoritos.");
            return;
        }

        apiService.deleteFavorite("Bearer " + session.token, songKey).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call,
                                   @NonNull Response<MessageResponse> response) {

                if (response.isSuccessful()) {
                    favoritosLocalRepository.deleteBySongKey(songKey);
                    borrarArchivoDescargado(songKey);

                    String message = response.body() != null && response.body().getMessage() != null
                            ? response.body().getMessage()
                            : "Favorito eliminado.";

                    callback.onSuccess(message);
                } else {
                    callback.onError("Error al eliminar el favorito. Código: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                callback.onError("Error de conexión al eliminar favorito.");
            }
        });
    }

    public void sincronizarFavoritosDelUsuario(@NonNull String rawToken, @NonNull String fallbackUsername) {
        estadoFavoritos.postValue(ESTADO_FAVORITOS_CARGANDO);

        apiService.getMyFavorites("Bearer " + rawToken).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<FavoriteListResponse> call,
                                   @NonNull Response<FavoriteListResponse> response) {

                if (!response.isSuccessful() || response.body() == null || response.body().getSongs() == null) {
                    estadoFavoritos.postValue(ESTADO_FAVORITOS_IDLE);
                    return;
                }

                List<FavoriteItem> remotos = response.body().getSongs();

                if (remotos.isEmpty()) {
                    favoritosLocalRepository.replaceAllForCurrentUser(remotos, fallbackUsername);
                    estadoFavoritos.postValue(ESTADO_FAVORITOS_IDLE);
                    return;
                }

                executor.execute(() -> {
                    List<FavoriteItem> preparados = new ArrayList<>();

                    for (FavoriteItem item : remotos) {
                        String rutaLocal = prepararArchivoFavoritoLocal(item);

                        if (!TextUtils.isEmpty(rutaLocal)) {
                            item.setRutaArchivo(rutaLocal);
                        }

                        preparados.add(item);
                    }

                    favoritosLocalRepository.replaceAllForCurrentUser(preparados, fallbackUsername);
                    estadoFavoritos.postValue(ESTADO_FAVORITOS_IDLE);
                });
            }

            @Override
            public void onFailure(@NonNull Call<FavoriteListResponse> call, @NonNull Throwable t) {
                estadoFavoritos.postValue(ESTADO_FAVORITOS_IDLE);
            }
        });
    }

    private String prepararArchivoFavoritoLocal(@NonNull FavoriteItem item) {
        if (TextUtils.isEmpty(item.getSongKey())) {
            return item.getRutaArchivo();
        }

        File dir = new File(application.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "favoritos");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        File destino = new File(dir, item.getSongKey() + ".mp3");

        if (destino.exists() && destino.length() > 0) {
            return destino.getAbsolutePath();
        }

        if (TextUtils.isEmpty(item.getRutaArchivo())) {
            return "";
        }

        estadoFavoritos.postValue(ESTADO_FAVORITOS_DESCARGANDO);

        try {
            Request request = new Request.Builder()
                    .url(item.getRutaArchivo())
                    .build();

            okhttp3.Response response = okHttpClient.newCall(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                return item.getRutaArchivo();
            }

            InputStream inputStream = response.body().byteStream();
            FileOutputStream outputStream = new FileOutputStream(destino);

            byte[] buffer = new byte[8192];
            int leidos;

            while ((leidos = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, leidos);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();
            response.close();

            return destino.getAbsolutePath();

        } catch (Exception e) {
            return item.getRutaArchivo();
        }
    }

    private void borrarArchivoDescargado(@NonNull String songKey) {
        File dir = new File(application.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "favoritos");
        File archivo = new File(dir, songKey + ".mp3");

        if (archivo.exists()) {
            archivo.delete();
        }
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

        return "song_" + Integer.toHexString(base.hashCode());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}