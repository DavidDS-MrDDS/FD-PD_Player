package es.iesagora.fd_pdplayer.funcionamiento;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.util.LruCache;

public class ImagenCancionUtils {

    private ImagenCancionUtils() {
    }

    public static LruCache<String, Bitmap> crearCacheImagenes(int divisorMemoria) {
        int memoriaMaximaKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int divisorSeguro = Math.max(1, divisorMemoria);
        int tamanoCacheKb = Math.max(1024, memoriaMaximaKb / divisorSeguro);

        return new LruCache<String, Bitmap>(tamanoCacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                if (value == null) return 0;
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
    }

    public static Bitmap obtenerImagenDesdeArchivoReducida(String ruta, int anchoDeseado, int altoDeseado) {
        if (TextUtils.isEmpty(ruta)) return null;

        MediaMetadataRetriever mmr = null;

        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);

            byte[] art = mmr.getEmbeddedPicture();

            if (art == null) {
                return null;
            }

            return decodificarBitmapReducido(art, anchoDeseado, altoDeseado);
        } catch (Exception ignored) {
            return null;
        } finally {
            liberarMetadataRetriever(mmr);
        }
    }

    public static Bitmap obtenerImagenDesdeArchivoRedondeada(String ruta, int anchoDeseado, int altoDeseado, float radio) {
        Bitmap bitmap = obtenerImagenDesdeArchivoReducida(ruta, anchoDeseado, altoDeseado);

        if (bitmap == null) {
            return null;
        }

        return crearBitmapRedondeado(bitmap, radio);
    }

    public static Bitmap decodificarBitmapReducido(byte[] data, int reqWidth, int reqHeight) {
        if (data == null || data.length == 0) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calcularInSampleSize(bounds, reqWidth, reqHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    public static int calcularInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (options == null) return 1;

        int height = options.outHeight;
        int width = options.outWidth;

        if (height <= 0 || width <= 0) {
            return 1;
        }

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

    public static Bitmap crearBitmapRedondeado(Bitmap bitmap, float radio) {
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

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmapCuadrado, 0, 0, paint);
        paint.setXfermode(null);

        return salida;
    }

    public static Bitmap recortarCentroCuadrado(Bitmap bitmap) {
        if (bitmap == null) return null;

        int ancho = bitmap.getWidth();
        int alto = bitmap.getHeight();
        int lado = Math.min(ancho, alto);

        int x = (ancho - lado) / 2;
        int y = (alto - lado) / 2;

        return Bitmap.createBitmap(bitmap, x, y, lado, lado);
    }

    public static int dp(Context context, int value) {
        if (context == null) return value;

        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static void liberarMetadataRetriever(MediaMetadataRetriever mmr) {
        if (mmr == null) return;

        try {
            mmr.release();
        } catch (Exception ignored) {
        }
    }
}