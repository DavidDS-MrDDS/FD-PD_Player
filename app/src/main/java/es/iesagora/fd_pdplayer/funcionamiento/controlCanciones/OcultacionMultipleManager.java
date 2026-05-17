package es.iesagora.fd_pdplayer.funcionamiento.controlCanciones;

import android.content.Context;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.funcionamiento.otros.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;

public class OcultacionMultipleManager {

    public interface Callback {
        void onModoOcultacionCambiado(boolean activo);

        void onSeleccionCambiada(Set<String> rutasSeleccionadas);

        void onCancionesOcultadas(Set<String> rutasOcultadas, int total);

        void mostrarMensaje(String mensaje);
    }

    private final Context context;
    private final CancionesOcultasRepository cancionesOcultasRepository;
    private final Callback callback;

    private final Map<String, Cancion> cancionesSeleccionadas = new HashMap<>();

    private boolean modoOcultacionActivo = false;

    public OcultacionMultipleManager(Context context,
                                     CancionesOcultasRepository cancionesOcultasRepository,
                                     Callback callback) {
        this.context = context;
        this.cancionesOcultasRepository = cancionesOcultasRepository;
        this.callback = callback;
    }

    public void alPulsarBotonOcultacion() {
        if (modoOcultacionActivo) {
            mostrarMenuFinalizarOcultacion();
        } else {
            mostrarConfirmacionActivarModo();
        }
    }

    private void mostrarConfirmacionActivarModo() {
        VentanasApp.mostrarConfirmacion(
                context,
                "OcultarCanciones",
                "Modo ocultación",
                "¿Quieres activar el modo de ocultación?\n\nLos botones de opciones cambiarán para seleccionar varias canciones.",
                "Activar",
                this::activarModoOcultacion
        );
    }

    private void activarModoOcultacion() {
        modoOcultacionActivo = true;
        cancionesSeleccionadas.clear();

        if (callback != null) {
            callback.onModoOcultacionCambiado(true);
            callback.onSeleccionCambiada(getRutasSeleccionadas());
        }
    }

    private void mostrarMenuFinalizarOcultacion() {
        String mensaje;

        if (cancionesSeleccionadas.isEmpty()) {
            mensaje = "No hay canciones seleccionadas.";
        } else {
            mensaje = "Canciones seleccionadas: " + cancionesSeleccionadas.size();
        }

        String[] opciones = {
                "Ocultar seleccionadas",
                "Cancelar ocultación"
        };

        VentanasApp.mostrarMenu(
                context,
                "OcultarCanciones",
                "Finalizar ocultación",
                mensaje,
                opciones,
                (posicion, texto) -> {
                    if (posicion == 0) {
                        ocultarSeleccionadas();
                    } else {
                        cancelarModoOcultacion();
                    }
                }
        );
    }

    public void alternarSeleccion(Cancion cancion) {
        if (!modoOcultacionActivo || cancion == null) return;

        String ruta = cancion.getRutaArchivo();

        if (TextUtils.isEmpty(ruta)) {
            if (callback != null) {
                callback.mostrarMensaje("No se encontró la ruta de la canción");
            }
            return;
        }

        if (cancionesSeleccionadas.containsKey(ruta)) {
            cancionesSeleccionadas.remove(ruta);
        } else {
            cancionesSeleccionadas.put(ruta, cancion);
        }

        if (callback != null) {
            callback.onSeleccionCambiada(getRutasSeleccionadas());
        }
    }

    private void ocultarSeleccionadas() {
        if (cancionesSeleccionadas.isEmpty()) {
            if (callback != null) {
                callback.mostrarMensaje("No hay canciones seleccionadas");
            }
            return;
        }

        Set<String> rutasOcultadas = getRutasSeleccionadas();
        int total = cancionesSeleccionadas.size();

        ReproductorApp.getInstance().liberar();

        for (Cancion cancion : cancionesSeleccionadas.values()) {
            cancionesOcultasRepository.ocultarCancion(cancion);
        }

        modoOcultacionActivo = false;
        cancionesSeleccionadas.clear();

        if (callback != null) {
            callback.onModoOcultacionCambiado(false);
            callback.onSeleccionCambiada(getRutasSeleccionadas());
            callback.onCancionesOcultadas(rutasOcultadas, total);
        }
    }

    private void cancelarModoOcultacion() {
        modoOcultacionActivo = false;
        cancionesSeleccionadas.clear();

        if (callback != null) {
            callback.onModoOcultacionCambiado(false);
            callback.onSeleccionCambiada(getRutasSeleccionadas());
        }
    }

    public boolean estaActivo() {
        return modoOcultacionActivo;
    }

    public Set<String> getRutasSeleccionadas() {
        return new HashSet<>(cancionesSeleccionadas.keySet());
    }

    public int getCantidadSeleccionadas() {
        return cancionesSeleccionadas.size();
    }

    public void liberar() {
        cancionesSeleccionadas.clear();
        modoOcultacionActivo = false;
    }
}