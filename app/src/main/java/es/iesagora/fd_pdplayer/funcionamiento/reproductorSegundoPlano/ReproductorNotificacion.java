package es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.LruCache;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.MainActivity;
import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.ImagenCancionUtils;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class ReproductorNotificacion implements ReproductorApp.Listener {

    public static final String CHANNEL_ID = "canal_reproductor";
    public static final int NOTIFICATION_ID = 1201;

    public static final String ACTION_ANTERIOR = "es.iesagora.fd_pdplayer.ACCION_ANTERIOR";
    public static final String ACTION_PLAY_PAUSE = "es.iesagora.fd_pdplayer.ACCION_PLAY_PAUSE";
    public static final String ACTION_SIGUIENTE = "es.iesagora.fd_pdplayer.ACCION_SIGUIENTE";
    public static final String ACTION_DETENER = "es.iesagora.fd_pdplayer.ACCION_DETENER";

    private final Context context;
    private final NotificationManagerCompat notificationManager;
    private final MediaSessionCompat mediaSession;

    private final ExecutorService imagenExecutor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> imagenCache;

    private String ultimaRuta = "";
    private boolean ultimoEstadoReproduciendo = false;
    private long ultimaActualizacionProgreso = 0;

    private String rutaImagenCargando = "";
    private int versionCargaImagen = 0;

    public ReproductorNotificacion(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = NotificationManagerCompat.from(this.context);
        this.imagenCache = ImagenCancionUtils.crearCacheImagenes(24);
        this.mediaSession = new MediaSessionCompat(this.context, "FD_PDPlayer");

        this.mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                ReproductorApp.getInstance().toggle();
            }

            @Override
            public void onPause() {
                ReproductorApp.getInstance().toggle();
            }

            @Override
            public void onSkipToPrevious() {
                ReproductorApp.getInstance().irAnterior();
            }

            @Override
            public void onSkipToNext() {
                ReproductorApp.getInstance().irSiguiente();
            }

            @Override
            public void onStop() {
                ReproductorApp.getInstance().liberar();
                cancelarNotificacion(ReproductorNotificacion.this.context);
            }

            @Override
            public void onSeekTo(long posicionMs) {
                ReproductorApp.getInstance().seekTo((int) posicionMs);
            }
        });

        this.mediaSession.setActive(true);
        crearCanal();
    }

    public void crearCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Reproductor",
                NotificationManager.IMPORTANCE_LOW
        );

        channel.setDescription("Controles de reproducción de música");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);

        NotificationManager manager = context.getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onReproductorActualizado(
            Cancion cancionActual,
            ArrayList<Cancion> listaCanciones,
            int posicionLista,
            boolean preparada,
            boolean reproduciendo,
            int progresoMs,
            int duracionMs,
            int modoReproduccion,
            boolean modoLista
    ) {
        if (cancionActual == null) {
            cancelar();
            ultimaRuta = "";
            rutaImagenCargando = "";
            versionCargaImagen++;
            actualizarPlaybackState(false, 0);
            return;
        }

        long ahora = System.currentTimeMillis();

        String rutaActual = safe(cancionActual.getRutaArchivo());
        boolean cambioCancion = !rutaActual.equals(ultimaRuta);
        boolean cambioEstado = reproduciendo != ultimoEstadoReproduciendo;
        boolean tiempoActualizar = ahora - ultimaActualizacionProgreso >= 1000;

        if (!cambioCancion && !cambioEstado && !tiempoActualizar) {
            return;
        }

        ultimaRuta = rutaActual;
        ultimoEstadoReproduciendo = reproduciendo;
        ultimaActualizacionProgreso = ahora;

        actualizarMetadata(cancionActual, preparada, progresoMs, duracionMs);
        actualizarPlaybackState(reproduciendo, progresoMs);

        mostrarNotificacion(
                cancionActual,
                preparada,
                reproduciendo,
                progresoMs,
                duracionMs
        );
    }

    private void actualizarMetadata(Cancion cancion,
                                    boolean preparada,
                                    int progresoMs,
                                    int duracionMs) {
        if (cancion == null) return;

        String textoSecundario = crearTextoSecundario(
                cancion,
                preparada,
                progresoMs,
                duracionMs
        );

        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, safe(cancion.getNombre()))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, textoSecundario)
                .putLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION,
                        duracionMs > 0 ? duracionMs : -1
                )
                .build());
    }

    private void actualizarPlaybackState(boolean reproduciendo, int progresoMs) {
        long acciones = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SEEK_TO;

        float velocidad = reproduciendo ? 1.0f : 0.0f;

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(acciones)
                .setState(
                        reproduciendo
                                ? PlaybackStateCompat.STATE_PLAYING
                                : PlaybackStateCompat.STATE_PAUSED,
                        Math.max(0, progresoMs),
                        velocidad
                )
                .build());
    }

    private void mostrarNotificacion(
            Cancion cancion,
            boolean preparada,
            boolean reproduciendo,
            int progresoMs,
            int duracionMs
    ) {
        if (!tienePermisoNotificaciones()) return;

        NotificationCompat.Action accionAnterior = new NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Anterior",
                crearBroadcast(ACTION_ANTERIOR, 201)
        );

        NotificationCompat.Action accionPlayPause = new NotificationCompat.Action(
                reproduciendo ? R.drawable.ic_player_pause : R.drawable.ic_player_play,
                reproduciendo ? "Pausar" : "Reproducir",
                crearBroadcast(ACTION_PLAY_PAUSE, 202)
        );

        NotificationCompat.Action accionSiguiente = new NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Siguiente",
                crearBroadcast(ACTION_SIGUIENTE, 203)
        );

        NotificationCompat.Action accionCerrar = new NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cerrar reproductor",
                crearBroadcast(ACTION_DETENER, 204)
        );

        Bitmap caratula = obtenerImagenCacheada(
                cancion,
                preparada,
                reproduciendo,
                progresoMs,
                duracionMs
        );

        String textoSecundario = crearTextoSecundario(
                cancion,
                preparada,
                progresoMs,
                duracionMs
        );

        MediaStyle estilo = new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2, 3);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(safe(cancion.getNombre()))
                .setContentText(textoSecundario)
                .setLargeIcon(caratula)
                .addAction(accionAnterior)
                .addAction(accionPlayPause)
                .addAction(accionSiguiente)
                .addAction(accionCerrar)
                .setStyle(estilo)
                .setContentIntent(abrirApp())
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private Bitmap obtenerImagenCacheada(
            Cancion cancion,
            boolean preparada,
            boolean reproduciendo,
            int progresoMs,
            int duracionMs
    ) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) return null;

        String ruta = safe(cancion.getRutaArchivo());

        Bitmap cacheada = imagenCache.get(ruta);

        if (cacheada != null) {
            return cacheada;
        }

        cargarImagenAsync(
                cancion,
                preparada,
                reproduciendo,
                progresoMs,
                duracionMs
        );

        return null;
    }

    private void cargarImagenAsync(
            Cancion cancion,
            boolean preparada,
            boolean reproduciendo,
            int progresoMs,
            int duracionMs
    ) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) return;

        String ruta = safe(cancion.getRutaArchivo());

        if (ruta.equals(rutaImagenCargando)) {
            return;
        }

        rutaImagenCargando = ruta;

        final int versionActual = ++versionCargaImagen;

        imagenExecutor.execute(() -> {
            Bitmap bitmap = ImagenCancionUtils.obtenerImagenDesdeArchivoRedondeada(
                    ruta,
                    dp(96),
                    dp(96),
                    dp(12)
            );

            if (bitmap != null) {
                imagenCache.put(ruta, bitmap);
            }

            rutaImagenCargando = "";

            if (versionActual != versionCargaImagen) {
                return;
            }

            if (!ruta.equals(ultimaRuta)) {
                return;
            }

            mostrarNotificacion(
                    cancion,
                    preparada,
                    reproduciendo,
                    progresoMs,
                    duracionMs
            );
        });
    }

    private PendingIntent crearBroadcast(String action, int requestCode) {
        Intent intent = new Intent(context, ReproductorNotificacionReceiver.class);
        intent.setAction(action);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                flags
        );
    }

    private PendingIntent abrirApp() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getActivity(
                context,
                300,
                intent,
                flags
        );
    }

    public void cancelar() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    public static void cancelarNotificacion(Context context) {
        if (context == null) return;

        NotificationManagerCompat.from(context.getApplicationContext())
                .cancel(NOTIFICATION_ID);
    }

    public void liberar() {
        cancelar();
        mediaSession.setActive(false);
        mediaSession.release();
        imagenExecutor.shutdownNow();
        imagenCache.evictAll();
    }

    private String crearTextoSecundario(Cancion cancion,
                                        boolean preparada,
                                        int progresoMs,
                                        int duracionMs) {
        String artista = safe(cancion != null ? cancion.getArtista() : "");

        if (TextUtils.isEmpty(artista)) {
            artista = "Artista desconocido";
        }

        if (!preparada || duracionMs <= 0) {
            return artista;
        }

        int duracionSegura = Math.max(1, duracionMs);
        int progresoSeguro = Math.max(0, Math.min(progresoMs, duracionSegura));

        return artista + " • "
                + formatearTiempo(progresoSeguro)
                + " / "
                + formatearTiempo(duracionSegura);
    }

    private String formatearTiempo(int ms) {
        int segundos = Math.max(0, ms / 1000);
        return (segundos / 60) + ":" + String.format("%02d", segundos % 60);
    }

    private boolean tienePermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return ImagenCancionUtils.dp(context, value);
    }
}