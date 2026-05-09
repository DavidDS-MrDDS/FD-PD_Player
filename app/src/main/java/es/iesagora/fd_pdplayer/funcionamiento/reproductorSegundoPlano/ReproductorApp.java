package es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class ReproductorApp {

    public interface Listener {
        void onReproductorActualizado(
                Cancion cancionActual,
                ArrayList<Cancion> listaCanciones,
                int posicionLista,
                boolean preparada,
                boolean reproduciendo,
                int progresoMs,
                int duracionMs,
                int modoReproduccion,
                boolean modoLista
        );
    }

    public static final int MODO_NORMAL = 0;
    public static final int MODO_REPETIR_UNA = 1;
    public static final int MODO_MIXTA = 2;

    private static ReproductorApp instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private Context appContext;

    private MediaPlayer mediaPlayer;
    private boolean preparada = false;
    private boolean preparando = false;

    private Cancion cancionActual;
    private ArrayList<Cancion> listaCanciones = new ArrayList<>();
    private int posicionLista = 0;

    private int modoReproduccion = MODO_NORMAL;
    private boolean modoLista = false;

    private final Runnable actualizador = new Runnable() {
        @Override
        public void run() {
            notificar();

            if (mediaPlayer != null && preparada) {
                handler.postDelayed(this, 500);
            }
        }
    };

    private ReproductorApp() {
    }

    public static ReproductorApp getInstance() {
        if (instance == null) {
            instance = new ReproductorApp();
        }

        return instance;
    }

    public void addListener(Listener listener) {
        if (listener == null) return;

        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }

        notificarUno(listener);
    }

    public void removeListener(Listener listener) {
        if (listener == null) return;
        listeners.remove(listener);
    }

    public void reproducir(Context context,
                           Cancion nuevaCancion,
                           ArrayList<Cancion> nuevaLista,
                           int nuevaPosicion,
                           boolean reproducirComoLista) {
        if (context == null || nuevaCancion == null) return;

        appContext = context.getApplicationContext();

        ArrayList<Cancion> listaPreparada = nuevaLista != null
                ? new ArrayList<>(nuevaLista)
                : new ArrayList<>();

        if (listaPreparada.isEmpty()) {
            listaPreparada.add(nuevaCancion);
        }

        if (nuevaPosicion < 0 || nuevaPosicion >= listaPreparada.size()) {
            nuevaPosicion = buscarPosicionPorRuta(listaPreparada, nuevaCancion.getRutaArchivo());
        }

        if (nuevaPosicion < 0 || nuevaPosicion >= listaPreparada.size()) {
            nuevaPosicion = 0;
        }

        boolean mismaCancion = cancionActual != null
                && !TextUtils.isEmpty(cancionActual.getRutaArchivo())
                && cancionActual.getRutaArchivo().equals(nuevaCancion.getRutaArchivo())
                && mediaPlayer != null;

        listaCanciones = listaPreparada;
        posicionLista = nuevaPosicion;
        cancionActual = listaCanciones.get(posicionLista);
        modoLista = reproducirComoLista;

        if (mismaCancion) {
            notificar();

            if (estaReproduciendo()) {
                iniciarActualizador();
            }

            return;
        }

        prepararCancionActual();
    }

    private void prepararCancionActual() {
        liberarMediaPlayer(false);

        if (cancionActual == null || TextUtils.isEmpty(cancionActual.getRutaArchivo())) {
            preparada = false;
            preparando = false;
            notificar();
            return;
        }

        preparada = false;
        preparando = true;
        notificar();

        MediaPlayer nuevoPlayer = new MediaPlayer();
        mediaPlayer = nuevoPlayer;

        try {
            nuevoPlayer.setDataSource(cancionActual.getRutaArchivo());

            nuevoPlayer.setOnPreparedListener(mp -> {
                if (mediaPlayer != mp) return;

                preparada = true;
                preparando = false;

                mp.start();
                notificar();
                iniciarActualizador();
            });

            nuevoPlayer.setOnCompletionListener(mp -> alTerminarCancion());

            nuevoPlayer.prepareAsync();

        } catch (IOException | IllegalStateException e) {
            liberarMediaPlayer(false);
            preparada = false;
            preparando = false;
            notificar();
        }
    }

    public void toggle() {
        if (mediaPlayer == null || !preparada) return;

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                mediaPlayer.start();
                iniciarActualizador();
            }

            notificar();

        } catch (IllegalStateException ignored) {
        }
    }

    public void irSiguiente() {
        if (listaCanciones == null || listaCanciones.isEmpty()) return;

        if (modoReproduccion == MODO_MIXTA) {
            posicionLista = obtenerPosicionAleatoria();
        } else {
            posicionLista++;

            if (posicionLista >= listaCanciones.size()) {
                posicionLista = 0;
            }
        }

        cancionActual = listaCanciones.get(posicionLista);
        prepararCancionActual();
    }

    public void irAnterior() {
        if (listaCanciones == null || listaCanciones.isEmpty()) return;

        if (modoReproduccion == MODO_MIXTA) {
            posicionLista = obtenerPosicionAleatoria();
        } else {
            posicionLista--;

            if (posicionLista < 0) {
                posicionLista = listaCanciones.size() - 1;
            }
        }

        cancionActual = listaCanciones.get(posicionLista);
        prepararCancionActual();
    }

    public void seekTo(int posicionMs) {
        if (mediaPlayer == null || !preparada) return;

        try {
            mediaPlayer.seekTo(posicionMs);
            notificar();
        } catch (IllegalStateException ignored) {
        }
    }

    public void cambiarModoReproduccion() {
        modoReproduccion++;

        if (modoReproduccion > MODO_MIXTA) {
            modoReproduccion = MODO_NORMAL;
        }

        notificar();
    }

    private void alTerminarCancion() {
        if (modoReproduccion == MODO_REPETIR_UNA) {
            if (mediaPlayer != null && preparada) {
                try {
                    mediaPlayer.seekTo(0);
                    mediaPlayer.start();
                    iniciarActualizador();
                    notificar();
                } catch (IllegalStateException ignored) {
                }
            }

            return;
        }

        irSiguiente();
    }

    private int obtenerPosicionAleatoria() {
        if (listaCanciones == null || listaCanciones.isEmpty()) return 0;

        if (listaCanciones.size() == 1) {
            return 0;
        }

        int nuevaPosicion;

        do {
            nuevaPosicion = random.nextInt(listaCanciones.size());
        } while (nuevaPosicion == posicionLista);

        return nuevaPosicion;
    }

    private int buscarPosicionPorRuta(ArrayList<Cancion> lista, String ruta) {
        if (lista == null || TextUtils.isEmpty(ruta)) return -1;

        for (int i = 0; i < lista.size(); i++) {
            Cancion c = lista.get(i);

            if (c != null && ruta.equals(c.getRutaArchivo())) {
                return i;
            }
        }

        return -1;
    }

    private void iniciarActualizador() {
        handler.removeCallbacks(actualizador);
        handler.post(actualizador);
    }

    public void liberar() {
        liberarMediaPlayer(true);
        notificar();
    }

    private void liberarMediaPlayer(boolean limpiarCancion) {
        handler.removeCallbacks(actualizador);

        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }

            mediaPlayer = null;
        }

        preparada = false;
        preparando = false;

        if (limpiarCancion) {
            cancionActual = null;
            listaCanciones = new ArrayList<>();
            posicionLista = 0;
            modoLista = false;
        }
    }

    private void notificar() {
        for (Listener listener : listeners) {
            notificarUno(listener);
        }
    }

    private void notificarUno(Listener listener) {
        if (listener == null) return;

        listener.onReproductorActualizado(
                cancionActual,
                new ArrayList<>(listaCanciones),
                posicionLista,
                preparada,
                estaReproduciendo(),
                obtenerProgreso(),
                obtenerDuracion(),
                modoReproduccion,
                modoLista
        );
    }

    private boolean estaReproduciendo() {
        if (mediaPlayer == null || !preparada) return false;

        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private int obtenerProgreso() {
        if (mediaPlayer == null || !preparada) return 0;

        try {
            return mediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    private int obtenerDuracion() {
        if (mediaPlayer == null || !preparada) return 0;

        try {
            return mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public Cancion getCancionActual() {
        return cancionActual;
    }

    public ArrayList<Cancion> getListaCanciones() {
        return new ArrayList<>(listaCanciones);
    }

    public int getPosicionLista() {
        return posicionLista;
    }

    public int getModoReproduccion() {
        return modoReproduccion;
    }

    public boolean isModoLista() {
        return modoLista;
    }

    public boolean isPreparando() {
        return preparando;
    }
}