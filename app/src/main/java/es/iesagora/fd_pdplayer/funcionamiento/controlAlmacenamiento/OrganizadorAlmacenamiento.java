package es.iesagora.fd_pdplayer.funcionamiento.controlAlmacenamiento;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.almacenamientoInterno.CancionesRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasRepository;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class OrganizadorAlmacenamiento {

    private static final String PREFS = "organizar_almacenamiento_prefs";
    private static final String KEY_ORGANIZADO = "organizacion_realizada";
    public static final String NOMBRE_CARPETA = "FD-PD_Player_Canciones";

    private final Context context;
    private final CancionesRepository cancionesRepository;
    private final ListasRepository listasRepository;
    private final FavoritosLocalRepository favoritosRepository;

    private final ArrayList<MovimientoPendiente> pendientes = new ArrayList<>();
    private ResultadoOrganizacion resultado = new ResultadoOrganizacion();

    public OrganizadorAlmacenamiento(Context context, Application application) {
        this.context = context.getApplicationContext();
        cancionesRepository = new CancionesRepository(this.context);
        listasRepository = new ListasRepository(application);
        favoritosRepository = new FavoritosLocalRepository(application);
    }

    public boolean organizacionYaRealizada() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ORGANIZADO, false);
    }

    public boolean hayPendientes() {
        return !pendientes.isEmpty();
    }

    public ArrayList<Uri> obtenerUrisPendientes() {
        ArrayList<Uri> uris = new ArrayList<>();

        for (MovimientoPendiente movimiento : pendientes) {
            if (movimiento.uriOriginal != null) {
                uris.add(movimiento.uriOriginal);
            }
        }

        return uris;
    }

    public ResultadoOrganizacion organizar() {
        pendientes.clear();
        resultado = new ResultadoOrganizacion();

        List<Cancion> canciones = cancionesRepository.getCancionesPorFecha(true);

        if (canciones == null) {
            resultado.fallidas++;
            return resultado.copia();
        }

        for (Cancion cancion : canciones) {
            organizarCancion(cancion);
        }

        guardarSiTodoFueBien();

        return resultado.copia();
    }

    public ResultadoOrganizacion confirmarPendientes() {
        for (MovimientoPendiente movimiento : pendientes) {
            actualizarRutas(movimiento.rutaAntigua, movimiento.rutaNueva);
            escanear(movimiento.rutaAntigua, movimiento.rutaNueva);
            resultado.movidas++;
        }

        pendientes.clear();
        guardarSiTodoFueBien();

        return resultado.copia();
    }

    public ResultadoOrganizacion cancelarPendientes() {
        for (MovimientoPendiente movimiento : pendientes) {
            borrarCopiaCreada(movimiento);
            resultado.fallidas++;
        }

        pendientes.clear();

        return resultado.copia();
    }

    private void organizarCancion(Cancion cancion) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            resultado.fallidas++;
            return;
        }

        if (estaEnCarpetaOrganizada(cancion.getRutaArchivo())) {
            resultado.yaOrganizadas++;
            return;
        }

        MovimientoPendiente movimiento;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            movimiento = copiarConMediaStore(cancion);
        } else {
            movimiento = moverConFile(cancion);
        }

        if (movimiento == null) {
            resultado.fallidas++;
            return;
        }

        if (movimiento.necesitaPermisoBorrado) {
            pendientes.add(movimiento);
        } else {
            actualizarRutas(movimiento.rutaAntigua, movimiento.rutaNueva);
            resultado.movidas++;
        }
    }

    private MovimientoPendiente copiarConMediaStore(Cancion cancion) {
        Uri uriDestino = null;

        try {
            File origen = new File(cancion.getRutaArchivo());

            if (!origen.exists() || !origen.isFile()) {
                return null;
            }

            ContentResolver resolver = context.getContentResolver();

            String carpetaDestino = crearRutaDestinoMediaStore(cancion);
            String nombreArchivo = crearNombreArchivo(cancion, origen);
            String nombreFinal = crearNombreUnicoMediaStore(resolver, nombreArchivo, carpetaDestino);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, nombreFinal);
            values.put(MediaStore.Audio.Media.MIME_TYPE, obtenerMimeType(nombreFinal));
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, carpetaDestino);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);

            uriDestino = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

            if (uriDestino == null) {
                return null;
            }

            try (InputStream in = new FileInputStream(origen);
                 OutputStream out = resolver.openOutputStream(uriDestino)) {

                if (out == null) {
                    resolver.delete(uriDestino, null, null);
                    return null;
                }

                copiar(in, out);
            }

            ContentValues finalValues = new ContentValues();
            finalValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(uriDestino, finalValues, null, null);

            String rutaNueva = obtenerRutaDesdeUri(resolver, uriDestino);

            if (TextUtils.isEmpty(rutaNueva)) {
                File destinoEstimado = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        crearRutaDestinoFile(cancion) + "/" + nombreFinal
                );

                rutaNueva = destinoEstimado.getAbsolutePath();
            }

            Uri uriOriginal = obtenerUriPorRuta(origen.getAbsolutePath());

            if (uriOriginal == null) {
                if (!origen.delete()) {
                    resolver.delete(uriDestino, null, null);
                    return null;
                }

                escanear(origen.getAbsolutePath(), rutaNueva);

                return new MovimientoPendiente(
                        origen.getAbsolutePath(),
                        rutaNueva,
                        null,
                        uriDestino,
                        false
                );
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return new MovimientoPendiente(
                        origen.getAbsolutePath(),
                        rutaNueva,
                        uriOriginal,
                        uriDestino,
                        true
                );
            }

            if (!borrarOriginalDirecto(origen, uriOriginal)) {
                resolver.delete(uriDestino, null, null);
                return null;
            }

            escanear(origen.getAbsolutePath(), rutaNueva);

            return new MovimientoPendiente(
                    origen.getAbsolutePath(),
                    rutaNueva,
                    uriOriginal,
                    uriDestino,
                    false
            );

        } catch (Exception e) {
            if (uriDestino != null) {
                try {
                    context.getContentResolver().delete(uriDestino, null, null);
                } catch (Exception ignored) {
                }
            }

            return null;
        }
    }

    private MovimientoPendiente moverConFile(Cancion cancion) {
        try {
            File origen = new File(cancion.getRutaArchivo());

            if (!origen.exists() || !origen.isFile()) {
                return null;
            }

            File carpeta = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    crearRutaDestinoFile(cancion)
            );

            if (!carpeta.exists() && !carpeta.mkdirs()) {
                return null;
            }

            File destino = crearArchivoUnico(
                    carpeta,
                    crearNombreArchivo(cancion, origen)
            );

            boolean movida = origen.renameTo(destino);

            if (!movida) {
                try (InputStream in = new FileInputStream(origen);
                     OutputStream out = new FileOutputStream(destino)) {
                    copiar(in, out);
                }

                if (!origen.delete()) {
                    destino.delete();
                    return null;
                }
            }

            escanear(origen.getAbsolutePath(), destino.getAbsolutePath());

            return new MovimientoPendiente(
                    origen.getAbsolutePath(),
                    destino.getAbsolutePath(),
                    null,
                    null,
                    false
            );

        } catch (Exception e) {
            return null;
        }
    }

    private void actualizarRutas(String rutaAntigua, String rutaNueva) {
        listasRepository.actualizarRutaCancion(rutaAntigua, rutaNueva);
        favoritosRepository.actualizarRutaCancion(rutaAntigua, rutaNueva);
    }

    private void borrarCopiaCreada(MovimientoPendiente movimiento) {
        try {
            if (movimiento.uriDestino != null) {
                context.getContentResolver().delete(movimiento.uriDestino, null, null);
            }
        } catch (Exception ignored) {
        }

        try {
            File archivo = new File(movimiento.rutaNueva);

            if (archivo.exists()) {
                archivo.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean borrarOriginalDirecto(File origen, Uri uriOriginal) {
        try {
            if (origen.delete()) {
                return true;
            }

            int borradas = context.getContentResolver().delete(uriOriginal, null, null);

            return borradas > 0 || !origen.exists();

        } catch (Exception e) {
            return false;
        }
    }

    private Uri obtenerUriPorRuta(String ruta) {
        Cursor cursor = null;

        try {
            String[] projection = {MediaStore.Audio.Media._ID};
            String selection = MediaStore.Audio.Media.DATA + " = ?";
            String[] args = {ruta};

            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

                return Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id)
                );
            }

        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private String obtenerRutaDesdeUri(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;

        try {
            String[] projection = {MediaStore.Audio.Media.DATA};

            cursor = resolver.query(uri, projection, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
            }

        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private String crearRutaDestinoMediaStore(Cancion cancion) {
        String album = obtenerAlbumValido(cancion);

        if (TextUtils.isEmpty(album)) {
            return Environment.DIRECTORY_MUSIC + "/" + NOMBRE_CARPETA + "/";
        }

        return Environment.DIRECTORY_MUSIC + "/" + NOMBRE_CARPETA + "/" + album + "/";
    }

    private String crearRutaDestinoFile(Cancion cancion) {
        String album = obtenerAlbumValido(cancion);

        if (TextUtils.isEmpty(album)) {
            return NOMBRE_CARPETA;
        }

        return NOMBRE_CARPETA + "/" + album;
    }

    private String obtenerAlbumValido(Cancion cancion) {
        String album = limpiar(safe(cancion.getAlbum()));

        if (TextUtils.isEmpty(album)) return "";
        if (album.equalsIgnoreCase("<unknown>")) return "";
        if (album.equalsIgnoreCase("unknown")) return "";
        if (album.equalsIgnoreCase("download")) return "";
        if (album.equalsIgnoreCase("downloads")) return "";

        return album;
    }

    private boolean estaEnCarpetaOrganizada(String ruta) {
        if (TextUtils.isEmpty(ruta)) {
            return false;
        }

        return ruta.replace("\\", "/")
                .toLowerCase()
                .contains("/" + NOMBRE_CARPETA.toLowerCase() + "/");
    }

    private String crearNombreArchivo(Cancion cancion, File origen) {
        String nombre = limpiar(safe(cancion.getNombre()));
        String artista = limpiar(safe(cancion.getArtista()));
        String extension = obtenerExtension(origen.getName());

        String resultado;

        if (!TextUtils.isEmpty(artista) && !artista.equalsIgnoreCase("<unknown>")
                && !TextUtils.isEmpty(nombre)) {
            resultado = artista + " - " + nombre;
        } else if (!TextUtils.isEmpty(nombre)) {
            resultado = nombre;
        } else {
            resultado = quitarExtension(origen.getName());
        }

        resultado = limpiar(resultado);

        if (TextUtils.isEmpty(resultado)) {
            resultado = "cancion_" + System.currentTimeMillis();
        }

        if (!resultado.toLowerCase().endsWith(extension.toLowerCase())) {
            resultado += extension;
        }

        return resultado;
    }

    private String crearNombreUnicoMediaStore(ContentResolver resolver, String nombreArchivo, String rutaDestino) {
        String base = quitarExtension(nombreArchivo);
        String extension = obtenerExtension(nombreArchivo);
        String candidato = nombreArchivo;
        int contador = 1;

        while (existeEnMediaStore(resolver, candidato, rutaDestino)) {
            candidato = base + " (" + contador + ")" + extension;
            contador++;
        }

        return candidato;
    }

    private boolean existeEnMediaStore(ContentResolver resolver, String nombreArchivo, String rutaDestino) {
        Cursor cursor = null;

        try {
            String[] projection = {MediaStore.Audio.Media._ID};
            String selection = MediaStore.Audio.Media.DISPLAY_NAME + " = ? AND "
                    + MediaStore.Audio.Media.RELATIVE_PATH + " = ?";
            String[] args = {nombreArchivo, rutaDestino};

            cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    null
            );

            return cursor != null && cursor.moveToFirst();

        } catch (Exception e) {
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private File crearArchivoUnico(File carpeta, String nombreArchivo) {
        String base = quitarExtension(nombreArchivo);
        String extension = obtenerExtension(nombreArchivo);

        File destino = new File(carpeta, nombreArchivo);
        int contador = 1;

        while (destino.exists()) {
            destino = new File(carpeta, base + " (" + contador + ")" + extension);
            contador++;
        }

        return destino;
    }

    private void copiar(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int leido;

        while ((leido = in.read(buffer)) != -1) {
            out.write(buffer, 0, leido);
        }

        out.flush();
    }

    private void escanear(String rutaAntigua, String rutaNueva) {
        MediaScannerConnection.scanFile(
                context,
                new String[]{rutaAntigua, rutaNueva},
                null,
                null
        );
    }

    private void guardarSiTodoFueBien() {
        if (resultado.fallidas == 0 && pendientes.isEmpty()) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_ORGANIZADO, true).apply();
        }
    }

    private String limpiar(String texto) {
        if (texto == null) {
            return "";
        }

        return texto
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
    }

    private String quitarExtension(String nombreArchivo) {
        if (TextUtils.isEmpty(nombreArchivo)) {
            return "cancion";
        }

        int punto = nombreArchivo.lastIndexOf(".");

        if (punto == -1) {
            return nombreArchivo;
        }

        return nombreArchivo.substring(0, punto);
    }

    private String obtenerExtension(String nombreArchivo) {
        if (TextUtils.isEmpty(nombreArchivo)) {
            return ".mp3";
        }

        int punto = nombreArchivo.lastIndexOf(".");

        if (punto == -1 || punto == nombreArchivo.length() - 1) {
            return ".mp3";
        }

        return nombreArchivo.substring(punto);
    }

    private String obtenerMimeType(String nombreArchivo) {
        String lower = nombreArchivo.toLowerCase();

        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";

        return "audio/mpeg";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class MovimientoPendiente {

        String rutaAntigua;
        String rutaNueva;
        Uri uriOriginal;
        Uri uriDestino;
        boolean necesitaPermisoBorrado;

        MovimientoPendiente(String rutaAntigua,
                            String rutaNueva,
                            Uri uriOriginal,
                            Uri uriDestino,
                            boolean necesitaPermisoBorrado) {
            this.rutaAntigua = rutaAntigua;
            this.rutaNueva = rutaNueva;
            this.uriOriginal = uriOriginal;
            this.uriDestino = uriDestino;
            this.necesitaPermisoBorrado = necesitaPermisoBorrado;
        }
    }
}