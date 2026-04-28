package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentAjustesBinding;
import es.iesagora.fd_pdplayer.loginYRegistro.AuthActivity;

public class AjustesFragment extends Fragment {

    private FragmentAjustesBinding binding;
    private AuthViewModel authViewModel;

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

        binding.rowLogin.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AuthActivity.class);
            startActivity(intent);
        });

        binding.rowBackup.setOnClickListener(v -> {
            // TODO: Aquí puedes añadir la función de copia de seguridad
        });

        binding.rowOpcion2.setOnClickListener(v -> {
            // TODO: Acción para opción 2
        });

        binding.rowOpcion3.setOnClickListener(v -> {
            // TODO: Acción para opción 3
        });

        binding.rowOpcion4.setOnClickListener(v -> {
            // TODO: Acción para opción 4
        });

        binding.rowOpcion5.setOnClickListener(v -> {
            // TODO: Acción para opción 5
        });

        binding.rowOpcion6.setOnClickListener(v -> {
            // TODO: Acción para opción 6
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}