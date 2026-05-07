package es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class ListasRepository {

    private final ListasDao dao;
    private final Executor executor;

    public ListasRepository(Application app) {
        dao = ListasDatabase.getInstance(app).listasDao();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<ListaEntity>> obtenerListas() {
        return dao.obtenerListas();
    }

    public LiveData<ListaCanciones> obtenerListaConCanciones(int listaId) {
        return dao.obtenerListaConCanciones(listaId);
    }

    public void crearLista(String nombre) {
        executor.execute(() -> dao.insertarLista(new ListaEntity(nombre)));
    }

    public void borrarLista(int listaId) {
        executor.execute(() -> dao.borrarListaPorId(listaId));
    }

    public void anadirCancionALista(int listaId, Cancion c) {
        executor.execute(() -> dao.insertarCancionEnLista(
                new CancionEnListaEntity(
                        listaId,
                        c.getNombre(),
                        c.getArtista(),
                        c.getAlbum(),
                        c.getRutaArchivo()
                )
        ));
    }

    public void quitarCancionDeLista(int listaId, String rutaArchivo) {
        executor.execute(() -> dao.quitarCancionDeLista(listaId, rutaArchivo));
    }

    public void quitarCancionDeTodasLasListas(String rutaArchivo) {
        executor.execute(() -> dao.quitarCancionDeTodasLasListas(rutaArchivo));
    }

    public void actualizarRutaCancion(String rutaAntigua, String rutaNueva) {
        executor.execute(() -> dao.actualizarRutaCancion(rutaAntigua, rutaNueva));
    }

    public void actualizarCancion(String rutaAntigua,
                                  String rutaNueva,
                                  String nombre,
                                  String artista,
                                  String album) {
        executor.execute(() -> dao.actualizarCancion(
                rutaAntigua,
                rutaNueva,
                nombre,
                artista,
                album
        ));
    }
}