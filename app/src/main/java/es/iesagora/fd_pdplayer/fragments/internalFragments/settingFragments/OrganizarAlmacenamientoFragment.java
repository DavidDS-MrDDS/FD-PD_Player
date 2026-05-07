package es.iesagora.fd_pdplayer.fragments.internalFragments.settingFragments;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.CancionesRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentOrganizarAlmacenamientoBinding;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class OrganizarAlmacenamientoFragment extends Fragment {

    private FragmentOrganizarAlmacenamientoBinding binding;

    private static final String PREFS_ORGANIZAR = "organizar_almacenamiento_prefs";
    private static final String KEY_ORGANIZACION_REALIZADA = "organizacion_realizada";
    private static final String NOMBRE_CARPETA = "FD-PD_Player_Canciones";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;

    private final ArrayList<MovimientoPendiente> movimientosPendientes = new ArrayList<>();
    private ResultadoOrganizacion resultadoPendiente = new ResultadoOrganizacion();

    public OrganizarAlmacenamientoFragment() {
        super(R.layout.fragment_organizar_almacenamiento);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deleteRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        confirmarMovimientosPendientes();
                    } else {
                        cancelarMovimientosPendientes();
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentOrganizarAlmacenamientoBinding.bind(view);

        actualizarEstadoVisual();

        binding.btnOrganizarCanciones.setOnClickListener(v -> {
            if (organizacionYaRealizada()) {
                mostrarDialogActualizarCarpeta();
            } else {
                organizarCanciones();
            }
        });
    }

    private void actualizarEstadoVisual() {
        if (binding == null) return;

        if (organizacionYaRealizada()) {
            binding.tvEstadoOrganizacion.setText("La carpeta ya fue organizada anteriormente.");
            binding.tvDescripcionOrganizacion.setText(
                    "Puedes actualizar la carpeta \"" + NOMBRE_CARPETA + "\" para añadir canciones nuevas que todavía no estén organizadas."
            );
            binding.btnOrganizarCanciones.setText("Actualizar carpeta");
        } else {
            binding.tvEstadoOrganizacion.setText("La carpeta todavía no se ha organizado.");
            binding.tvDescripcionOrganizacion.setText(
                    "La aplicación creará la carpeta \"" + NOMBRE_CARPETA + "\" y moverá allí las canciones visibles de Canciones."
            );
            binding.btnOrganizarCanciones.setText("Organizar canciones");
        }
    }

    private boolean organizacionYaRealizada() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ORGANIZAR, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ORGANIZACION_REALIZADA, false);
    }

    private void guardarOrganizacionRealizada() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ORGANIZAR, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ORGANIZACION_REALIZADA, true).apply();
    }

    private void mostrarDialogActualizarCarpeta() {
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "ActualizarCarpeta",
                "Actualizar carpeta",
                "¿Quieres actualizar la carpeta y añadir las nuevas canciones?",
                "Actualizar",
                this::actualizarCarpeta
        );
    }

    private void organizarCanciones() {
        ejecutarOrganizacion();
    }

    private void actualizarCarpeta() {
        ejecutarOrganizacion();
    }

    private void ejecutarOrganizacion() {
        Context appContext = requireContext().getApplicationContext();
        Application application = requireActivity().getApplication();

        movimientosPendientes.clear();
        resultadoPendiente = new ResultadoOrganizacion();

        setCargando(true, "Preparando canciones...");

        executor.execute(() -> {
            try {
                CancionesRepository cancionesRepository = new CancionesRepository(appContext);
                List<Cancion> canciones = cancionesRepository.getCancionesPorFecha(true);

                if (canciones != null) {
                    for (Cancion cancion : canciones) {
                        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
                            resultadoPendiente.fallidas++;
                            continue;
                        }

                        if (estaEnCarpetaOrganizada(cancion.getRutaArchivo())) {
                            resultadoPendiente.yaOrganizadas++;
                            continue;
                        }

                        MovimientoPendiente movimiento;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            movimiento = prepararMovimientoConMediaStore(appContext, cancion);
                        } else {
                            movimiento = moverConFile(appContext, cancion);
                        }

                        if (movimiento == null) {
                            resultadoPendiente.fallidas++;
                            continue;
                        }

                        if (movimiento.necesitaPermisoBorrado) {
                            movimientosPendientes.add(movimiento);
                        } else {
                            actualizarRutas(application, movimiento.rutaAntigua, movimiento.rutaNueva);
                            resultadoPendiente.movidas++;
                        }
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    if (binding == null) return;

                    if (!movimientosPendientes.isEmpty()) {
                        pedirPermisoBorrarOriginales();
                    } else {
                        finalizarOrganizacion();
                    }
                });

            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    if (binding == null) return;

                    setCargando(false, null);
                    mostrarDialogo(
                            "Error al organizar",
                            "No se pudo organizar la carpeta.\n\n" + safe(e.getMessage())
                    );
                });
            }
        });
    }

    private MovimientoPendiente prepararMovimientoConMediaStore(Context context, Cancion cancion) {
        Uri uriDestino = null;

        try {
            File origen = new File(cancion.getRutaArchivo());

            if (!origen.exists() || !origen.isFile()) {
                return null;
            }

            ContentResolver resolver = context.getContentResolver();

            String relativePath = obtenerRelativePathDestino(cancion);
            String nombreArchivo = crearNombreArchivo(cancion, origen);
            String nombreFinal = crearNombreUnicoMediaStore(resolver, nombreArchivo, relativePath);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, nombreFinal);
            values.put(MediaStore.Audio.Media.MIME_TYPE, obtenerMimeType(nombreFinal));
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);

            uriDestino = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

            if (uriDestino == null) {
                return null;
            }

            try (InputStream inputStream = new FileInputStream(origen);
                 OutputStream outputStream = resolver.openOutputStream(uriDestino)) {

                if (outputStream == null) {
                    resolver.delete(uriDestino, null, null);
                    return null;
                }

                copiarStreams(inputStream, outputStream);
            }

            ContentValues finalValues = new ContentValues();
            finalValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(uriDestino, finalValues, null, null);

            String rutaNueva = obtenerRutaDesdeUri(resolver, uriDestino);

            if (TextUtils.isEmpty(rutaNueva)) {
                File destinoEstimado = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        obtenerRutaRelativaFile(cancion) + "/" + nombreFinal
                );
                rutaNueva = destinoEstimado.getAbsolutePath();
            }

            Uri uriOriginal = obtenerUriMediaStorePorRuta(context, origen.getAbsolutePath());

            if (uriOriginal == null) {
                boolean borrada = origen.delete();

                if (!borrada) {
                    resolver.delete(uriDestino, null, null);
                    return null;
                }

                escanearArchivos(context, origen.getAbsolutePath(), rutaNueva);

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

            boolean borrada = borrarOriginalDirecto(context, origen, uriOriginal);

            if (!borrada) {
                resolver.delete(uriDestino, null, null);
                return null;
            }

            escanearArchivos(context, origen.getAbsolutePath(), rutaNueva);

            return new MovimientoPendiente(
                    origen.getAbsolutePath(),
                    rutaNueva,
                    uriOriginal,
                    uriDestino,
                    false
            );

        } catch (Exception e) {
            try {
                if (uriDestino != null) {
                    context.getContentResolver().delete(uriDestino, null, null);
                }
            } catch (Exception ignored) {
            }

            return null;
        }
    }

    private MovimientoPendiente moverConFile(Context context, Cancion cancion) {
        try {
            File origen = new File(cancion.getRutaArchivo());

            if (!origen.exists() || !origen.isFile()) {
                return null;
            }

            File carpeta = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    obtenerRutaRelativaFile(cancion)
            );

            if (!carpeta.exists() && !carpeta.mkdirs()) {
                return null;
            }

            String nombreArchivo = crearNombreArchivo(cancion, origen);
            File destino = crearArchivoUnico(carpeta, nombreArchivo);

            boolean movida = origen.renameTo(destino);

            if (!movida) {
                try (InputStream inputStream = new FileInputStream(origen);
                     OutputStream outputStream = new FileOutputStream(destino)) {
                    copiarStreams(inputStream, outputStream);
                }

                boolean borrada = origen.delete();

                if (!borrada) {
                    destino.delete();
                    return null;
                }
            }

            escanearArchivos(context, origen.getAbsolutePath(), destino.getAbsolutePath());

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

    private void pedirPermisoBorrarOriginales() {
        if (binding == null) return;

        setCargando(true, "Esperando permiso...");

        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "PermisoBorrarOriginales",
                "Permiso necesario",
                "Para mover las canciones sin dejar copias duplicadas, Android necesita que confirmes el borrado de los archivos originales.\n\nDespués de aceptar, la app terminará de actualizar la carpeta.",
                "Continuar",
                this::lanzarPermisoBorrado
        );
    }

    private void lanzarPermisoBorrado() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cancelarMovimientosPendientes();
            return;
        }

        try {
            ArrayList<Uri> uris = new ArrayList<>();

            for (MovimientoPendiente movimiento : movimientosPendientes) {
                if (movimiento.uriOriginal != null) {
                    uris.add(movimiento.uriOriginal);
                }
            }

            if (uris.isEmpty()) {
                finalizarOrganizacion();
                return;
            }

            PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                    requireContext().getContentResolver(),
                    uris
            );

            IntentSenderRequest request = new IntentSenderRequest.Builder(
                    pendingIntent.getIntentSender()
            ).build();

            deleteRequestLauncher.launch(request);

        } catch (Exception e) {
            cancelarMovimientosPendientes();
        }
    }

    private void confirmarMovimientosPendientes() {
        if (!isAdded()) return;

        Application application = requireActivity().getApplication();
        Context context = requireContext().getApplicationContext();

        executor.execute(() -> {
            for (MovimientoPendiente movimiento : movimientosPendientes) {
                actualizarRutas(application, movimiento.rutaAntigua, movimiento.rutaNueva);
                escanearArchivos(context, movimiento.rutaAntigua, movimiento.rutaNueva);
                resultadoPendiente.movidas++;
            }

            movimientosPendientes.clear();

            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                finalizarOrganizacion();
            });
        });
    }

    private void cancelarMovimientosPendientes() {
        if (!isAdded()) return;

        Context context = requireContext().getApplicationContext();

        executor.execute(() -> {
            for (MovimientoPendiente movimiento : movimientosPendientes) {
                borrarDestinoPendiente(context, movimiento);
                resultadoPendiente.fallidas++;
            }

            movimientosPendientes.clear();

            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;

                setCargando(false, null);
                actualizarEstadoVisual();

                mostrarDialogo(
                        "Organización cancelada",
                        "No se borraron las canciones originales, así que se han eliminado las copias creadas para evitar duplicados."
                );
            });
        });
    }

    private void finalizarOrganizacion() {
        if (binding == null) return;

        if (resultadoPendiente.fallidas == 0) {
            guardarOrganizacionRealizada();
        }

        setCargando(false, null);
        actualizarEstadoVisual();

        String mensaje = "Organización terminada.\n\n"
                + "Movidas: " + resultadoPendiente.movidas + "\n"
                + "Ya organizadas: " + resultadoPendiente.yaOrganizadas + "\n"
                + "Fallidas: " + resultadoPendiente.fallidas;

        mostrarDialogo("Resultado", mensaje);
    }

    private void actualizarRutas(Application application, String rutaAntigua, String rutaNueva) {
        ListasRepository listasRepository = new ListasRepository(application);
        FavoritosLocalRepository favoritosLocalRepository = new FavoritosLocalRepository(application);

        listasRepository.actualizarRutaCancion(rutaAntigua, rutaNueva);
        favoritosLocalRepository.actualizarRutaCancion(rutaAntigua, rutaNueva);
    }

    private void borrarDestinoPendiente(Context context, MovimientoPendiente movimiento) {
        try {
            if (movimiento.uriDestino != null) {
                context.getContentResolver().delete(movimiento.uriDestino, null, null);
            }
        } catch (Exception ignored) {
        }

        try {
            if (!TextUtils.isEmpty(movimiento.rutaNueva)) {
                File archivo = new File(movimiento.rutaNueva);

                if (archivo.exists()) {
                    archivo.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean borrarOriginalDirecto(Context context, File origen, Uri uriOriginal) {
        try {
            if (origen.delete()) {
                return true;
            }

            int borradas = context.getContentResolver().delete(uriOriginal, null, null);
            return borradas > 0 || !origen.exists();

        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private Uri obtenerUriMediaStorePorRuta(Context context, String ruta) {
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
                return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            }

            return null;

        } catch (Exception e) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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

            return null;

        } catch (Exception e) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean estaEnCarpetaOrganizada(String ruta) {
        if (TextUtils.isEmpty(ruta)) {
            return false;
        }

        String normalizada = ruta.replace("\\", "/").toLowerCase();
        return normalizada.contains("/" + NOMBRE_CARPETA.toLowerCase() + "/");
    }

    private String obtenerRelativePathDestino(Cancion cancion) {
        String album = obtenerNombreAlbumValido(cancion);

        if (TextUtils.isEmpty(album)) {
            return Environment.DIRECTORY_MUSIC + "/" + NOMBRE_CARPETA + "/";
        }

        return Environment.DIRECTORY_MUSIC + "/" + NOMBRE_CARPETA + "/" + album + "/";
    }

    private String obtenerRutaRelativaFile(Cancion cancion) {
        String album = obtenerNombreAlbumValido(cancion);

        if (TextUtils.isEmpty(album)) {
            return NOMBRE_CARPETA;
        }

        return NOMBRE_CARPETA + "/" + album;
    }

    private String obtenerNombreAlbumValido(Cancion cancion) {
        if (cancion == null) return "";

        String album = safe(cancion.getAlbum()).trim();

        if (TextUtils.isEmpty(album)) return "";
        if (album.equalsIgnoreCase("<unknown>")) return "";
        if (album.equalsIgnoreCase("unknown")) return "";
        if (album.equalsIgnoreCase("download")) return "";
        if (album.equalsIgnoreCase("downloads")) return "";

        return limpiarNombreArchivo(album);
    }

    private String limpiarNombreArchivo(String texto) {
        if (texto == null) return "";

        return texto
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
    }

    private String crearNombreUnicoMediaStore(ContentResolver resolver, String nombreArchivo, String relativePath) {
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

    private boolean existeEnMediaStore(ContentResolver resolver, String nombreArchivo, String relativePath) {
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

    private void copiarStreams(InputStream inputStream, OutputStream outputStream) throws Exception {
        byte[] buffer = new byte[8192];
        int leido;

        while ((leido = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, leido);
        }

        outputStream.flush();
    }

    private void escanearArchivos(Context context, String rutaAntigua, String rutaNueva) {
        MediaScannerConnection.scanFile(
                context,
                new String[]{rutaAntigua, rutaNueva},
                null,
                null
        );
    }

    private String crearNombreArchivo(Cancion cancion, File origen) {
        String nombre = limpiarNombreArchivo(safe(cancion.getNombre()));
        String artista = limpiarNombreArchivo(safe(cancion.getArtista()));
        String extension = obtenerExtension(origen.getName());

        String nombreArchivo;

        if (!TextUtils.isEmpty(artista) && !TextUtils.isEmpty(nombre)) {
            nombreArchivo = artista + " - " + nombre;
        } else if (!TextUtils.isEmpty(nombre)) {
            nombreArchivo = nombre;
        } else {
            nombreArchivo = quitarExtension(origen.getName());
        }

        nombreArchivo = limpiarNombreArchivo(nombreArchivo);

        if (TextUtils.isEmpty(nombreArchivo)) {
            nombreArchivo = "cancion_" + System.currentTimeMillis();
        }

        if (!nombreArchivo.toLowerCase().endsWith(extension.toLowerCase())) {
            nombreArchivo += extension;
        }

        return nombreArchivo;
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

    private void setCargando(boolean cargando, String texto) {
        if (binding == null) return;

        binding.btnOrganizarCanciones.setEnabled(!cargando);

        if (cargando) {
            binding.tvEstadoOrganizacion.setText(texto != null ? texto : "Organizando...");
            binding.btnOrganizarCanciones.setText("Trabajando...");
        } else {
            binding.btnOrganizarCanciones.setEnabled(true);
        }
    }

    private void mostrarDialogo(String titulo, String mensaje) {
        if (!isAdded()) return;

        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "MensajeResultado",
                titulo,
                mensaje,
                "Aceptar",
                null
        );
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

    private static class ResultadoOrganizacion {
        int movidas = 0;
        int yaOrganizadas = 0;
        int fallidas = 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}