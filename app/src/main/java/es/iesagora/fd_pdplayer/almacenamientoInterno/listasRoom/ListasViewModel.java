package es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class ListasViewModel extends AndroidViewModel {

    private final ListasRepository repo;

    public ListasViewModel(@NonNull Application application) {
        super(application);
        repo = new ListasRepository(application);
    }

    public LiveData<List<ListaEntity>> obtenerListas() {
        return repo.obtenerListas();
    }

    public LiveData<ListaCanciones> obtenerListaConCanciones(int listaId) {
        return repo.obtenerListaConCanciones(listaId);
    }

    public void crearLista(String nombre) {
        repo.crearLista(nombre);
    }

    public void borrarLista(int listaId) {
        repo.borrarLista(listaId);
    }

    public void anadirCancionALista(int listaId, Cancion c) {
        repo.anadirCancionALista(listaId, c);
    }

    public void quitarCancionDeLista(int listaId, String rutaArchivo) {
        repo.quitarCancionDeLista(listaId, rutaArchivo);
    }

    public void quitarCancionDeTodasLasListas(String rutaArchivo) {
        repo.quitarCancionDeTodasLasListas(rutaArchivo);
    }

    public void actualizarRutaCancion(String rutaAntigua, String rutaNueva) {
        repo.actualizarRutaCancion(rutaAntigua, rutaNueva);
    }

    public void actualizarCancion(String rutaAntigua,
                                  String rutaNueva,
                                  String nombre,
                                  String artista,
                                  String album) {
        repo.actualizarCancion(
                rutaAntigua,
                rutaNueva,
                nombre,
                artista,
                album
        );
    }
}