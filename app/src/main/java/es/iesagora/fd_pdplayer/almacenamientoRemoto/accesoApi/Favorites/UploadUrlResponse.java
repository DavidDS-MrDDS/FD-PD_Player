package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites;

public class UploadUrlResponse {

    private String signedUrl;

    public UploadUrlResponse(String signedUrl) {
        this.signedUrl = signedUrl;
    }

    public String getSignedUrl() {
        return signedUrl;
    }
}