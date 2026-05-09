package es.iesagora.fd_pdplayer.fragments.internalFragments.otherFragments;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
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
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom.FavoritosLocalRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentModificarCancionBinding;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class ModificarCancionFragment extends Fragment {

    private FragmentModificarCancionBinding binding;
    private Cancion cancion;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;
    private ActivityResultLauncher<String> permisoEscrituraLauncher;
    private ActivityResultLauncher<String> seleccionarImagenLauncher;

    private ModificacionPendiente modificacionPendiente;

    private File imagenSeleccionadaTemporal;

    private String nombrePendiente;
    private String artistaPendiente;
    private String albumPendiente;

    public ModificarCancionFragment() {
        super(R.layout.fragment_modificar_cancion);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deleteRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        continuarTrasPermisoBorrado();
                    } else {
                        cancelarModificacionPendiente();
                    }
                }
        );

        permisoEscrituraLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isAdded() || binding == null) return;

                    if (isGranted) {
                        ejecutarModificacion(
                                safe(nombrePendiente),
                                safe(artistaPendiente),
                                safe(albumPendiente)
                        );
                    } else {
                        VentanasApp.mostrarMensaje(binding.getRoot(), "Permiso de escritura denegado");
                    }
                }
        );

        seleccionarImagenLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (!isAdded() || binding == null || uri == null) return;

                    try {
                        imagenSeleccionadaTemporal = crearImagenTemporalDesdeUri(uri);

                        if (imagenSeleccionadaTemporal == null || !imagenSeleccionadaTemporal.exists()) {
                            VentanasApp.mostrarMensaje(binding.getRoot(), "No se pudo cargar la imagen");
                            return;
                        }

                        Bitmap bitmap = BitmapFactory.decodeFile(imagenSeleccionadaTemporal.getAbsolutePath());

                        if (bitmap != null) {
                            binding.ivImagenEditar.setImageBitmap(bitmap);
                            VentanasApp.mostrarMensaje(binding.getRoot(), "Imagen seleccionada");
                        } else {
                            VentanasApp.mostrarMensaje(binding.getRoot(), "Imagen no válida");
                        }

                    } catch (Exception e) {
                        VentanasApp.mostrarMensaje(binding.getRoot(), "Error al seleccionar imagen");
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentModificarCancionBinding.bind(view);

        recogerDatos();
        pintarDatos();

        binding.btnGuardarCambios.setOnClickListener(v -> guardarCambios());

        binding.btnCambiarImagen.setOnClickListener(v ->
                seleccionarImagenLauncher.launch("image/*")
        );
    }

    private void recogerDatos() {
        if (getArguments() == null) return;
        cancion = (Cancion) getArguments().getSerializable("cancion");
    }

    private void pintarDatos() {
        if (cancion == null) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "No se encontró la canción");
            return;
        }

        binding.etNombreCancion.setText(safe(cancion.getNombre()));
        binding.etArtistaCancion.setText(safe(cancion.getArtista()));
        binding.etAlbumCancion.setText(safe(cancion.getAlbum()));
        binding.tvRutaCancion.setText(safe(cancion.getRutaArchivo()));

        Bitmap imagen = obtenerImagenDesdeArchivo(cancion.getRutaArchivo());

        if (imagen != null) {
            binding.ivImagenEditar.setImageBitmap(imagen);
        } else {
            binding.ivImagenEditar.setImageResource(R.drawable.imagenotfound);
        }
    }

    private void guardarCambios() {
        if (cancion == null) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "No se encontró la canción");
            return;
        }

        String nuevoNombre = obtenerTexto(binding.etNombreCancion);
        String nuevoArtista = obtenerTexto(binding.etArtistaCancion);
        String nuevoAlbum = obtenerTexto(binding.etAlbumCancion);

        if (TextUtils.isEmpty(nuevoNombre)) {
            binding.etNombreCancion.setError("El nombre es obligatorio");
            return;
        }

        if (TextUtils.isEmpty(nuevoArtista)) {
            nuevoArtista = "<unknown>";
        }

        if (TextUtils.isEmpty(nuevoAlbum)) {
            nuevoAlbum = "<unknown>";
        }

        final String nombreFinal = nuevoNombre;
        final String artistaFinal = nuevoArtista;
        final String albumFinal = nuevoAlbum;

        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "ModificarCancion",
                "Guardar cambios",
                "Se creará una nueva canción con estos datos y se eliminará la original.\n\n¿Quieres continuar?",
                "Guardar",
                () -> comprobarPermisoYModificar(nombreFinal, artistaFinal, albumFinal)
        );
    }

    private void comprobarPermisoYModificar(String nombre, String artista, String album) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            nombrePendiente = nombre;
            artistaPendiente = artista;
            albumPendiente = album;

            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                permisoEscrituraLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

        ejecutarModificacion(nombre, artista, album);
    }

    private void ejecutarModificacion(String nuevoNombre, String nuevoArtista, String nuevoAlbum) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "No se encontró la ruta de la canción");
            return;
        }

        Context appContext = requireContext().getApplicationContext();

        setCargando(true);

        executor.execute(() -> {
            File archivoOriginal = new File(cancion.getRutaArchivo());

            if (!archivoOriginal.exists() || !archivoOriginal.isFile()) {
                mostrarError("La canción original no existe en el dispositivo.");
                return;
            }

            File archivoTemporal = null;

            try {
                archivoTemporal = crearArchivoTemporalEditado(
                        appContext,
                        archivoOriginal,
                        nuevoNombre,
                        nuevoArtista,
                        nuevoAlbum,
                        imagenSeleccionadaTemporal
                );

                if (archivoTemporal == null || !archivoTemporal.exists()) {
                    mostrarError("No se pudo preparar la canción modificada.");
                    return;
                }

                ModificacionPendiente pendiente;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    pendiente = guardarNuevoArchivoConMediaStore(
                            appContext,
                            archivoOriginal,
                            archivoTemporal,
                            nuevoNombre,
                            nuevoArtista,
                            nuevoAlbum
                    );
                } else {
                    pendiente = guardarNuevoArchivoConFile(
                            appContext,
                            archivoOriginal,
                            archivoTemporal,
                            nuevoNombre,
                            nuevoArtista,
                            nuevoAlbum
                    );
                }

                if (pendiente == null || TextUtils.isEmpty(pendiente.rutaNueva)) {
                    mostrarError("No se pudo crear la nueva canción.");
                    return;
                }

                modificacionPendiente = pendiente;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pedirPermisoBorrarAndroid11(pendiente);
                    return;
                }

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    intentarBorrarOriginalAndroid10(pendiente);
                    return;
                }

                boolean borrada = archivoOriginal.delete();

                if (!borrada) {
                    borrarDestinoPendiente(appContext, pendiente);
                    mostrarError("No se pudo borrar la canción original.");
                    return;
                }

                finalizarModificacionCorrecta(pendiente);

            } catch (Exception e) {
                if (modificacionPendiente != null) {
                    borrarDestinoPendiente(appContext, modificacionPendiente);
                }

                mostrarError("Error al modificar la canción:\n" + safe(e.getMessage()));

            } finally {
                if (archivoTemporal != null && archivoTemporal.exists()) {
                    archivoTemporal.delete();
                }
            }
        });
    }

    private File crearArchivoTemporalEditado(Context context,
                                             File archivoOriginal,
                                             String nuevoNombre,
                                             String nuevoArtista,
                                             String nuevoAlbum,
                                             File nuevaImagen) throws Exception {
        String extension = obtenerExtension(archivoOriginal.getName());

        File temporal = new File(
                context.getCacheDir(),
                "edit_" + System.currentTimeMillis() + extension
        );

        try (InputStream inputStream = new FileInputStream(archivoOriginal);
             OutputStream outputStream = new FileOutputStream(temporal)) {
            copiarStreams(inputStream, outputStream);
        }

        escribirMetadatos(temporal, nuevoNombre, nuevoArtista, nuevoAlbum, nuevaImagen);

        return temporal;
    }

    private void escribirMetadatos(File archivo,
                                   String nuevoNombre,
                                   String nuevoArtista,
                                   String nuevoAlbum,
                                   File nuevaImagen) throws Exception {
        AudioFile audioFile = AudioFileIO.read(archivo);
        Tag tag = audioFile.getTagOrCreateAndSetDefault();

        tag.setField(FieldKey.TITLE, nuevoNombre);
        tag.setField(FieldKey.ARTIST, nuevoArtista);
        tag.setField(FieldKey.ALBUM, nuevoAlbum);

        if (nuevaImagen != null && nuevaImagen.exists()) {
            try {
                tag.deleteArtworkField();
            } catch (Exception ignored) {
            }

            Artwork artwork = ArtworkFactory.createArtworkFromFile(nuevaImagen);
            tag.setField(artwork);
        }

        audioFile.commit();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private ModificacionPendiente guardarNuevoArchivoConMediaStore(Context context,
                                                                   File archivoOriginal,
                                                                   File archivoTemporal,
                                                                   String nuevoNombre,
                                                                   String nuevoArtista,
                                                                   String nuevoAlbum) {
        Uri uriDestino = null;

        try {
            ContentResolver resolver = context.getContentResolver();

            String relativePath = obtenerRelativePathDesdeOriginal(archivoOriginal);
            String nombreArchivo = crearNombreArchivo(nuevoNombre, nuevoArtista, archivoOriginal);
            String nombreUnico = crearNombreUnicoMediaStore(resolver, nombreArchivo, relativePath);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, nombreUnico);
            values.put(MediaStore.Audio.Media.MIME_TYPE, obtenerMimeType(nombreUnico));
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);

            uriDestino = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

            if (uriDestino == null) {
                return null;
            }

            try (InputStream inputStream = new FileInputStream(archivoTemporal);
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
                rutaNueva = Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/"
                        + relativePath
                        + nombreUnico;
            }

            Uri uriOriginal = obtenerUriMediaStorePorRuta(context, archivoOriginal.getAbsolutePath());

            return new ModificacionPendiente(
                    archivoOriginal.getAbsolutePath(),
                    rutaNueva,
                    uriOriginal,
                    uriDestino,
                    nuevoNombre,
                    nuevoArtista,
                    nuevoAlbum,
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

    private ModificacionPendiente guardarNuevoArchivoConFile(Context context,
                                                             File archivoOriginal,
                                                             File archivoTemporal,
                                                             String nuevoNombre,
                                                             String nuevoArtista,
                                                             String nuevoAlbum) {
        try {
            File carpeta = archivoOriginal.getParentFile();

            if (carpeta == null || !carpeta.exists()) {
                carpeta = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            }

            String nombreArchivo = crearNombreArchivo(nuevoNombre, nuevoArtista, archivoOriginal);
            File destino = crearArchivoUnico(carpeta, nombreArchivo);

            try (InputStream inputStream = new FileInputStream(archivoTemporal);
                 OutputStream outputStream = new FileOutputStream(destino)) {
                copiarStreams(inputStream, outputStream);
            }

            return new ModificacionPendiente(
                    archivoOriginal.getAbsolutePath(),
                    destino.getAbsolutePath(),
                    null,
                    null,
                    nuevoNombre,
                    nuevoArtista,
                    nuevoAlbum,
                    false
            );

        } catch (Exception e) {
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void pedirPermisoBorrarAndroid11(ModificacionPendiente pendiente) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (binding == null) return;

            try {
                if (pendiente.uriOriginal == null) {
                    File original = new File(pendiente.rutaAntigua);

                    if (original.delete()) {
                        finalizarModificacionCorrecta(pendiente);
                    } else {
                        borrarDestinoPendiente(requireContext().getApplicationContext(), pendiente);
                        mostrarError("No se pudo borrar la canción original.");
                    }

                    return;
                }

                ArrayList<Uri> uris = new ArrayList<>();
                uris.add(pendiente.uriOriginal);

                PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                        requireContext().getContentResolver(),
                        uris
                );

                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        pendingIntent.getIntentSender()
                ).build();

                deleteRequestLauncher.launch(request);

            } catch (Exception e) {
                borrarDestinoPendiente(requireContext().getApplicationContext(), pendiente);
                mostrarError("No se pudo pedir permiso para borrar la canción original.");
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void intentarBorrarOriginalAndroid10(ModificacionPendiente pendiente) {
        Context context = requireContext().getApplicationContext();

        try {
            if (pendiente.uriOriginal == null) {
                File original = new File(pendiente.rutaAntigua);

                if (original.delete()) {
                    finalizarModificacionCorrecta(pendiente);
                } else {
                    borrarDestinoPendiente(context, pendiente);
                    mostrarError("No se pudo borrar la canción original.");
                }

                return;
            }

            int borradas = context.getContentResolver().delete(pendiente.uriOriginal, null, null);

            if (borradas > 0) {
                finalizarModificacionCorrecta(pendiente);
            } else {
                borrarDestinoPendiente(context, pendiente);
                mostrarError("No se pudo borrar la canción original.");
            }

        } catch (RecoverableSecurityException e) {
            pendiente.reintentarBorradoAndroid10 = true;

            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;

                try {
                    IntentSenderRequest request = new IntentSenderRequest.Builder(
                            e.getUserAction().getActionIntent().getIntentSender()
                    ).build();

                    deleteRequestLauncher.launch(request);

                } catch (Exception ex) {
                    borrarDestinoPendiente(context, pendiente);
                    mostrarError("No se pudo pedir permiso para borrar la canción original.");
                }
            });

        } catch (Exception e) {
            borrarDestinoPendiente(context, pendiente);
            mostrarError("No se pudo borrar la canción original.");
        }
    }

    private void continuarTrasPermisoBorrado() {
        if (modificacionPendiente == null || !isAdded()) return;

        Context context = requireContext().getApplicationContext();

        executor.execute(() -> {
            try {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                        && modificacionPendiente.reintentarBorradoAndroid10
                        && modificacionPendiente.uriOriginal != null) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        int borradas = context.getContentResolver().delete(
                                modificacionPendiente.uriOriginal,
                                null,
                                null
                        );

                        if (borradas <= 0) {
                            borrarDestinoPendiente(context, modificacionPendiente);
                            mostrarError("No se pudo borrar la canción original.");
                            return;
                        }
                    }
                }

                finalizarModificacionCorrecta(modificacionPendiente);

            } catch (Exception e) {
                borrarDestinoPendiente(context, modificacionPendiente);
                mostrarError("No se pudo terminar la modificación.");
            }
        });
    }

    private void cancelarModificacionPendiente() {
        if (modificacionPendiente == null || !isAdded()) return;

        Context context = requireContext().getApplicationContext();

        executor.execute(() -> {
            borrarDestinoPendiente(context, modificacionPendiente);

            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;

                setCargando(false);
                VentanasApp.mostrarMensaje(binding.getRoot(), "Modificación cancelada");
            });

            modificacionPendiente = null;
        });
    }

    private void finalizarModificacionCorrecta(ModificacionPendiente pendiente) {
        if (!isAdded() || pendiente == null) return;

        actualizarReferencias(pendiente);
        escanearArchivos(
                requireContext().getApplicationContext(),
                pendiente.rutaAntigua,
                pendiente.rutaNueva
        );

        modificacionPendiente = null;

        requireActivity().runOnUiThread(() -> {
            if (binding == null) return;

            setCargando(false);
            VentanasApp.mostrarMensaje(binding.getRoot(), "Canción modificada");

            NavHostFragment.findNavController(this).popBackStack();
        });
    }

    private void actualizarReferencias(ModificacionPendiente pendiente) {
        ListasRepository listasRepository = new ListasRepository(requireActivity().getApplication());
        FavoritosLocalRepository favoritosLocalRepository = new FavoritosLocalRepository(requireActivity().getApplication());
        CancionesOcultasRepository cancionesOcultasRepository = new CancionesOcultasRepository(requireContext());

        listasRepository.actualizarCancion(
                pendiente.rutaAntigua,
                pendiente.rutaNueva,
                pendiente.nombre,
                pendiente.artista,
                pendiente.album
        );

        favoritosLocalRepository.actualizarCancion(
                pendiente.rutaAntigua,
                pendiente.rutaNueva,
                pendiente.nombre,
                pendiente.artista,
                pendiente.album
        );

        cancionesOcultasRepository.actualizarCancion(
                pendiente.rutaAntigua,
                pendiente.rutaNueva,
                pendiente.nombre,
                pendiente.artista,
                pendiente.album
        );
    }

    private void borrarDestinoPendiente(Context context, ModificacionPendiente pendiente) {
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
                return Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id)
                );
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

    private String obtenerRelativePathDesdeOriginal(File archivoOriginal) {
        try {
            File parent = archivoOriginal.getParentFile();

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

    private String crearNombreArchivo(String nombre, String artista, File archivoOriginal) {
        String extension = obtenerExtension(archivoOriginal.getName());

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
            resultado = quitarExtension(archivoOriginal.getName());
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

    private File crearImagenTemporalDesdeUri(Uri uri) throws Exception {
        File archivoImagen = new File(
                requireContext().getCacheDir(),
                "imagen_cancion_" + System.currentTimeMillis() + ".jpg"
        );

        Bitmap bitmap;

        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(inputStream);
        }

        if (bitmap == null) {
            return null;
        }

        try (OutputStream outputStream = new FileOutputStream(archivoImagen)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        }

        return archivoImagen;
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

    private Bitmap obtenerImagenDesdeArchivo(String ruta) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();

            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String obtenerTexto(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
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

    private void setCargando(boolean cargando) {
        if (binding == null) return;

        binding.btnGuardarCambios.setEnabled(!cargando);
        binding.btnCambiarImagen.setEnabled(!cargando);

        if (cargando) {
            binding.btnGuardarCambios.setText("Guardando...");
        } else {
            binding.btnGuardarCambios.setText("Guardar cambios");
        }
    }

    private void mostrarError(String mensaje) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (binding == null) return;

            setCargando(false);
            VentanasApp.mostrarMensaje(binding.getRoot(), mensaje);
        });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ModificacionPendiente {
        String rutaAntigua;
        String rutaNueva;
        Uri uriOriginal;
        Uri uriDestino;
        String nombre;
        String artista;
        String album;
        boolean reintentarBorradoAndroid10;

        ModificacionPendiente(String rutaAntigua,
                              String rutaNueva,
                              Uri uriOriginal,
                              Uri uriDestino,
                              String nombre,
                              String artista,
                              String album,
                              boolean reintentarBorradoAndroid10) {
            this.rutaAntigua = rutaAntigua;
            this.rutaNueva = rutaNueva;
            this.uriOriginal = uriOriginal;
            this.uriDestino = uriDestino;
            this.nombre = nombre;
            this.artista = artista;
            this.album = album;
            this.reintentarBorradoAndroid10 = reintentarBorradoAndroid10;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (imagenSeleccionadaTemporal != null && imagenSeleccionadaTemporal.exists()) {
            imagenSeleccionadaTemporal.delete();
        }

        executor.shutdown();
    }
}