package es.iesagora.fd_pdplayer;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;

import es.iesagora.fd_pdplayer.databinding.ActivityMainBinding;
import es.iesagora.fd_pdplayer.funcionamiento.MiniPlayer;
import es.iesagora.fd_pdplayer.funcionamiento.mainActivity.PermisoNotificacion;
import es.iesagora.fd_pdplayer.funcionamiento.mainActivity.Navegacion;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorNotificacion;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private NavController navController;

    // Se ocupa del funcionamiento de la Toolbar.
    private Navegacion navegacion;

    // Se ocupa de solicitar el permiso de notificación.
    private PermisoNotificacion permisoNotificacion;

    // Controla el minireproductor.
    private MiniPlayer miniPlayer;

    // Se ocupa de la reproducción de música ya sea teniendo "CancionFragment" activo o no.
    private final ReproductorApp reproductorApp = ReproductorApp.getInstance();

    // Se ocupa de la notificación para controlar la reproducción de música.
    private ReproductorNotificacion reproductorNotificacion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configurarPermisos();
        configurarNavegacion();
        configurarNotificacionReproductor();
        configurarMiniPlayer();

        permisoNotificacion.iniciar();
    }

    private void configurarPermisos() {
        permisoNotificacion = new PermisoNotificacion(this);
    }

    private void configurarNavegacion() {
        navegacion = new Navegacion(
                this,
                binding,
                () -> {
                    // Cada vez que cambia de fragmento avisa al miniplayer para saber si mostrarse o no.
                    if (miniPlayer != null) {
                        miniPlayer.actualizarVisibilidadMiniPlayer();
                    }
                }
        );

        navegacion.iniciar();
        navController = navegacion.getNavController();
    }

    private void configurarNotificacionReproductor() {
        reproductorNotificacion = new ReproductorNotificacion(this);
        reproductorNotificacion.crearCanal();

        // ReproductorNotificacion recibe cambios de ReproductorApp.
        reproductorApp.addListener(reproductorNotificacion);
    }

    private void configurarMiniPlayer() {
        // Informa sobre el miniplayer a los fragmentos y al miniplayer del estado del reproductor.
        miniPlayer = new MiniPlayer(
                this,
                binding,
                navController,
                reproductorApp,
                reproductorNotificacion
        );

        miniPlayer.iniciar();
    }

    public void ejecutarCuandoPermisosInicialesTerminen(Runnable callback) {
        // Evita que el permiso de almacenamiento sea tapado por el de notificaciones y no llegue a salir.
        if (callback == null) return;
        if (permisoNotificacion != null) {
            permisoNotificacion.ejecutarCuandoTerminen(callback);
        } else {
            callback.run();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // El funcionamiento del botón "volver" se encuentra en Navegacion.
        if (navegacion != null && navegacion.navigateUp()) {
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (miniPlayer != null) {
            miniPlayer.liberar();
            miniPlayer = null;
        }
        if (reproductorNotificacion != null) {
            reproductorApp.removeListener(reproductorNotificacion);
        }
        if (isFinishing()) {
            reproductorApp.liberar();

            if (reproductorNotificacion != null) {
                reproductorNotificacion.cancelar();
            }
        }
        if (reproductorNotificacion != null) {
            reproductorNotificacion.liberar();
            reproductorNotificacion = null;
        }
        if (navegacion != null) {
            navegacion.liberar();
            navegacion = null;
        }
        if (permisoNotificacion != null) {
            permisoNotificacion.liberar();
            permisoNotificacion = null;
        }
        navController = null;
        binding = null;
    }
}