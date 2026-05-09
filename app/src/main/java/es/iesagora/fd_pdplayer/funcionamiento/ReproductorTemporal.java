package es.iesagora.fd_pdplayer.funcionamiento;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;

public class ReproductorTemporal {

    public interface Listener {
        void onTemporizadorActualizado(boolean activo, String tiempoRestante);
    }

    private static ReproductorTemporal instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private boolean temporizadorActivo = false;
    private long tiempoFinMs = 0;

    private final Runnable runnableTemporizador = new Runnable() {
        @Override
        public void run() {
            if (!temporizadorActivo) {
                notificar();
                return;
            }

            long restanteMs = tiempoFinMs - System.currentTimeMillis();

            if (restanteMs <= 0) {
                ReproductorApp.getInstance().liberar();

                temporizadorActivo = false;
                tiempoFinMs = 0;

                notificar();
                return;
            }

            notificar();
            handler.postDelayed(this, 1000);
        }
    };

    private ReproductorTemporal() {
    }

    public static ReproductorTemporal getInstance() {
        if (instance == null) {
            instance = new ReproductorTemporal();
        }

        return instance;
    }

    public void addListener(Listener listener) {
        if (listener == null) return;

        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }

        listener.onTemporizadorActualizado(
                temporizadorActivo,
                getTiempoRestanteFormateado()
        );
    }

    public void removeListener(Listener listener) {
        if (listener == null) return;
        listeners.remove(listener);
    }

    public void programarApagado(int minutos) {
        cancelarSinNotificar();

        if (minutos <= 0) {
            notificar();
            return;
        }

        temporizadorActivo = true;
        tiempoFinMs = System.currentTimeMillis() + minutos * 60L * 1000L;

        notificar();

        handler.removeCallbacks(runnableTemporizador);
        handler.post(runnableTemporizador);
    }

    public void cancelar() {
        cancelarSinNotificar();
        notificar();
    }

    private void cancelarSinNotificar() {
        handler.removeCallbacks(runnableTemporizador);

        temporizadorActivo = false;
        tiempoFinMs = 0;
    }

    public boolean estaActivo() {
        return temporizadorActivo;
    }

    public String getTiempoRestanteFormateado() {
        if (!temporizadorActivo || tiempoFinMs <= 0) {
            return "";
        }

        long restanteMs = tiempoFinMs - System.currentTimeMillis();

        if (restanteMs <= 0) {
            return "";
        }

        long segundosTotales = restanteMs / 1000;

        long minutos = segundosTotales / 60;
        long segundos = segundosTotales % 60;

        return minutos + ":" + String.format("%02d", segundos);
    }

    private void notificar() {
        String tiempo = getTiempoRestanteFormateado();

        for (Listener listener : listeners) {
            listener.onTemporizadorActualizado(temporizadorActivo, tiempo);
        }
    }
}