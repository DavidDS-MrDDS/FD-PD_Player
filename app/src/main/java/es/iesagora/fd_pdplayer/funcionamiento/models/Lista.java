package es.iesagora.fd_pdplayer.funcionamiento.models;

import java.util.List;

public class Lista {
    private int id;
    private String nombre;
    private List<Cancion> Canciones;

    public Lista(int id, String nombre, List<Cancion> canciones) {
        this.id = id;
        this.nombre = nombre;
        Canciones = canciones;
    }

    public int getId() { return id; }

    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }

    public void setNombre(String nombre) { this.nombre = nombre; }

    public List<Cancion> getCanciones() { return Canciones; }

    public void setCanciones(List<Cancion> canciones) { Canciones = canciones; }
}
