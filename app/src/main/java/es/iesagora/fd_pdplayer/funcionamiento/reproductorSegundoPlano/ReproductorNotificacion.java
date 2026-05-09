package es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.text.TextUtils;
import android.util.LruCache;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.MainActivity;
import es.iesagora.fd_pdplayer.R;
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

        int memoriaMaximaKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int tamanoCacheKb = memoriaMaximaKb / 24;

        imagenCache = new LruCache<String, Bitmap>(tamanoCacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };

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
    public void onReproductorActualizado(Cancion cancionActual,
                                         ArrayList<Cancion> listaCanciones,
                                         int posicionLista,
                                         boolean preparada,
                                         boolean reproduciendo,
                                         int progresoMs,
                                         int duracionMs,
                                         int modoReproduccion,
                                         boolean modoLista) {

        if (cancionActual == null) {
            cancelar();
            ultimaRuta = "";
            rutaImagenCargando = "";
            versionCargaImagen++;
            return;
        }

        long ahora = System.currentTimeMillis();
        String rutaActual = safe(cancionActual.getRutaArchivo());

        boolean cambioCancion = !rutaActual.equals(ultimaRuta);
        boolean cambioEstado = reproduciendo != ultimoEstadoReproduciendo;
        boolean actualizarProgreso = ahora - ultimaActualizacionProgreso >= 1000;

        if (!cambioCancion && !cambioEstado && !actualizarProgreso) {
            return;
        }

        ultimaRuta = rutaActual;
        ultimoEstadoReproduciendo = reproduciendo;
        ultimaActualizacionProgreso = ahora;

        mostrarActualizar(
                cancionActual,
                preparada,
                reproduciendo,
                progresoMs,
                duracionMs
        );
    }

    private void mostrarActualizar(Cancion cancion,
                                   boolean preparada,
                                   boolean reproduciendo,
                                   int progresoMs,
                                   int duracionMs) {

        if (!tienePermisoNotificaciones()) {
            return;
        }

        RemoteViews views = crearRemoteViews(
                cancion,
                preparada,
                reproduciendo,
                progresoMs,
                duracionMs
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(safe(cancion.getNombre()))
                .setContentText(safe(cancion.getArtista()))
                .setCustomContentView(views)
                .setCustomBigContentView(views)
                .setContentIntent(crearPendingIntentAbrirApp())
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

    private RemoteViews crearRemoteViews(Cancion cancion,
                                         boolean preparada,
                                         boolean reproduciendo,
                                         int progresoMs,
                                         int duracionMs) {

        RemoteViews views = new RemoteViews(
                context.getPackageName(),
                R.layout.notification_reproductor
        );

        views.setTextViewText(R.id.tvNotifNombre, safe(cancion.getNombre()));

        String artista = safe(cancion.getArtista());
        if (TextUtils.isEmpty(artista)) {
            artista = "Artista desconocido";
        }

        views.setTextViewText(R.id.tvNotifArtista, artista);

        Bitmap imagen = obtenerImagenCacheada(cancion, preparada, reproduciendo, progresoMs, duracionMs);

        if (imagen != null) {
            views.setImageViewBitmap(R.id.ivNotifImagen, imagen);
        } else {
            views.setImageViewResource(R.id.ivNotifImagen, R.drawable.imagenotfound);
        }

        views.setImageViewResource(R.id.btnNotifAnterior, android.R.drawable.ic_media_previous);

        views.setImageViewResource(
                R.id.btnNotifPlayPause,
                reproduciendo ? R.drawable.ic_player_pause : R.drawable.ic_player_play
        );

        views.setImageViewResource(R.id.btnNotifSiguiente, android.R.drawable.ic_media_next);
        views.setImageViewResource(R.id.btnNotifCerrar, android.R.drawable.ic_menu_close_clear_cancel);

        views.setOnClickPendingIntent(
                R.id.btnNotifAnterior,
                crearPendingIntentAccion(ACTION_ANTERIOR, 201)
        );

        views.setOnClickPendingIntent(
                R.id.btnNotifPlayPause,
                crearPendingIntentAccion(ACTION_PLAY_PAUSE, 202)
        );

        views.setOnClickPendingIntent(
                R.id.btnNotifSiguiente,
                crearPendingIntentAccion(ACTION_SIGUIENTE, 203)
        );

        views.setOnClickPendingIntent(
                R.id.btnNotifCerrar,
                crearPendingIntentAccion(ACTION_DETENER, 204)
        );

        int duracionSegura = duracionMs > 0 ? duracionMs : 1;
        int progresoSeguro = Math.max(0, Math.min(progresoMs, duracionSegura));

        views.setTextViewText(R.id.tvNotifTiempoActual, formatearTiempo(progresoSeguro));
        views.setTextViewText(
                R.id.tvNotifTiempoFinal,
                preparada && duracionMs > 0 ? formatearTiempo(duracionMs) : "--:--"
        );

        views.setProgressBar(
                R.id.progressNotifCancion,
                duracionSegura,
                progresoSeguro,
                false
        );

        return views;
    }

    private Bitmap obtenerImagenCacheada(Cancion cancion,
                                         boolean preparada,
                                         boolean reproduciendo,
                                         int progresoMs,
                                         int duracionMs) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            return null;
        }

        String ruta = safe(cancion.getRutaArchivo());

        Bitmap cacheada = imagenCache.get(ruta);

        if (cacheada != null) {
            return cacheada;
        }

        cargarImagenNotificacionAsync(
                cancion,
                preparada,
                reproduciendo,
                progresoMs,
                duracionMs
        );

        return null;
    }

    private void cargarImagenNotificacionAsync(Cancion cancion,
                                               boolean preparada,
                                               boolean reproduciendo,
                                               int progresoMs,
                                               int duracionMs) {
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            return;
        }

        String ruta = safe(cancion.getRutaArchivo());

        if (ruta.equals(rutaImagenCargando)) {
            return;
        }

        rutaImagenCargando = ruta;
        final int versionActual = ++versionCargaImagen;

        imagenExecutor.execute(() -> {
            Bitmap bitmap = obtenerImagenDesdeArchivoRedondeada(ruta);

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

            mostrarActualizar(
                    cancion,
                    preparada,
                    reproduciendo,
                    progresoMs,
                    duracionMs
            );
        });
    }

    private PendingIntent crearPendingIntentAccion(String action, int requestCode) {
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

    private PendingIntent crearPendingIntentAbrirApp() {
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

    public void liberar() {
        cancelar();
        imagenExecutor.shutdownNow();
        imagenCache.evictAll();
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

    private Bitmap obtenerImagenDesdeArchivoRedondeada(String ruta) {
        if (TextUtils.isEmpty(ruta)) return null;

        MediaMetadataRetriever mmr = null;

        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);

            byte[] art = mmr.getEmbeddedPicture();

            if (art == null) {
                return null;
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(art, 0, art.length, bounds);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcularInSampleSize(bounds, dp(96), dp(96));
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length, options);

            if (bitmap == null) {
                return null;
            }

            return crearBitmapRedondeado(bitmap, dp(12));

        } catch (Exception ignored) {
            return null;

        } finally {
            try {
                if (mmr != null) {
                    mmr.release();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private int calcularInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return Math.max(1, inSampleSize);
    }

    private Bitmap crearBitmapRedondeado(Bitmap bitmap, float radio) {
        if (bitmap == null) return null;

        Bitmap bitmapCuadrado = recortarCentroCuadrado(bitmap);

        Bitmap salida = Bitmap.createBitmap(
                bitmapCuadrado.getWidth(),
                bitmapCuadrado.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(salida);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RectF rect = new RectF(
                0,
                0,
                bitmapCuadrado.getWidth(),
                bitmapCuadrado.getHeight()
        );

        canvas.drawRoundRect(rect, radio, radio, paint);

        paint.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.SRC_IN
        ));

        canvas.drawBitmap(bitmapCuadrado, 0, 0, paint);

        return salida;
    }

    private Bitmap recortarCentroCuadrado(Bitmap bitmap) {
        int ancho = bitmap.getWidth();
        int alto = bitmap.getHeight();

        int lado = Math.min(ancho, alto);

        int x = (ancho - lado) / 2;
        int y = (alto - lado) / 2;

        return Bitmap.createBitmap(bitmap, x, y, lado, lado);
    }

    private String formatearTiempo(int ms) {
        int segundos = Math.max(0, ms / 1000);
        return (segundos / 60) + ":" + String.format("%02d", segundos % 60);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}