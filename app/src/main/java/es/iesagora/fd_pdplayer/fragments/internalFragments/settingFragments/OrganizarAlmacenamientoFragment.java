package es.iesagora.fd_pdplayer.fragments.internalFragments.settingFragments;

import android.app.Activity;
import android.app.PendingIntent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentOrganizarAlmacenamientoBinding;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.controlAlmacenamiento.OrganizadorAlmacenamiento;
import es.iesagora.fd_pdplayer.funcionamiento.controlAlmacenamiento.ResultadoOrganizacion;

public class OrganizarAlmacenamientoFragment extends Fragment {

    private FragmentOrganizarAlmacenamientoBinding binding;
    private OrganizadorAlmacenamiento organizador;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<IntentSenderRequest> borrarOriginalesLauncher;

    public OrganizarAlmacenamientoFragment() {
        super(R.layout.fragment_organizar_almacenamiento);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        borrarOriginalesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        terminarOrganizacionConPermiso();
                    } else {
                        cancelarOrganizacion();
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentOrganizarAlmacenamientoBinding.bind(view);

        organizador = new OrganizadorAlmacenamiento(
                requireContext(),
                requireActivity().getApplication()
        );

        actualizarEstadoVisual();

        binding.btnOrganizarCanciones.setOnClickListener(v -> {
            if (organizador.organizacionYaRealizada()) {
                confirmarActualizarCarpeta();
            } else {
                empezarOrganizacion();
            }
        });
    }

    private void actualizarEstadoVisual() {
        if (binding == null || organizador == null) return;

        if (organizador.organizacionYaRealizada()) {
            binding.tvEstadoOrganizacion.setText("La carpeta ya fue organizada anteriormente.");
            binding.tvDescripcionOrganizacion.setText(
                    "Puedes actualizar la carpeta \""
                            + OrganizadorAlmacenamiento.NOMBRE_CARPETA
                            + "\" para añadir canciones nuevas."
            );
            binding.btnOrganizarCanciones.setText("Actualizar carpeta");
        } else {
            binding.tvEstadoOrganizacion.setText("La carpeta todavía no se ha organizado.");
            binding.tvDescripcionOrganizacion.setText(
                    "La aplicación creará la carpeta \""
                            + OrganizadorAlmacenamiento.NOMBRE_CARPETA
                            + "\" y moverá allí tus canciones visibles."
            );
            binding.btnOrganizarCanciones.setText("Organizar canciones");
        }
    }

    private void confirmarActualizarCarpeta() {
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "ActualizarCarpeta",
                "Actualizar carpeta",
                "¿Quieres actualizar la carpeta y añadir las canciones nuevas?",
                "Actualizar",
                this::empezarOrganizacion
        );
    }

    private void empezarOrganizacion() {
        setCargando(true, "Preparando canciones...");

        executor.execute(() -> {
            ResultadoOrganizacion resultado = organizador.organizar();

            ejecutarEnPantalla(() -> {
                if (binding == null) return;

                if (organizador.hayPendientes()) {
                    pedirPermisoParaBorrarOriginales();
                } else {
                    mostrarResultado(resultado);
                }
            });
        });
    }

    private void pedirPermisoParaBorrarOriginales() {
        setCargando(true, "Esperando permiso...");

        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "PermisoBorrarOriginales",
                "Permiso necesario",
                "Android necesita que confirmes el borrado de los archivos originales para terminar de mover las canciones.",
                "Continuar",
                this::lanzarPermisoBorrado
        );
    }

    private void lanzarPermisoBorrado() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cancelarOrganizacion();
            return;
        }

        try {
            ArrayList<Uri> uris = organizador.obtenerUrisPendientes();

            if (uris.isEmpty()) {
                terminarOrganizacionConPermiso();
                return;
            }

            PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                    requireContext().getContentResolver(),
                    uris
            );

            IntentSenderRequest request = new IntentSenderRequest.Builder(
                    pendingIntent.getIntentSender()
            ).build();

            borrarOriginalesLauncher.launch(request);

        } catch (Exception e) {
            cancelarOrganizacion();
        }
    }

    private void terminarOrganizacionConPermiso() {
        setCargando(true, "Terminando...");

        executor.execute(() -> {
            ResultadoOrganizacion resultado = organizador.confirmarPendientes();

            ejecutarEnPantalla(() -> mostrarResultado(resultado));
        });
    }

    private void cancelarOrganizacion() {
        setCargando(true, "Cancelando...");

        executor.execute(() -> {
            organizador.cancelarPendientes();

            ejecutarEnPantalla(() -> {
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

    private void mostrarResultado(ResultadoOrganizacion resultado) {
        if (binding == null) return;

        setCargando(false, null);
        actualizarEstadoVisual();

        mostrarDialogo("Resultado", resultado.crearMensaje());
    }

    private void setCargando(boolean cargando, String texto) {
        if (binding == null) return;

        binding.btnOrganizarCanciones.setEnabled(!cargando);

        if (cargando) {
            binding.tvEstadoOrganizacion.setText(texto != null ? texto : "Organizando...");
            binding.btnOrganizarCanciones.setText("Trabajando...");
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

    private void ejecutarEnPantalla(Runnable accion) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (binding != null) {
                accion.run();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}