package es.iesagora.fd_pdplayer;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;

import es.iesagora.fd_pdplayer.databinding.ActivityMainBinding;
import es.iesagora.fd_pdplayer.funcionamiento.MiniPlayer;
import es.iesagora.fd_pdplayer.funcionamiento.Navegacion;
import es.iesagora.fd_pdplayer.funcionamiento.Permisos;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorNotificacion;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private NavController navController;

    private Navegacion navegacion;
    private Permisos permisos;
    private MiniPlayer miniPlayer;

    private final ReproductorApp reproductorApp = ReproductorApp.getInstance();
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

        permisos.iniciar();
    }

    private void configurarPermisos() {
        permisos = new Permisos(this);
    }

    private void configurarNavegacion() {
        navegacion = new Navegacion(
                this,
                binding,
                () -> {
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

        reproductorApp.addListener(reproductorNotificacion);
    }

    private void configurarMiniPlayer() {
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
        if (callback == null) return;

        if (permisos != null) {
            permisos.ejecutarCuandoTerminen(callback);
        } else {
            callback.run();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
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

        if (permisos != null) {
            permisos.liberar();
            permisos = null;
        }

        navController = null;
        binding = null;
    }
}