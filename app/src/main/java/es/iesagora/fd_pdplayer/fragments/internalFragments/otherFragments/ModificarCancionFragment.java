package es.iesagora.fd_pdplayer.fragments.internalFragments.otherFragments;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentModificarCancionBinding;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;
import es.iesagora.fd_pdplayer.funcionamiento.modificacionCanciones.ModificacionPendiente;
import es.iesagora.fd_pdplayer.funcionamiento.modificacionCanciones.ModificarCancionManager;

public class ModificarCancionFragment extends Fragment {

    private FragmentModificarCancionBinding binding;
    private Cancion cancion;

    private ModificarCancionManager manager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;
    private ActivityResultLauncher<String> permisoEscrituraLauncher;
    private ActivityResultLauncher<String> seleccionarImagenLauncher;

    private volatile ModificacionPendiente modificacionPendiente;

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
        registrarLaunchers();
    }

    private void registrarLaunchers() {
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
                        mostrarMensaje("Permiso de escritura denegado");
                    }
                }
        );

        seleccionarImagenLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::seleccionarImagen
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentModificarCancionBinding.bind(view);
        manager = new ModificarCancionManager(requireActivity().getApplication());

        recogerDatos();
        pintarDatos();
        configurarBotones();
    }

    private void configurarBotones() {
        binding.btnGuardarCambios.setOnClickListener(v -> guardarCambios());
        binding.btnCambiarImagen.setOnClickListener(v -> seleccionarImagenLauncher.launch("image/*"));
    }

    private void recogerDatos() {
        if (getArguments() == null) return;
        cancion = obtenerCancionArgumento(getArguments(), "cancion");
    }

    private void pintarDatos() {
        if (cancion == null) {
            mostrarMensaje("No se encontró la canción");
            return;
        }

        binding.etNombreCancion.setText(safe(cancion.getNombre()));
        binding.etArtistaCancion.setText(safe(cancion.getArtista()));
        binding.etAlbumCancion.setText(safe(cancion.getAlbum()));
        binding.tvRutaCancion.setText(safe(cancion.getRutaArchivo()));
        binding.ivImagenEditar.setImageResource(R.drawable.imagenotfound);

        executor.execute(() -> {
            Bitmap imagen = manager.obtenerImagenDesdeArchivo(cancion.getRutaArchivo());

            runUi(() -> {
                if (imagen != null) {
                    binding.ivImagenEditar.setImageBitmap(imagen);
                } else {
                    binding.ivImagenEditar.setImageResource(R.drawable.imagenotfound);
                }
            });
        });
    }

    private void seleccionarImagen(Uri uri) {
        if (!isAdded() || binding == null || uri == null || manager == null) return;

        try {
            File nuevaTemporal = manager.crearImagenTemporalDesdeUri(uri);

            if (nuevaTemporal == null || !nuevaTemporal.exists()) {
                mostrarMensaje("No se pudo cargar la imagen");
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(nuevaTemporal.getAbsolutePath());

            if (bitmap == null) {
                nuevaTemporal.delete();
                mostrarMensaje("Imagen no válida");
                return;
            }

            eliminarImagenTemporal();

            imagenSeleccionadaTemporal = nuevaTemporal;
            binding.ivImagenEditar.setImageBitmap(bitmap);
            mostrarMensaje("Imagen seleccionada");

        } catch (Exception e) {
            mostrarMensaje("Error al seleccionar imagen");
        }
    }

    private void guardarCambios() {
        if (cancion == null) {
            mostrarMensaje("No se encontró la canción");
            return;
        }

        String nombre = obtenerTexto(binding.etNombreCancion);
        String artista = obtenerTexto(binding.etArtistaCancion);
        String album = obtenerTexto(binding.etAlbumCancion);

        if (TextUtils.isEmpty(nombre)) {
            binding.etNombreCancion.setError("El nombre es obligatorio");
            return;
        }

        if (TextUtils.isEmpty(artista)) artista = "<unknown>";
        if (TextUtils.isEmpty(album)) album = "<unknown>";

        String nombreFinal = nombre;
        String artistaFinal = artista;
        String albumFinal = album;

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

            boolean permisoConcedido = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED;

            if (!permisoConcedido) {
                permisoEscrituraLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

        ejecutarModificacion(nombre, artista, album);
    }

    private void ejecutarModificacion(String nombre, String artista, String album) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            mostrarMensaje("No se encontró la ruta de la canción");
            return;
        }

        setCargando(true);

        executor.execute(() -> {
            ModificacionPendiente pendiente = null;

            try {
                pendiente = manager.prepararNuevaCancion(
                        cancion,
                        nombre,
                        artista,
                        album,
                        imagenSeleccionadaTemporal
                );

                modificacionPendiente = pendiente;
                resolverBorradoOriginal(pendiente);

            } catch (Exception e) {
                if (pendiente != null) {
                    manager.borrarDestinoPendiente(pendiente);
                }

                mostrarError("Error al modificar la canción:\n" + safe(e.getMessage()));
            }
        });
    }

    private void resolverBorradoOriginal(ModificacionPendiente pendiente) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pedirPermisoBorrarAndroid11(pendiente);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            borrarOriginalAndroid10(pendiente);
            return;
        }

        borrarOriginalDirecto(pendiente);
    }

    private void borrarOriginalDirecto(ModificacionPendiente pendiente) {
        try {
            if (!manager.borrarOriginalDirecto(pendiente)) {
                manager.borrarDestinoPendiente(pendiente);
                mostrarError("No se pudo borrar la canción original.");
                return;
            }

            finalizarModificacionCorrecta(pendiente);

        } catch (Exception e) {
            manager.borrarDestinoPendiente(pendiente);
            mostrarError("No se pudo borrar la canción original.");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void borrarOriginalAndroid10(ModificacionPendiente pendiente) {
        try {
            if (!manager.borrarOriginalDirecto(pendiente)) {
                manager.borrarDestinoPendiente(pendiente);
                mostrarError("No se pudo borrar la canción original.");
                return;
            }

            finalizarModificacionCorrecta(pendiente);

        } catch (RecoverableSecurityException e) {
            pendiente.reintentarBorradoAndroid10 = true;
            lanzarPermisoAndroid10(e, pendiente);

        } catch (Exception e) {
            manager.borrarDestinoPendiente(pendiente);
            mostrarError("No se pudo borrar la canción original.");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void lanzarPermisoAndroid10(RecoverableSecurityException e,
                                        ModificacionPendiente pendiente) {
        runUi(() -> {
            try {
                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        e.getUserAction().getActionIntent().getIntentSender()
                ).build();

                deleteRequestLauncher.launch(request);

            } catch (Exception ex) {
                manager.borrarDestinoPendiente(pendiente);
                mostrarError("No se pudo pedir permiso para borrar la canción original.");
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void pedirPermisoBorrarAndroid11(ModificacionPendiente pendiente) {
        runUi(() -> {
            try {
                if (pendiente.uriOriginal == null) {
                    executor.execute(() -> borrarOriginalAndroid10(pendiente));
                    return;
                }

                PendingIntent pendingIntent = manager.crearDeleteRequest(pendiente);

                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        pendingIntent.getIntentSender()
                ).build();

                deleteRequestLauncher.launch(request);

            } catch (Exception e) {
                manager.borrarDestinoPendiente(pendiente);
                mostrarError("No se pudo pedir permiso para borrar la canción original.");
            }
        });
    }

    private void continuarTrasPermisoBorrado() {
        ModificacionPendiente pendiente = modificacionPendiente;

        if (pendiente == null || !isAdded()) return;

        executor.execute(() -> {
            try {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                        && pendiente.reintentarBorradoAndroid10
                        && pendiente.uriOriginal != null) {

                    if (!manager.borrarOriginalDirecto(pendiente)) {
                        manager.borrarDestinoPendiente(pendiente);
                        mostrarError("No se pudo borrar la canción original.");
                        return;
                    }
                }

                finalizarModificacionCorrecta(pendiente);

            } catch (Exception e) {
                manager.borrarDestinoPendiente(pendiente);
                mostrarError("No se pudo terminar la modificación.");
            }
        });
    }

    private void cancelarModificacionPendiente() {
        ModificacionPendiente pendiente = modificacionPendiente;

        if (pendiente == null || !isAdded()) return;

        executor.execute(() -> {
            manager.borrarDestinoPendiente(pendiente);
            modificacionPendiente = null;

            runUi(() -> {
                setCargando(false);
                mostrarMensaje("Modificación cancelada");
            });
        });
    }

    private void finalizarModificacionCorrecta(ModificacionPendiente pendiente) {
        if (pendiente == null || !isAdded()) return;

        manager.finalizarModificacion(pendiente);
        modificacionPendiente = null;

        runUi(() -> {
            setCargando(false);
            mostrarMensaje("Canción modificada");
            NavHostFragment.findNavController(ModificarCancionFragment.this).popBackStack();
        });
    }

    private void setCargando(boolean cargando) {
        if (binding == null) return;

        binding.btnGuardarCambios.setEnabled(!cargando);
        binding.btnCambiarImagen.setEnabled(!cargando);
        binding.btnGuardarCambios.setText(cargando ? "Guardando..." : "Guardar cambios");
    }

    private void mostrarError(String mensaje) {
        runUi(() -> {
            setCargando(false);
            mostrarMensaje(mensaje);
        });
    }

    private void mostrarMensaje(String mensaje) {
        if (binding != null && !TextUtils.isEmpty(mensaje)) {
            VentanasApp.mostrarMensaje(binding.getRoot(), mensaje);
        }
    }

    private void runUi(Runnable action) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (binding != null) {
                action.run();
            }
        });
    }

    private String obtenerTexto(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void eliminarImagenTemporal() {
        if (imagenSeleccionadaTemporal != null && imagenSeleccionadaTemporal.exists()) {
            imagenSeleccionadaTemporal.delete();
        }

        imagenSeleccionadaTemporal = null;
    }

    @SuppressWarnings("deprecation")
    private Cancion obtenerCancionArgumento(Bundle args, String key) {
        if (args == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return args.getSerializable(key, Cancion.class);
        } else {
            return (Cancion) args.getSerializable(key);
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

        eliminarImagenTemporal();
        executor.shutdown();
    }
}