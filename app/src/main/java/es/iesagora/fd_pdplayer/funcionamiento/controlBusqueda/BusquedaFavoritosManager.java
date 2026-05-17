package es.iesagora.fd_pdplayer.funcionamiento.controlBusqueda;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteListResponse;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoritesApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BusquedaFavoritosManager {

    public interface BusquedaCallback {
        void onFavoritosCargados(List<FavoriteItem> favoritos, String busquedaActual);
        void onError(String mensaje);
    }

    private static final int PAGE = 1;
    private static final int LIMIT = 100;

    private static final List<FavoriteItem> favoritosMemoria = new ArrayList<>();
    private static String busquedaMemoria = null;
    private static String tokenMemoria = "";
    private static boolean memoriaCargada = false;

    private final FavoritesApiService apiService;

    private Call<FavoriteListResponse> llamadaActual;

    public BusquedaFavoritosManager(FavoritesApiService apiService) {
        this.apiService = apiService;
    }

    public boolean hayMemoriaPara(String tokenActual, String busquedaActual) {
        return memoriaCargada
                && tokenActual != null
                && tokenActual.equals(tokenMemoria)
                && mismaBusqueda(busquedaActual, busquedaMemoria);
    }

    public List<FavoriteItem> obtenerFavoritosMemoria() {
        return new ArrayList<>(favoritosMemoria);
    }

    public void cargarFavoritosPublicos(String tokenActual,
                                        String textoBusqueda,
                                        BusquedaCallback callback) {
        if (llamadaActual != null) {
            llamadaActual.cancel();
        }

        llamadaActual = apiService.getPublicFavorites(
                "Bearer " + tokenActual,
                textoBusqueda,
                PAGE,
                LIMIT
        );

        llamadaActual.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<FavoriteListResponse> call,
                                   @NonNull Response<FavoriteListResponse> response) {

                if (!response.isSuccessful()) {
                    if (callback != null) {
                        callback.onError("No se pudieron cargar los favoritos públicos");
                    }
                    return;
                }

                if (response.body() == null || response.body().getSongs() == null) {
                    favoritosMemoria.clear();
                    busquedaMemoria = textoBusqueda;
                    tokenMemoria = tokenActual;
                    memoriaCargada = true;

                    if (callback != null) {
                        callback.onFavoritosCargados(new ArrayList<>(), textoBusqueda);
                    }
                    return;
                }

                List<FavoriteItem> items = response.body().getSongs();

                favoritosMemoria.clear();
                favoritosMemoria.addAll(items);
                busquedaMemoria = textoBusqueda;
                tokenMemoria = tokenActual;
                memoriaCargada = true;

                if (callback != null) {
                    callback.onFavoritosCargados(obtenerFavoritosMemoria(), textoBusqueda);
                }
            }

            @Override
            public void onFailure(@NonNull Call<FavoriteListResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;

                if (callback != null) {
                    callback.onError("Error de red: " + t.getMessage());
                }
            }
        });
    }

    public void limpiarMemoria() {
        favoritosMemoria.clear();
        busquedaMemoria = null;
        tokenMemoria = "";
        memoriaCargada = false;
    }

    public void cancelar() {
        if (llamadaActual != null) {
            llamadaActual.cancel();
            llamadaActual = null;
        }
    }

    private boolean mismaBusqueda(String actual, String guardada) {
        if (actual == null && guardada == null) return true;
        if (actual == null || guardada == null) return false;

        return actual.equals(guardada);
    }
}