package es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReproductorNotificacionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;

        ReproductorApp reproductorApp = ReproductorApp.getInstance();

        String action = intent.getAction();

        if (ReproductorNotificacion.ACTION_ANTERIOR.equals(action)) {
            reproductorApp.irAnterior();
            return;
        }

        if (ReproductorNotificacion.ACTION_PLAY_PAUSE.equals(action)) {
            reproductorApp.toggle();
            return;
        }

        if (ReproductorNotificacion.ACTION_SIGUIENTE.equals(action)) {
            reproductorApp.irSiguiente();
            return;
        }

        if (ReproductorNotificacion.ACTION_DETENER.equals(action)) {
            reproductorApp.liberar();
            ReproductorNotificacion.cancelarNotificacion(context);
        }
    }
}