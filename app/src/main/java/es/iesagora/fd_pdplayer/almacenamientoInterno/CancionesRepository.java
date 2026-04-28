package es.iesagora.fd_pdplayer.almacenamientoInterno;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.models.Cancion;

public class CancionesRepository {

    private final Context context;

    public CancionesRepository(Context context) {
        this.context = context;
    }

    public List<Cancion> getCanciones() {
        return obtenerCancionesDelSistema();
    }

    private List<Cancion> obtenerCancionesDelSistema() {
        List<Cancion> canciones = new ArrayList<>();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE + " ASC"
        );

        if (cursor != null) {
            int colTitulo = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int colArtista = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int colAlbum = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int colRuta = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);


            while (cursor.moveToNext()) {
                String titulo = cursor.getString(colTitulo);
                if (titulo == null || titulo.trim().isEmpty()) {
                    titulo = "<unknown>";
                }

                String artista = cursor.getString(colArtista);
                if (artista == null || artista.trim().isEmpty() || artista.equalsIgnoreCase("<unknown>")) {
                    artista = "<unknown>";
                }

                String album = cursor.getString(colAlbum);
                if (album == null || album.trim().isEmpty() || album.equalsIgnoreCase("<unknown>")) {
                    album = "<unknown>";
                }

                String rutaArchivo = cursor.getString(colRuta);
                String claveUnica = titulo + "|" + artista + "|" + album;

                boolean encontrada = false;
                for (Cancion c : canciones) {
                    String claveExistente = c.getNombre() + "|" + c.getArtista() + "|" + c.getAlbum();
                    if (claveExistente.equals(claveUnica)) {
                        encontrada = true;
                        break;
                    }
                }

                if (!encontrada) {
                    canciones.add(new Cancion(
                            titulo,
                            artista,
                            album,
                            rutaArchivo
                    ));

                }
            }

            cursor.close();
        }

        return canciones;
    }
}
