package es.iesagora.fd_pdplayer.funcionamiento.controlBusqueda;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

public class OptimizarBuscador implements TextWatcher {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long delayMs;
    private final Runnable accion;

    private Runnable accionPendiente;

    public OptimizarBuscador(long delayMs, Runnable accion) {
        this.delayMs = delayMs;
        this.accion = accion;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        cancelar();

        accionPendiente = () -> {
            if (accion != null) {
                accion.run();
            }
        };

        handler.postDelayed(accionPendiente, delayMs);
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    public void cancelar() {
        if (accionPendiente != null) {
            handler.removeCallbacks(accionPendiente);
            accionPendiente = null;
        }
    }
}