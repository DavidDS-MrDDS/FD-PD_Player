package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentAjustesBinding;
import es.iesagora.fd_pdplayer.funcionamiento.ReproductorTemporal;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.loginYRegistro.AuthActivity;

public class AjustesFragment extends Fragment implements ReproductorTemporal.Listener {

    private FragmentAjustesBinding binding;
    private AuthViewModel authViewModel;

    private final ReproductorTemporal reproductorTemporal = ReproductorTemporal.getInstance();

    private static final String TEXTO_REPRODUCCION_TEMPORAL = "Reproducción temporal";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAjustesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        observarSesion();

        reproductorTemporal.addListener(this);

        binding.rowLogin.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AuthActivity.class);
            startActivity(intent);
        });

        binding.rowOrganizarAlmacenamiento.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.organizarAlmacenamientoFragment);
        });

        binding.rowCancionesOcultas.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.cancionesOcultasFragment);
        });

        binding.rowReproduccionTemporal.setOnClickListener(v -> mostrarMenuReproduccionTemporal());

        binding.rowContacto.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.contactoFragment);
        });
    }

    private void observarSesion() {
        authViewModel.getCurrentSession().observe(getViewLifecycleOwner(), this::actualizarBotonSesion);
    }

    private void actualizarBotonSesion(SessionEntity session) {
        if (binding == null) return;

        if (session != null) {
            binding.tvLoginTitle.setText("Modificar Sesión");
        } else {
            binding.tvLoginTitle.setText("Iniciar Sesión");
        }
    }

    private void mostrarMenuReproduccionTemporal() {
        String mensaje;

        if (reproductorTemporal.estaActivo()) {
            mensaje = "Temporizador activo. Tiempo restante: "
                    + reproductorTemporal.getTiempoRestanteFormateado();
        } else {
            mensaje = "Elige cuándo quieres que se detenga la reproducción.";
        }

        String[] opciones = {
                "Apagar temporizador",
                "10 minutos",
                "15 minutos",
                "30 minutos",
                "60 minutos"
        };

        VentanasApp.mostrarMenu(
                requireContext(),
                "ReproduccionTemporal",
                "Reproducción temporal",
                mensaje,
                opciones,
                (posicion, texto) -> {
                    if (binding == null) return;

                    if (posicion == 0) {
                        reproductorTemporal.cancelar();

                        VentanasApp.mostrarMensaje(
                                binding.getRoot(),
                                "Temporizador apagado"
                        );
                    } else if (posicion == 1) {
                        activarTemporizador(10);
                    } else if (posicion == 2) {
                        activarTemporizador(15);
                    } else if (posicion == 3) {
                        activarTemporizador(30);
                    } else if (posicion == 4) {
                        activarTemporizador(60);
                    }
                }
        );
    }

    private void activarTemporizador(int minutos) {
        reproductorTemporal.programarApagado(minutos);

        VentanasApp.mostrarMensaje(
                binding.getRoot(),
                "La música se detendrá después de " + minutos + " minutos"
        );
    }

    @Override
    public void onTemporizadorActualizado(boolean activo, String tiempoRestante) {
        if (binding == null) return;

        if (activo && tiempoRestante != null && !tiempoRestante.isEmpty()) {
            binding.tvReproduccionTemporalTitle.setText(
                    TEXTO_REPRODUCCION_TEMPORAL + " [" + tiempoRestante + "]"
            );
        } else {
            binding.tvReproduccionTemporalTitle.setText(TEXTO_REPRODUCCION_TEMPORAL);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        reproductorTemporal.removeListener(this);

        binding = null;
    }
}