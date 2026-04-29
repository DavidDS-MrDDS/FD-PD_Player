package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;

public class UploadUrlResponse {

    private String signedUrl;
    private String token;
    private String path;
    private String publicUrl;

    public String getSignedUrl() {
        return signedUrl;
    }

    public String getToken() {
        return token;
    }

    public String getPath() {
        return path;
    }

    public String getPublicUrl() {
        return publicUrl;
    }
}