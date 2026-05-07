package es.iesagora.fd_pdplayer.funcionamiento.models;

import java.io.Serializable;

public class Cancion implements Serializable {
    //atributos
    private String Nombre;
    private String Artista;
    private String Album;
    private String RutaArchivo;

    //constructor
    public Cancion(String nombre, String artista, String album, String rutaArchivo) {
        Nombre = nombre;
        Artista = artista;
        Album = album;
        RutaArchivo = rutaArchivo;
    }

    //getters
    public String getNombre() {return Nombre;}
    public String getArtista() {return Artista;}
    public String getAlbum() {return Album;}
    public String getRutaArchivo() { return RutaArchivo; }
}