package es.iesagora.fd_pdplayer.funcionamiento.mainActivity;

import android.Manifest;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class PermisoNotificacion {

    private final AppCompatActivity activity;

    private ActivityResultLauncher<String> permisoNotificacionesLauncher;

    private boolean iniciado = false;
    private boolean terminado = false;

    private final ArrayList<Runnable> callbacks = new ArrayList<>();

    public PermisoNotificacion(AppCompatActivity activity) {
        this.activity = activity;
        prepararPermisoNotificaciones();
    }

    private void prepararPermisoNotificaciones() {
        permisoNotificacionesLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> marcarComoTerminado()
        );
    }

    public void iniciar() {
        if (iniciado || terminado) return;

        iniciado = true;
        PermisoNotificaciones();
    }

    private void PermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            marcarComoTerminado();
            return;
        }

        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);

        } else {
            marcarComoTerminado();
        }
    }

    public void ejecutarCuandoTerminen(Runnable callback) {
        if (callback == null) return;

        if (terminado) {
            callback.run();
        } else {
            callbacks.add(callback);
        }
    }

    private void marcarComoTerminado() {
        if (terminado) return;

        terminado = true;

        for (Runnable callback : callbacks) {
            if (callback != null) {
                callback.run();
            }
        }

        callbacks.clear();
    }

    public void liberar() {
        callbacks.clear();
    }
}