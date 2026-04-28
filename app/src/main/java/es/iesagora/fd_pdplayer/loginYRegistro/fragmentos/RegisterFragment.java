package es.iesagora.fd_pdplayer.loginYRegistro.fragmentos;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
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
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthState;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.AuthViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentRegisterBinding;

public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private AuthViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        observarEstadoAutenticacion();

        binding.btnVolver.setOnClickListener(v -> requireActivity().finish());

        binding.btnCrearCuenta.setOnClickListener(v -> {
            String usuario = binding.eTUsuario.getText() != null
                    ? binding.eTUsuario.getText().toString().trim()
                    : "";

            String correo = binding.eTEmail.getText() != null
                    ? binding.eTEmail.getText().toString().trim()
                    : "";

            String password = binding.tVContrasenia.getText() != null
                    ? binding.tVContrasenia.getText().toString()
                    : "";

            if (!validarCampos(usuario, correo, password)) {
                return;
            }

            viewModel.register(usuario, correo, password);
        });

        binding.tvIrLogin.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_registerFragment_to_loginFragment)
        );
    }

    private void observarEstadoAutenticacion() {
        viewModel.getAuthState().observe(getViewLifecycleOwner(), this::procesarEstado);
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

            Navigation.findNavController(binding.getRoot())
                    .navigate(R.id.action_registerFragment_to_loginFragment);
        }
    }

    private boolean validarCampos(String usuario, String correo, String password) {
        if (TextUtils.isEmpty(usuario)) {
            Toast.makeText(getContext(), "Rellena el usuario", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (TextUtils.isEmpty(correo)) {
            Toast.makeText(getContext(), "Rellena el correo", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            Toast.makeText(getContext(), "Correo no válido", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            Toast.makeText(getContext(), "Rellena la contraseña", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (password.length() < 6) {
            Toast.makeText(getContext(), "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;

        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnCrearCuenta.setEnabled(!loading);
        binding.btnVolver.setEnabled(!loading);
        binding.eTUsuario.setEnabled(!loading);
        binding.eTEmail.setEnabled(!loading);
        binding.tVContrasenia.setEnabled(!loading);
        binding.tvIrLogin.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}