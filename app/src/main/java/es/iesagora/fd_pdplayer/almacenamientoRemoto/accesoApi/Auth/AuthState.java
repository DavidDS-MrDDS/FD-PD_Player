package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth;

public class AuthState {

    public boolean loading;
    public ApiUser user;
    public String token;
    public String error;
    public String successMessage;

    public static AuthState loading() {
        AuthState state = new AuthState();
        state.loading = true;
        return state;
    }

    public static AuthState loginSuccess(ApiUser user, String token, String successMessage) {
        AuthState state = new AuthState();
        state.user = user;
        state.token = token;
        state.successMessage = successMessage;
        return state;
    }

    public static AuthState message(String successMessage) {
        AuthState state = new AuthState();
        state.successMessage = successMessage;
        return state;
    }

    public static AuthState error(String error) {
        AuthState state = new AuthState();
        state.error = error;
        return state;
    }
}