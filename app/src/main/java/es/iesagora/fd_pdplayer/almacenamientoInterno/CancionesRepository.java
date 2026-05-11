package es.iesagora.fd_pdplayer.almacenamientoInterno;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionesRepository {

    private final Context context;
    private final CancionesOcultasRepository cancionesOcultasRepository;

    public CancionesRepository(Context context) {
        this.context = context.getApplicationContext();
        cancionesOcultasRepository = new CancionesOcultasRepository(context);
    }

    public List<Cancion> getCancionesPorFecha(boolean masNuevasPrimero) {
        String orden = masNuevasPrimero
                ? MediaStore.Audio.Media.DATE_ADDED + " DESC"
                : MediaStore.Audio.Media.DATE_ADDED + " ASC";

        return obtenerCancionesDelSistema(orden);
    }

    private List<Cancion> obtenerCancionesDelSistema(String sortOrder) {
        List<Cancion> canciones = new ArrayList<>();
        Set<String> rutasOcultas = new HashSet<>(cancionesOcultasRepository.obtenerRutasOcultasSync());

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
        );

        if (cursor != null) {
            int colTitulo = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int colArtista = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int colAlbum = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int colRuta = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                String rutaArchivo = cursor.getString(colRuta);

                if (TextUtils.isEmpty(rutaArchivo) || rutasOcultas.contains(rutaArchivo)) {
                    continue;
                }

                String tituloMediaStore = cursor.getString(colTitulo);
                String artistaMediaStore = cursor.getString(colArtista);
                String albumMediaStore = cursor.getString(colAlbum);

                Cancion cancion = construirCancionConMetadatos(
                        rutaArchivo,
                        tituloMediaStore,
                        artistaMediaStore,
                        albumMediaStore
                );

                if (!yaExisteCancion(canciones, cancion)) {
                    canciones.add(cancion);
                }
            }

            cursor.close();
        }

        return canciones;
    }

    private Cancion construirCancionConMetadatos(String rutaArchivo,
                                                 String tituloMediaStore,
                                                 String artistaMediaStore,
                                                 String albumMediaStore) {

        String titulo = limpiarValor(tituloMediaStore);
        String artista = limpiarValor(artistaMediaStore);
        String album = limpiarValor(albumMediaStore);

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();

        try {
            mmr.setDataSource(rutaArchivo);

            String tituloReal = limpiarValor(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            String artistaReal = limpiarValor(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            String albumReal = limpiarValor(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));

            // Prioritize real metadata from MediaMetadataRetriever
            if (!TextUtils.isEmpty(tituloReal)) {
                titulo = tituloReal;
            }
            if (!TextUtils.isEmpty(artistaReal)) {
                artista = artistaReal;
            }
            if (!TextUtils.isEmpty(albumReal)) {
                album = albumReal;
            }

        } catch (Exception ignored) {
            // Log the exception if needed, but for now, continue with MediaStore/filename
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }

        String nombreArchivoSinExtension = obtenerNombreArchivoSinExtension(rutaArchivo);

        // --- Handle Title ---
        if (TextUtils.isEmpty(titulo)) {
            titulo = nombreArchivoSinExtension;
        }
        titulo = limpiarTituloVisible(titulo); // Clean common suffixes like (Audio)

        // Attempt to parse Artist - Title from the title field if it looks like it
        DatosDesdeNombre datosDesdeTitulo = intentarSepararArtistaYTitulo(titulo);
        if (datosDesdeTitulo != null) {
            if (TextUtils.isEmpty(artista) || artista.equalsIgnoreCase("<unknown>") || titulo.toLowerCase().startsWith(artista.toLowerCase() + " - ")) {
                artista = datosDesdeTitulo.artista;
            }
            titulo = datosDesdeTitulo.titulo;
        } else {
            // If title itself didn't have "Artist - Title", try from the raw filename
            DatosDesdeNombre datosDesdeArchivo = intentarSepararArtistaYTitulo(limpiarTituloVisible(nombreArchivoSinExtension));
            if (datosDesdeArchivo != null) {
                // Only update title if the current title is generic or starts with filename artist
                boolean tituloEsGenericoOIncluyeArtistaArchivo = TextUtils.isEmpty(titulo)
                                                        || titulo.equalsIgnoreCase(nombreArchivoSinExtension)
                                                        || titulo.toLowerCase().startsWith(datosDesdeArchivo.artista.toLowerCase() + " - ");
                if (tituloEsGenericoOIncluyeArtistaArchivo) {
                    titulo = datosDesdeArchivo.titulo;
                }
                if (TextUtils.isEmpty(artista) || artista.equalsIgnoreCase("<unknown>")) {
                    artista = datosDesdeArchivo.artista;
                }
            }
        }
        // Final fallback for title if still empty
        if (TextUtils.isEmpty(titulo)) {
            titulo = nombreArchivoSinExtension;
        }


        // --- Handle Artist ---
        if (TextUtils.isEmpty(artista) || artista.equalsIgnoreCase("<unknown>")) {
            artista = "<unknown>";
        }

        // --- Handle Album ---
        // If the album is still empty after MediaMetadataRetriever, it should default to <unknown>
        if (TextUtils.isEmpty(album) || album.equalsIgnoreCase("<unknown>")) {
            album = "<unknown>";
        } else {
            // Otherwise, check if the album appears to be just a generic filename or title copy
            if (albumPareceIncorrecto(album, titulo, artista, nombreArchivoSinExtension)) {
                album = "<unknown>";
            }
        }


        return new Cancion(
                titulo,
                artista,
                album,
                rutaArchivo
        );
    }

    private boolean yaExisteCancion(List<Cancion> canciones, Cancion nueva) {
        if (canciones == null || nueva == null) {
            return false;
        }

        String rutaNueva = safe(nueva.getRutaArchivo());

        for (Cancion existente : canciones) {
            if (rutaNueva.equals(safe(existente.getRutaArchivo()))) {
                return true;
            }
        }

        return false;
    }

    private String limpiarValor(String valor) {
        if (valor == null) {
            return "";
        }

        valor = valor.trim();

        if (valor.isEmpty()) {
            return "";
        }

        if (valor.equalsIgnoreCase("<unknown>")
                || valor.equalsIgnoreCase("unknown")
                || valor.equalsIgnoreCase("null")) {
            return "";
        }

        return valor;
    }

    private String obtenerNombreArchivoSinExtension(String rutaArchivo) {
        if (TextUtils.isEmpty(rutaArchivo)) {
            return "";
        }

        File file = new File(rutaArchivo);
        String nombre = file.getName();

        return quitarExtensionSiExiste(nombre);
    }

    private String quitarExtensionSiExiste(String texto) {
        if (TextUtils.isEmpty(texto)) {
            return "";
        }

        int punto = texto.lastIndexOf(".");

        if (punto > 0) {
            return texto.substring(0, punto);
        }

        return texto;
    }

    private String limpiarTituloVisible(String titulo) {
        if (titulo == null) {
            return "";
        }

        String limpio = titulo.trim();

        limpio = limpio.replaceAll("(?i)\\s*\\(audio\\)\\s*$", "");
        limpio = limpio.replaceAll("(?i)\\s*\\[audio\\]\\s*$", "");
        limpio = limpio.replaceAll("(?i)\\s*\\(official audio\\)\\s*$", "");
        limpio = limpio.replaceAll("(?i)\\s*\\[official audio\\]\\s*$", "");
        limpio = limpio.replaceAll("(?i)\\s*\\(lyrics\\)\\s*$", "");
        limpio = limpio.replaceAll("(?i)\\s*\\[lyrics\\]\\s*$", "");

        return limpio.trim();
    }

    private DatosDesdeNombre intentarSepararArtistaYTitulo(String texto) {
        if (TextUtils.isEmpty(texto)) {
            return null;
        }

        String limpio = texto.trim();

        String separador = null;

        if (limpio.contains(" - ")) {
            separador = " - ";
        } else if (limpio.contains(" – ")) {
            separador = " – ";
        }

        if (separador == null) {
            return null;
        }

        String[] partes = limpio.split(java.util.regex.Pattern.quote(separador), 2);

        if (partes.length != 2) {
            return null;
        }

        String posibleArtista = partes[0].trim();
        String posibleTitulo = partes[1].trim();

        posibleTitulo = limpiarTituloVisible(posibleTitulo);

        if (TextUtils.isEmpty(posibleArtista) || TextUtils.isEmpty(posibleTitulo)) {
            return null;
        }

        return new DatosDesdeNombre(posibleArtista, posibleTitulo);
    }

    private boolean albumPareceIncorrecto(String album,
                                          String titulo,
                                          String artista,
                                          String nombreArchivoSinExtension) {
        if (TextUtils.isEmpty(album)) {
            return true;
        }

        if (album.equalsIgnoreCase("<unknown>")) {
            return false;
        }

        String albumLimpio = limpiarTituloVisible(album);
        String tituloLimpio = limpiarTituloVisible(titulo);
        String archivoLimpio = limpiarTituloVisible(nombreArchivoSinExtension);

        if (albumLimpio.equalsIgnoreCase(tituloLimpio)) {
            return true;
        }

        if (albumLimpio.equalsIgnoreCase(archivoLimpio)) {
            return true;
        }

        if (!TextUtils.isEmpty(artista)
                && albumLimpio.equalsIgnoreCase(artista + " - " + tituloLimpio)) {
            return true;
        }

        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class DatosDesdeNombre {
        String artista;
        String titulo;

        DatosDesdeNombre(String artista, String titulo) {
            this.artista = artista;
            this.titulo = titulo;
        }
    }
}