package es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveSession(SessionEntity session);

    @Query("SELECT * FROM session WHERE sessionId = 1 LIMIT 1")
    LiveData<SessionEntity> getSessionLive();

    @Query("SELECT * FROM session WHERE sessionId = 1 LIMIT 1")
    SessionEntity getSession();

    @Query("DELETE FROM session")
    void clearSession();
}