package es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "lista")
public class ListaEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String nombre;

    public ListaEntity(String nombre) {
        this.nombre = nombre;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
