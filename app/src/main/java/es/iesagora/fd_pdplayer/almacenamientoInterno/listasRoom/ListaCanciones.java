package es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class ListaCanciones {

    @Embedded
    public ListaEntity lista;

    @Relation(
            parentColumn = "id",
            entityColumn = "listaId"
    )
    public List<CancionEnListaEntity> canciones;
}
