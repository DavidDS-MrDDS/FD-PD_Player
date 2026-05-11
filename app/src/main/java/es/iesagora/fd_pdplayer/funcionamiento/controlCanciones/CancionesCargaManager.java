package es.iesagora.fd_pdplayer.funcionamiento.controlCanciones;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import es.iesagora.fd_pdplayer.almacenamientoInterno.CancionesRepository;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionesCargaManager {

    public interface Callback {
        void onCancionesCargadas(List<Cancion> canciones);
    }

    private final CancionesRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ExecutorService executor;
    private int versionCarga = 0;

    public CancionesCargaManager(Context context) {
        repository = new CancionesRepository(context.getApplicationContext());
        prepararExecutor();
    }

    public void cargar(int modoOrden, Callback callback) {
        prepararExecutor();

        int versionActual = ++versionCarga;

        try {
            ejecutarCarga(versionActual, modoOrden, callback);
        } catch (RejectedExecutionException e) {
            executor = null;
            prepararExecutor();

            try {
                ejecutarCarga(versionActual, modoOrden, callback);
            } catch (RejectedExecutionException ignored) {
                postResultado(versionActual, new ArrayList<>(), callback);
            }
        }
    }

    private void ejecutarCarga(int versionActual, int modoOrden, Callback callback) {
        executor.execute(() -> {
            List<Cancion> resultado = repository.getCancionesPorFecha(
                    modoOrden != CancionesUtils.ORDEN_MAS_ANTIGUO
            );

            if (modoOrden == CancionesUtils.ORDEN_A_Z) {
                CancionesUtils.ordenarAlfabeticamente(resultado, true);
            } else if (modoOrden == CancionesUtils.ORDEN_Z_A) {
                CancionesUtils.ordenarAlfabeticamente(resultado, false);
            }

            postResultado(versionActual, resultado, callback);
        });
    }

    private void postResultado(int versionActual,
                               List<Cancion> canciones,
                               Callback callback) {
        mainHandler.post(() -> {
            if (versionActual != versionCarga) return;

            if (callback != null) {
                callback.onCancionesCargadas(
                        canciones != null ? canciones : new ArrayList<>()
                );
            }
        });
    }

    private void prepararExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newSingleThreadExecutor();
        }
    }

    public void cancelarPendientes() {
        versionCarga++;
    }

    public void liberar() {
        versionCarga++;

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}