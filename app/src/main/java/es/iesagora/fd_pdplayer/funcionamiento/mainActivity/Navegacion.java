package es.iesagora.fd_pdplayer.funcionamiento.mainActivity;

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

        if (!prepararNavController()) {
            return;
        }

        configurarBotonVolver();
        escucharCambiosPantalla();
        actualizarPantallaActual();
    }

    private void configurarToolbar() {
        activity.setSupportActionBar(binding.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private boolean prepararNavController() {
        NavHostFragment navHostFragment = (NavHostFragment) activity
                .getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) {
            return false;
        }

        navController = navHostFragment.getNavController();
        return true;
    }

    private void configurarBotonVolver() {
        binding.btnBackToolbar.setImageResource(R.drawable.ic_arrow);
        binding.btnBackToolbar.setOnClickListener(v -> navigateUp());
    }

    private void escucharCambiosPantalla() {
        destinationChangedListener = (controller, destination, arguments) -> {
            actualizarBotonVolver(destination);
            avisarCambioDestino();
        };

        navController.addOnDestinationChangedListener(destinationChangedListener);
    }

    private void actualizarPantallaActual() {
        NavDestination destinoActual = navController.getCurrentDestination();

        if (destinoActual != null) {
            actualizarBotonVolver(destinoActual);
        }

        avisarCambioDestino();
    }

    private void actualizarBotonVolver(NavDestination destination) {
        if (destination.getId() == R.id.principalFragment) {
            binding.btnBackToolbar.setVisibility(View.INVISIBLE);
        } else {
            binding.btnBackToolbar.setVisibility(View.VISIBLE);
        }
    }

    private void avisarCambioDestino() {
        if (onDestinoCambiado != null) {
            onDestinoCambiado.run();
        }
    }

    public boolean navigateUp() {
        if (navController == null) {
            return false;
        }

        return navController.navigateUp();
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