package es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "session")
public class SessionEntity {

    @PrimaryKey
    public int sessionId = 1;

    @NonNull
    public String token;

    public int userId;

    @NonNull
    public String username;

    @NonNull
    public String email;

    public SessionEntity(@NonNull String token, int userId, @NonNull String username, @NonNull String email) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.email = email;
    }
}