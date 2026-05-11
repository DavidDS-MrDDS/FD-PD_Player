package es.iesagora.fd_pdplayer.funcionamiento.modificacionCanciones;

import android.net.Uri;

public class ModificacionPendiente {

    public String rutaAntigua;
    public String rutaNueva;

    public Uri uriOriginal;
    public Uri uriDestino;

    public String nombre;
    public String artista;
    public String album;

    public boolean reintentarBorradoAndroid10;

    public ModificacionPendiente(String rutaAntigua,
                                 String rutaNueva,
                                 Uri uriOriginal,
                                 Uri uriDestino,
                                 String nombre,
                                 String artista,
                                 String album,
                                 boolean reintentarBorradoAndroid10) {
        this.rutaAntigua = rutaAntigua;
        this.rutaNueva = rutaNueva;
        this.uriOriginal = uriOriginal;
        this.uriDestino = uriDestino;
        this.nombre = nombre;
        this.artista = artista;
        this.album = album;
        this.reintentarBorradoAndroid10 = reintentarBorradoAndroid10;
    }
}