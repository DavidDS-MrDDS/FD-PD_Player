package es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom;

import android.content.Context;
import android.media.MediaScannerConnection;

import androidx.lifecycle.LiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import es.iesagora.fd_pdplayer.models.Cancion;

public class CancionesOcultasRepository {

    public interface SimpleCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private final Context context;
    private final CancionesOcultasDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CancionesOcultasRepository(Context context) {
        this.context = context.getApplicationContext();
        dao = CancionesOcultasDatabase.getInstance(context).cancionesOcultasDao();
    }

    public LiveData<List<CancionOcultaEntity>> obtenerOcultasLive() {
        return dao.obtenerOcultasLive();
    }

    public void ocultarCancion(Cancion cancion) {
        executor.execute(() -> dao.ocultarCancion(new CancionOcultaEntity(
                safe(cancion.getRutaArchivo()),
                safe(cancion.getNombre()),
                safe(cancion.getArtista()),
                safe(cancion.getAlbum()),
                System.currentTimeMillis()
        )));
    }

    public void desocultarCancion(String rutaArchivo) {
        executor.execute(() -> dao.desocultarCancion(rutaArchivo));
    }

    public List<String> obtenerRutasOcultasSync() {
        Future<List<String>> future = executor.submit((Callable<List<String>>) dao::obtenerRutasOcultasSync);

        try {
            List<String> rutas = future.get();
            return rutas != null ? rutas : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void borrarCancionDelDispositivo(Cancion cancion, SimpleCallback callback) {
        executor.execute(() -> {
            String ruta = cancion.getRutaArchivo();

            if (ruta == null || ruta.trim().isEmpty()) {
                callback.onError("No se encontró la ruta del archivo.");
                return;
            }

            File archivo = new File(ruta);

            if (!archivo.exists()) {
                dao.desocultarCancion(ruta);
                callback.onSuccess("La canción ya no existe en el dispositivo.");
                return;
            }

            boolean borrada = archivo.delete();

            if (borrada) {
                dao.desocultarCancion(ruta);

                MediaScannerConnection.scanFile(
                        context,
                        new String[]{ruta},
                        null,
                        null
                );

                callback.onSuccess("Canción borrada del dispositivo.");
            } else {
                callback.onError("No se pudo borrar la canción del dispositivo.");
            }
        });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}