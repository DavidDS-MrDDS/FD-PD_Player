package es.iesagora.fd_pdplayer.almacenamientoInterno.favoritosRoom;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {FavoritoLocalEntity.class}, version = 2, exportSchema = false)
public abstract class FavoritosDatabase extends RoomDatabase {

    public abstract FavoritosDao favoritosDao();

    private static volatile FavoritosDatabase INSTANCE;

    public static FavoritosDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (FavoritosDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    FavoritosDatabase.class,
                                    "favoritos_local.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}