package es.iesagora.fd_pdplayer.funcionamiento.controlCanciones;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;

public class CancionesAccionesManager {

    public interface Callback {
        void refrescarLista();
        void mostrarMensaje(String mensaje);
    }

    private final Fragment fragment;
    private final ListasViewModel listasViewModel;
    private final FavoriteUploadRepository favoritosRepository;
    private final CancionesOcultasRepository ocultasRepository;
    private final Callback callback;

    private List<ListaEntity> listasActuales = new ArrayList<>();
    private List<Cancion> cancionesTodas = new ArrayList<>();
    private List<Cancion> cancionesFiltradas = new ArrayList<>();

    public CancionesAccionesManager(Fragment fragment,
                                    ListasViewModel listasViewModel,
                                    FavoriteUploadRepository favoritosRepository,
                                    CancionesOcultasRepository ocultasRepository,
                                    Callback callback) {
        this.fragment = fragment;
        this.listasViewModel = listasViewModel;
        this.favoritosRepository = favoritosRepository;
        this.ocultasRepository = ocultasRepository;
        this.callback = callback;
    }

    public void setListasActuales(List<ListaEntity> listasActuales) {
        this.listasActuales = listasActuales != null ? listasActuales : new ArrayList<>();
    }

    public void setCanciones(List<Cancion> cancionesTodas, List<Cancion> cancionesFiltradas) {
        this.cancionesTodas = cancionesTodas != null ? cancionesTodas : new ArrayList<>();
        this.cancionesFiltradas = cancionesFiltradas != null ? cancionesFiltradas : new ArrayList<>();
    }

    public void mostrarMenuCancion(Cancion cancion) {
        if (!fragment.isAdded() || cancion == null) return;

        VentanasApp.mostrarMenu(
                fragment.requireContext(),
                "MenuCancion",
                "Opciones de canción",
                CancionesUtils.safe(cancion.getNombre()),
                CancionesUtils.getOpcionesCancion(),
                (posicion, texto) -> ejecutarOpcion(posicion, cancion)
        );
    }

    private void ejecutarOpcion(int posicion, Cancion cancion) {
        if (posicion == 0) {
            abrirModificarCancion(cancion);
        } else if (posicion == 1) {
            subirAFavoritos(cancion);
        } else if (posicion == 2) {
            mostrarDialogSeleccionLista(cancion);
        } else if (posicion == 3) {
            confirmarOcultarCancion(cancion);
        }
    }

    public void abrirCancion(Cancion cancion) {
        if (!fragment.isAdded() || cancion == null) return;

        if (cancionesFiltradas == null || cancionesFiltradas.isEmpty()) {
            callback.mostrarMensaje("No hay canciones disponibles");
            return;
        }

        int posicion = CancionesUtils.buscarPosicionPorRuta(
                cancionesFiltradas,
                cancion.getRutaArchivo()
        );

        if (posicion < 0) {
            posicion = 0;
        }

        ReproductorApp.getInstance().liberar();

        Bundle bundle = CancionesUtils.crearBundleReproduccion(
                cancionesFiltradas.get(posicion),
                cancionesFiltradas,
                posicion
        );

        NavHostFragment.findNavController(fragment)
                .navigate(R.id.cancionFragment, bundle);
    }

    private void abrirModificarCancion(Cancion cancion) {
        if (!fragment.isAdded() || cancion == null) return;

        if (TextUtils.isEmpty(cancion.getRutaArchivo())) {
            callback.mostrarMensaje("No se encontró la ruta de la canción");
            return;
        }

        if (!CancionesUtils.estaEnCarpetaOrganizada(cancion.getRutaArchivo())) {
            callback.mostrarMensaje("Solo se pueden modificar canciones organizadas.");
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);

        NavHostFragment.findNavController(fragment)
                .navigate(R.id.modificarCancionFragment, bundle);
    }

    private void subirAFavoritos(Cancion cancion) {
        if (cancion == null || favoritosRepository == null) return;

        favoritosRepository.subirCancionAFavoritos(cancion, new FavoriteUploadRepository.SimpleCallback() {
            @Override
            public void onSuccess(String message) {
                callback.mostrarMensaje(message);
            }

            @Override
            public void onError(String message) {
                callback.mostrarMensaje(message);
            }
        });
    }

    private void mostrarDialogSeleccionLista(Cancion cancion) {
        if (!fragment.isAdded() || cancion == null) return;

        if (listasActuales == null || listasActuales.isEmpty()) {
            callback.mostrarMensaje("Primero crea una lista");
            return;
        }

        VentanasApp.mostrarMenu(
                fragment.requireContext(),
                "MenuCancion_Listas",
                "Añadir a lista",
                CancionesUtils.safe(cancion.getNombre()),
                CancionesUtils.obtenerNombresListas(listasActuales),
                (posicion, texto) -> anadirCancionALista(posicion, cancion)
        );
    }

    private void anadirCancionALista(int posicion, Cancion cancion) {
        if (listasActuales == null || posicion < 0 || posicion >= listasActuales.size()) {
            return;
        }

        ListaEntity lista = listasActuales.get(posicion);

        listasViewModel.anadirCancionALista(lista.getId(), cancion);

        callback.mostrarMensaje("Añadida a " + CancionesUtils.safe(lista.getNombre()));
    }

    private void confirmarOcultarCancion(Cancion cancion) {
        if (!fragment.isAdded() || cancion == null) return;

        VentanasApp.mostrarConfirmacion(
                fragment.requireContext(),
                "OcultarCancion",
                "Ocultar canción",
                "¿Quieres ocultar \"" + CancionesUtils.safe(cancion.getNombre()) + "\"?\n\nNo aparecerá en canciones ni en listas, pero seguirá en favoritos.",
                "Ocultar",
                () -> ocultarCancion(cancion)
        );
    }

    private void ocultarCancion(Cancion cancion) {
        if (cancion == null) return;

        ocultasRepository.ocultarCancion(cancion);
        listasViewModel.quitarCancionDeTodasLasListas(cancion.getRutaArchivo());

        CancionesUtils.eliminarPorRuta(cancionesTodas, cancion.getRutaArchivo());

        callback.refrescarLista();
        callback.mostrarMensaje("Canción ocultada");
    }
}