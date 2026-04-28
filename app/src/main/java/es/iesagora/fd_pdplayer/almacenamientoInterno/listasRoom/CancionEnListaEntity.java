package es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cancion_en_lista",
        foreignKeys = @ForeignKey(
                entity = ListaEntity.class,
                parentColumns = "id",
                childColumns = "listaId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("listaId"), @Index(value = {"listaId","rutaArchivo"}, unique = true)}
)
public class CancionEnListaEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int listaId;
    private String nombre;
    private String artista;
    private String album;
    private String rutaArchivo;

    public CancionEnListaEntity(int listaId, String nombre, String artista, String album, String rutaArchivo) {
        this.listaId = listaId;
        this.nombre = nombre;
        this.artista = artista;
        this.album = album;
        this.rutaArchivo = rutaArchivo;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getListaId() { return listaId; }
    public void setListaId(int listaId) { this.listaId = listaId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getArtista() { return artista; }
    public void setArtista(String artista) { this.artista = artista; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }
}
