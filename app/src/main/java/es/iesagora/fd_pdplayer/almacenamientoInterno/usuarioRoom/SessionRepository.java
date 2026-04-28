package es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth.ApiUser;

public class SessionRepository {

    private final SessionDao sessionDao;
    private final LiveData<SessionEntity> sessionLive;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public SessionRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        sessionDao = db.sessionDao();
        sessionLive = sessionDao.getSessionLive();
    }

    public LiveData<SessionEntity> getSessionLive() {
        return sessionLive;
    }

    public void saveSession(String token, ApiUser user) {
        executorService.execute(() -> {
            SessionEntity session = new SessionEntity(
                    token,
                    user.getId(),
                    user.getUsername() != null ? user.getUsername() : "",
                    user.getEmail() != null ? user.getEmail() : ""
            );
            sessionDao.saveSession(session);
        });
    }

    public SessionEntity getSessionSync() {
        Future<SessionEntity> future = executorService.submit((Callable<SessionEntity>) sessionDao::getSession);
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            return null;
        }
    }

    public void clearSession() {
        executorService.execute(sessionDao::clearSession);
    }
}