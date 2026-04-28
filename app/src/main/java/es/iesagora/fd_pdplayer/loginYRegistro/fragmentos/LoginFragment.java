package es.iesagora.fd_pdplayer.loginYRegistro.fragmentos;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthState;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentLoginBinding;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        observarEstadoAutenticacion();
        observarSesion();

        binding.btnVolver.setOnClickListener(v -> requireActivity().finish());

        binding.btnIniciarSesion.setOnClickListener(v -> {
            String correo = binding.eTCorreo.getText() != null
                    ? binding.eTCorreo.getText().toString().trim()
                    : "";

            String password = binding.tVContrasenia.getText() != null
                    ? binding.tVContrasenia.getText().toString()
                    : "";

            if (TextUtils.isEmpty(correo) || TextUtils.isEmpty(password)) {
                Toast.makeText(getContext(), "Rellena correo y contraseña", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.login(correo, password);
        });

        binding.btnCerrarSesion.setOnClickListener(v -> {
            viewModel.logout();
            Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show();
        });

        binding.tvIrRegistro.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_loginFragment_to_registerFragment)
        );
    }

    private void observarEstadoAutenticacion() {
        viewModel.getAuthState().observe(getViewLifecycleOwner(), this::procesarEstado);
    }

    private void observarSesion() {
        viewModel.getCurrentSession().observe(getViewLifecycleOwner(), this::actualizarVistaSesion);
    }

    private void actualizarVistaSesion(SessionEntity session) {
        boolean sesionIniciada = session != null;

        binding.tvTituloLogin.setVisibility(sesionIniciada ? View.GONE : View.VISIBLE);
        binding.layoutFormularioLogin.setVisibility(sesionIniciada ? View.GONE : View.VISIBLE);
        binding.layoutSesionIniciada.setVisibility(sesionIniciada ? View.VISIBLE : View.GONE);

        if (sesionIniciada) {
            binding.tvSesionInfo.setText(session.username);
        }
    }

    private void procesarEstado(AuthState state) {
        if (state == null) return;

        setLoading(state.loading);

        if (state.error != null && !state.error.isEmpty()) {
            Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show();
            return;
        }

        if (state.successMessage != null && !state.successMessage.isEmpty()) {
            Toast.makeText(requireContext(), state.successMessage, Toast.LENGTH_LONG).show();
        }

        if (state.user != null && state.token != null && !state.token.isEmpty()) {
            requireActivity().finish();
        }
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;

        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnIniciarSesion.setEnabled(!loading);
        binding.btnVolver.setEnabled(!loading);
        binding.eTCorreo.setEnabled(!loading);
        binding.tVContrasenia.setEnabled(!loading);
        binding.tvIrRegistro.setEnabled(!loading);
        binding.btnCerrarSesion.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}