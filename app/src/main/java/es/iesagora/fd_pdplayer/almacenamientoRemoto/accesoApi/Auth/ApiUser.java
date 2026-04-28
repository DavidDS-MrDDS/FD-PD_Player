package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth;

public class ApiUser {

    private final int id;
    private final String username;
    private final String email;

    public ApiUser(int id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }
}