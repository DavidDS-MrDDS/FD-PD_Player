package es.iesagora.fd_pdplayer.funcionamiento.controlCanciones;

import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionesUtils {

    public static final int ORDEN_MAS_NUEVO = 0;
    public static final int ORDEN_MAS_ANTIGUO = 1;
    public static final int ORDEN_A_Z = 2;
    public static final int ORDEN_Z_A = 3;

    private static final String NOMBRE_CARPETA_ORGANIZADA = "FD-PD_Player_Canciones";

    private CancionesUtils() {
    }

    public static String[] getOpcionesOrden() {
        return new String[]{
                "Ordenar de más nuevo a más viejo",
                "Ordenar de más viejo a más nuevo",
                "Ordenar alfabéticamente A -> Z",
                "Ordenar alfabéticamente Z -> A"
        };
    }

    public static String[] getOpcionesCancion() {
        return new String[]{
                "Modificar canción",
                "Añadir a favoritos",
                "Añadir a lista",
                "Ocultar canción"
        };
    }

    public static String descripcionOrden(int modoOrden) {
        if (modoOrden == ORDEN_MAS_NUEVO) {
            return "Orden actual: de más nuevo a más viejo";
        }

        if (modoOrden == ORDEN_MAS_ANTIGUO) {
            return "Orden actual: de más viejo a más nuevo";
        }

        if (modoOrden == ORDEN_A_Z) {
            return "Orden actual: alfabéticamente A a Z";
        }

        return "Orden actual: alfabéticamente Z a A";
    }

    public static List<Cancion> filtrar(List<Cancion> canciones, String texto) {
        List<Cancion> resultado = new ArrayList<>();

        if (canciones == null) return resultado;

        if (TextUtils.isEmpty(texto)) {
            resultado.addAll(canciones);
            return resultado;
        }

        String filtro = texto.toLowerCase();

        for (Cancion c : canciones) {
            if (contiene(c.getNombre(), filtro)
                    || contiene(c.getArtista(), filtro)
                    || contiene(c.getAlbum(), filtro)) {
                resultado.add(c);
            }
        }

        return resultado;
    }

    public static void ordenarAlfabeticamente(List<Cancion> canciones, boolean ascendente) {
        if (canciones == null) return;

        Collections.sort(canciones, (c1, c2) -> {
            int resultado = safe(c1.getNombre()).compareToIgnoreCase(safe(c2.getNombre()));

            if (resultado == 0) {
                resultado = safe(c1.getArtista()).compareToIgnoreCase(safe(c2.getArtista()));
            }

            if (resultado == 0) {
                resultado = safe(c1.getAlbum()).compareToIgnoreCase(safe(c2.getAlbum()));
            }

            return ascendente ? resultado : -resultado;
        });
    }

    public static int buscarPosicionPorRuta(List<Cancion> canciones, String ruta) {
        if (canciones == null || TextUtils.isEmpty(ruta)) return -1;

        for (int i = 0; i < canciones.size(); i++) {
            Cancion c = canciones.get(i);

            if (c != null && ruta.equals(c.getRutaArchivo())) {
                return i;
            }
        }

        return -1;
    }

    public static void eliminarPorRuta(List<Cancion> canciones, String ruta) {
        if (canciones == null || TextUtils.isEmpty(ruta)) return;

        for (int i = canciones.size() - 1; i >= 0; i--) {
            Cancion c = canciones.get(i);

            if (c != null && ruta.equals(c.getRutaArchivo())) {
                canciones.remove(i);
            }
        }
    }

    public static boolean estaEnCarpetaOrganizada(String ruta) {
        if (TextUtils.isEmpty(ruta)) return false;

        return ruta.replace("\\", "/")
                .toLowerCase()
                .contains("/" + NOMBRE_CARPETA_ORGANIZADA.toLowerCase() + "/");
    }

    public static Bundle crearBundleReproduccion(Cancion cancion,
                                                 List<Cancion> listaCanciones,
                                                 int posicion) {
        ArrayList<Cancion> listaSerializable = listaCanciones != null
                ? new ArrayList<>(listaCanciones)
                : new ArrayList<>();

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);
        bundle.putSerializable("listaCanciones", listaSerializable);
        bundle.putInt("posicion", posicion);

        return bundle;
    }

    public static String[] obtenerNombresListas(List<ListaEntity> listas) {
        if (listas == null) return new String[0];

        String[] nombres = new String[listas.size()];

        for (int i = 0; i < listas.size(); i++) {
            nombres[i] = safe(listas.get(i).getNombre());
        }

        return nombres;
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean contiene(String value, String filtro) {
        return safe(value).toLowerCase().contains(filtro);
    }
}