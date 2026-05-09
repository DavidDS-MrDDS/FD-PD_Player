package es.iesagora.fd_pdplayer.funcionamiento;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.ActivityMainBinding;

public class Navegacion {

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Runnable onDestinoCambiado;

    private NavController navController;
    private NavController.OnDestinationChangedListener destinationChangedListener;

    public Navegacion(AppCompatActivity activity,
                               ActivityMainBinding binding,
                               Runnable onDestinoCambiado) {
        this.activity = activity;
        this.binding = binding;
        this.onDestinoCambiado = onDestinoCambiado;
    }

    public void iniciar() {
        configurarToolbar();
        configurarNavController();
        configurarBotonVolver();
        escucharCambiosDeDestino();
        actualizarEstadoInicial();
    }

    private void configurarToolbar() {
        activity.setSupportActionBar(binding.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void configurarNavController() {
        NavHostFragment navHostFragment = (NavHostFragment) activity
                .getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }
    }

    private void configurarBotonVolver() {
        binding.btnBackToolbar.setImageResource(
                androidx.appcompat.R.drawable.abc_ic_ab_back_material
        );

        binding.btnBackToolbar.setOnClickListener(v -> navigateUp());
    }

    private void escucharCambiosDeDestino() {
        if (navController == null) return;

        destinationChangedListener = (controller, destination, arguments) -> {
            actualizarBotonVolver(destination);

            if (onDestinoCambiado != null) {
                onDestinoCambiado.run();
            }
        };

        navController.addOnDestinationChangedListener(destinationChangedListener);
    }

    private void actualizarEstadoInicial() {
        if (navController == null) return;

        NavDestination destinoActual = navController.getCurrentDestination();

        if (destinoActual != null) {
            actualizarBotonVolver(destinoActual);
        }

        if (onDestinoCambiado != null) {
            onDestinoCambiado.run();
        }
    }

    private void actualizarBotonVolver(NavDestination destination) {
        if (destination == null) return;

        boolean mostrarFlecha = destination.getId() != R.id.principalFragment;

        binding.btnBackToolbar.setVisibility(
                mostrarFlecha ? View.VISIBLE : View.INVISIBLE
        );
    }

    public boolean navigateUp() {
        return navController != null && navController.navigateUp();
    }

    public NavController getNavController() {
        return navController;
    }

    public void liberar() {
        if (navController != null && destinationChangedListener != null) {
            navController.removeOnDestinationChangedListener(destinationChangedListener);
        }

        binding.btnBackToolbar.setOnClickListener(null);

        destinationChangedListener = null;
        navController = null;
    }
}