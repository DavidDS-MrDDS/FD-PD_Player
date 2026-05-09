package es.iesagora.fd_pdplayer.funcionamiento;

import android.Manifest;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class Permisos {

    private final AppCompatActivity activity;

    private ActivityResultLauncher<String> permisoNotificacionesLauncher;

    private boolean iniciado = false;
    private boolean terminado = false;

    private final ArrayList<Runnable> callbacks = new ArrayList<>();

    public Permisos(AppCompatActivity activity) {
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
        pedirPermisoNotificacionesSiHaceFalta();
    }

    private void pedirPermisoNotificacionesSiHaceFalta() {
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

    public boolean haTerminado() {
        return terminado;
    }

    public void liberar() {
        callbacks.clear();
    }
}