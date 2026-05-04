package es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CancionOcultaEntity.class}, version = 1, exportSchema = false)
public abstract class CancionesOcultasDatabase extends RoomDatabase {

    public abstract CancionesOcultasDao cancionesOcultasDao();

    private static volatile CancionesOcultasDatabase INSTANCE;

    public static CancionesOcultasDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CancionesOcultasDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    CancionesOcultasDatabase.class,
                                    "canciones_ocultas.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}