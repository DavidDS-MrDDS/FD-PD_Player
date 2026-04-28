package es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ListaEntity.class, CancionEnListaEntity.class}, version = 1, exportSchema = false)
public abstract class ListasDatabase extends RoomDatabase {

    public abstract ListasDao listasDao();

    private static volatile ListasDatabase instance;

    public static ListasDatabase getInstance(final Context context) {
        if (instance == null) {
            synchronized (ListasDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ListasDatabase.class,
                            "pdplayer.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return instance;
    }
}
