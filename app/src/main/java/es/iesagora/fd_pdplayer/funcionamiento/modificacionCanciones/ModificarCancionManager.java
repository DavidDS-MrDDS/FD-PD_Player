package es.iesagora.fd_pdplayer.funcionamiento.modificacionCanciones;

import android.app.Application;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasRepository;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class ModificarCancionManager {

    private final Application application;
    private final Context context;

    public ModificarCancionManager(@NonNull Application application) {
        this.application = application;
        this.context = application.getApplicationContext();
    }

    public ModificacionPendiente prepararNuevaCancion(@NonNull Cancion cancion,
                                                      @NonNull String nuevoNombre,
                                                      @NonNull String nuevoArtista,
                                                      @NonNull String nuevoAlbum,
                                                      File nuevaImagen) throws Exception {
        if (TextUtils.isEmpty(cancion.getRutaArchivo())) {
            throw new Exception("No se encontró la ruta de la canción.");
        }

        File original = new File(cancion.getRutaArchivo());

        if (!original.exists() || !original.isFile()) {
            throw new Exception("La canción original no existe en el dispositivo.");
        }

        File temporal = null;

        try {
            temporal = crearArchivoTemporalEditado(
                    original,
                    nuevoNombre,
                    nuevoArtista,
                    nuevoAlbum,
                    nuevaImagen
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return guardarNuevoArchivoConMediaStore(
                        original,
                        temporal,
                        nuevoNombre,
                        nuevoArtista,
                        nuevoAlbum
                );
            }

            return guardarNuevoArchivoConFile(
                    original,
                    temporal,
                    nuevoNombre,
                    nuevoArtista,
                    nuevoAlbum
            );

        } finally {
            if (temporal != null && temporal.exists()) {
                temporal.delete();
            }
        }
    }

    private File crearArchivoTemporalEditado(File original,
                                             String nombre,
                                             String artista,
                                             String album,
                                             File imagen) throws Exception {
        File temporal = new File(
                context.getCacheDir(),
                "edit_" + System.currentTimeMillis() + obtenerExtension(original.getName())
        );

        try (InputStream input = new FileInputStream(original);
             OutputStream output = new FileOutputStream(temporal)) {
            copiarStreams(input, output);
        }

        escribirMetadatos(temporal, nombre, artista, album, imagen);

        return temporal;
    }

    private void escribirMetadatos(File archivo,
                                   String nombre,
                                   String artista,
                                   String album,
                                   File imagen) throws Exception {
        AudioFile audioFile = AudioFileIO.read(archivo);
        Tag tag = audioFile.getTagOrCreateAndSetDefault();

        tag.setField(FieldKey.TITLE, nombre);
        tag.setField(FieldKey.ARTIST, artista);
        tag.setField(FieldKey.ALBUM, album);

        if (imagen != null && imagen.exists()) {
            try {
                tag.deleteArtworkField();
            } catch (Exception ignored) {
            }

            Artwork artwork = ArtworkFactory.createArtworkFromFile(imagen);
            tag.setField(artwork);
        }

        audioFile.commit();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private ModificacionPendiente guardarNuevoArchivoConMediaStore(File original,
                                                                   File temporal,
                                                                   String nombre,
                                                                   String artista,
                                                                   String album) throws Exception {
        Uri uriDestino = null;

        try {
            ContentResolver resolver = context.getContentResolver();

            String relativePath = obtenerRelativePathDesdeOriginal(original);
            String nombreArchivo = crearNombreArchivo(nombre, artista, original);
            String nombreUnico = crearNombreUnicoMediaStore(resolver, nombreArchivo, relativePath);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, nombreUnico);
            values.put(MediaStore.Audio.Media.MIME_TYPE, obtenerMimeType(nombreUnico));
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);

            uriDestino = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

            if (uriDestino == null) {
                throw new Exception("No se pudo crear la nueva canción.");
            }

            try (InputStream input = new FileInputStream(temporal);
                 OutputStream output = resolver.openOutputStream(uriDestino)) {

                if (output == null) {
                    resolver.delete(uriDestino, null, null);
                    throw new Exception("No se pudo escribir la nueva canción.");
                }

                copiarStreams(input, output);
            }

            ContentValues finalValues = new ContentValues();
            finalValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(uriDestino, finalValues, null, null);

            String rutaNueva = obtenerRutaDesdeUri(resolver, uriDestino);

            if (TextUtils.isEmpty(rutaNueva)) {
                rutaNueva = Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/"
                        + relativePath
                        + nombreUnico;
            }

            Uri uriOriginal = obtenerUriMediaStorePorRuta(original.getAbsolutePath());

            return new ModificacionPendiente(
                    original.getAbsolutePath(),
                    rutaNueva,
                    uriOriginal,
                    uriDestino,
                    nombre,
                    artista,
                    album,
                    false
            );

        } catch (Exception e) {
            if (uriDestino != null) {
                try {
                    context.getContentResolver().delete(uriDestino, null, null);
                } catch (Exception ignored) {
                }
            }

            throw e;
        }
    }

    private ModificacionPendiente guardarNuevoArchivoConFile(File original,
                                                             File temporal,
                                                             String nombre,
                                                             String artista,
                                                             String album) throws Exception {
        File carpeta = original.getParentFile();

        if (carpeta == null || !carpeta.exists()) {
            carpeta = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        }

        String nombreArchivo = crearNombreArchivo(nombre, artista, original);
        File destino = crearArchivoUnico(carpeta, nombreArchivo);

        try (InputStream input = new FileInputStream(temporal);
             OutputStream output = new FileOutputStream(destino)) {
            copiarStreams(input, output);
        }

        return new ModificacionPendiente(
                original.getAbsolutePath(),
                destino.getAbsolutePath(),
                null,
                null,
                nombre,
                artista,
                album,
                false
        );
    }

    public boolean borrarOriginalDirecto(ModificacionPendiente pendiente) {
        if (pendiente == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pendiente.uriOriginal != null) {
            int borradas = context.getContentResolver().delete(pendiente.uriOriginal, null, null);
            return borradas > 0;
        }

        File original = new File(pendiente.rutaAntigua);
        return !original.exists() || original.delete();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public PendingIntent crearDeleteRequest(ModificacionPendiente pendiente) {
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(pendiente.uriOriginal);

        return MediaStore.createDeleteRequest(
                context.getContentResolver(),
                uris
        );
    }

    public void borrarDestinoPendiente(ModificacionPendiente pendiente) {
        if (pendiente == null) return;

        try {
            if (pendiente.uriDestino != null) {
                context.getContentResolver().delete(pendiente.uriDestino, null, null);
            }
        } catch (Exception ignored) {
        }

        try {
            if (!TextUtils.isEmpty(pendiente.rutaNueva)) {
                File archivo = new File(pendiente.rutaNueva);

                if (archivo.exists()) {
                    archivo.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void finalizarModificacion(ModificacionPendiente pendiente) {
        actualizarReferencias(pendiente);
        escanearArchivos(pendiente.rutaAntigua, pendiente.rutaNueva);
    }

    private void actualizarReferencias(ModificacionPendiente pendiente) {
        new ListasRepository(application).actualizarCancion(
                pendiente.rutaAntigua,
                pendiente.rutaNueva,
                pendiente.nombre,
                pendiente.artista,
                pendiente.album
        );

        new FavoritosLocalRepository(application).actualizarCancion(
                pendiente.rutaAntigua,
                pendiente.rutaNueva,
                pendiente.nombre,
                pendiente.artista,
                pendiente.album
        );

        new CancionesOcultasRepository(context).actualizarCancion(
                pendiente.rutaAntigua,
                pendiente.rutaNueva,
                pendiente.nombre,
                pendiente.artista,
                pendiente.album
        );
    }

    public File crearImagenTemporalDesdeUri(Uri uri) throws Exception {
        File archivoImagen = new File(
                context.getCacheDir(),
                "imagen_cancion_" + System.currentTimeMillis() + ".jpg"
        );

        Bitmap bitmap;

        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input);
        }

        if (bitmap == null) {
            return null;
        }

        try (OutputStream output = new FileOutputStream(archivoImagen)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
        }

        bitmap.recycle();

        return archivoImagen;
    }

    public Bitmap obtenerImagenDesdeArchivo(String ruta) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();

        try {
            mmr.setDataSource(ruta);
            byte[] art = mmr.getEmbeddedPicture();

            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }

        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private Uri obtenerUriMediaStorePorRuta(String ruta) {
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

            cursor = resolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    null
            );

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

    private String obtenerRelativePathDesdeOriginal(File original) {
        try {
            File parent = original.getParentFile();

            if (parent == null) {
                return Environment.DIRECTORY_MUSIC + "/";
            }

            String raiz = Environment.getExternalStorageDirectory().getAbsolutePath();
            String parentPath = parent.getAbsolutePath();

            if (parentPath.startsWith(raiz)) {
                String relativo = parentPath.substring(raiz.length());

                while (relativo.startsWith("/")) {
                    relativo = relativo.substring(1);
                }

                if (!TextUtils.isEmpty(relativo)) {
                    return relativo.endsWith("/") ? relativo : relativo + "/";
                }
            }

        } catch (Exception ignored) {
        }

        return Environment.DIRECTORY_MUSIC + "/";
    }

    private String crearNombreArchivo(String nombre, String artista, File original) {
        String extension = obtenerExtension(original.getName());

        String nombreLimpio = limpiarNombreArchivo(nombre);
        String artistaLimpio = limpiarNombreArchivo(artista);

        String resultado;

        if (!TextUtils.isEmpty(artistaLimpio)
                && !artistaLimpio.equalsIgnoreCase("<unknown>")
                && !TextUtils.isEmpty(nombreLimpio)) {
            resultado = artistaLimpio + " - " + nombreLimpio;
        } else if (!TextUtils.isEmpty(nombreLimpio)) {
            resultado = nombreLimpio;
        } else {
            resultado = quitarExtension(original.getName());
        }

        resultado = limpiarNombreArchivo(resultado);

        if (TextUtils.isEmpty(resultado)) {
            resultado = "cancion_" + System.currentTimeMillis();
        }

        if (!resultado.toLowerCase().endsWith(extension.toLowerCase())) {
            resultado += extension;
        }

        return resultado;
    }

    private String crearNombreUnicoMediaStore(ContentResolver resolver,
                                              String nombreArchivo,
                                              String relativePath) {
        String base = quitarExtension(nombreArchivo);
        String extension = obtenerExtension(nombreArchivo);

        String candidato = nombreArchivo;
        int contador = 1;

        while (existeEnMediaStore(resolver, candidato, relativePath)) {
            candidato = base + " (" + contador + ")" + extension;
            contador++;
        }

        return candidato;
    }

    private boolean existeEnMediaStore(ContentResolver resolver,
                                       String nombreArchivo,
                                       String relativePath) {
        Cursor cursor = null;

        try {
            String[] projection = {MediaStore.Audio.Media._ID};
            String selection = MediaStore.Audio.Media.DISPLAY_NAME + " = ? AND "
                    + MediaStore.Audio.Media.RELATIVE_PATH + " = ?";
            String[] args = {nombreArchivo, relativePath};

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

    private void copiarStreams(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int leido;

        while ((leido = input.read(buffer)) != -1) {
            output.write(buffer, 0, leido);
        }

        output.flush();
    }

    private void escanearArchivos(String rutaAntigua, String rutaNueva) {
        MediaScannerConnection.scanFile(
                context,
                new String[]{rutaAntigua, rutaNueva},
                null,
                null
        );
    }

    private String limpiarNombreArchivo(String texto) {
        if (texto == null) return "";

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
}