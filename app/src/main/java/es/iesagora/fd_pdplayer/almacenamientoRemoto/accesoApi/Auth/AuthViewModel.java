package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth;

import android.app.Application;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONObject;

import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionRepository;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;

public class AuthViewModel extends AndroidViewModel {

    private final AuthApiService authApiService;
    private final SessionRepository sessionRepository;
    private final FavoriteUploadRepository favoriteUploadRepository;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();
    private final LiveData<SessionEntity> currentSession;

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authApiService = new AuthApiService(application);
        sessionRepository = new SessionRepository(application);
        favoriteUploadRepository = new FavoriteUploadRepository(application);
        currentSession = sessionRepository.getSessionLive();
    }

    public MutableLiveData<AuthState> getAuthState() {
        return authState;
    }

    public LiveData<SessionEntity> getCurrentSession() {
        return currentSession;
    }

    public void logout() {
        sessionRepository.clearSession();
    }

    public void login(String correo, String password) {
        String validationError = validateLogin(correo, password);
        if (validationError != null) {
            authState.setValue(AuthState.error(validationError));
            return;
        }

        authState.setValue(AuthState.loading());

        authApiService.login(correo.trim(), password, new AuthApiService.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    String token = response.optString("token", "");
                    JSONObject userJson = response.optJSONObject("user");

                    if (token.isEmpty() || userJson == null) {
                        authState.postValue(AuthState.error("Respuesta inválida del servidor."));
                        return;
                    }

                    ApiUser user = new ApiUser(
                            userJson.optInt("id", -1),
                            userJson.optString("username", ""),
                            userJson.optString("email", "")
                    );

                    sessionRepository.saveSession(token, user);
                    favoriteUploadRepository.sincronizarFavoritosDelUsuario(token, user.getUsername());

                    String message = response.optString("message", "Inicio de sesión correcto.");
                    authState.postValue(AuthState.loginSuccess(user, token, message));

                } catch (Exception e) {
                    authState.postValue(AuthState.error("Error procesando la respuesta del login."));
                }
            }

            @Override
            public void onError(String message) {
                authState.postValue(AuthState.error(message));
            }
        });
    }

    public void register(String usuario, String correo, String password) {
        String validationError = validateRegister(usuario, correo, password);
        if (validationError != null) {
            authState.setValue(AuthState.error(validationError));
            return;
        }

        authState.setValue(AuthState.loading());

        authApiService.register(usuario.trim(), correo.trim(), password, new AuthApiService.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                String message = response.optString("message", "Registro correcto.");
                authState.postValue(AuthState.message(message));
            }

            @Override
            public void onError(String message) {
                authState.postValue(AuthState.error(message));
            }
        });
    }

    private String validateLogin(String correo, String password) {
        if (correo == null || correo.trim().isEmpty()) {
            return "El correo es obligatorio.";
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()) {
            return "El correo no tiene un formato válido.";
        }

        if (password == null || password.isEmpty()) {
            return "La contraseña es obligatoria.";
        }

        return null;
    }

    private String validateRegister(String usuario, String correo, String password) {
        if (usuario == null || usuario.trim().isEmpty()) {
            return "El nombre de usuario es obligatorio.";
        }

        if (correo == null || correo.trim().isEmpty()) {
            return "El correo es obligatorio.";
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()) {
            return "El correo no tiene un formato válido.";
        }

        if (TextUtils.isEmpty(password)) {
            return "La contraseña es obligatoria.";
        }

        if (password.length() < 6) {
            return "La contraseña debe tener al menos 6 caracteres.";
        }

        return null;
    }
}