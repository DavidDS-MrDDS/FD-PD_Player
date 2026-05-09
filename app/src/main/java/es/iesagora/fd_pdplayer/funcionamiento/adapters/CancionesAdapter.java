package es.iesagora.fd_pdplayer.funcionamiento.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.ViewholderCancionBinding;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionesAdapter extends RecyclerView.Adapter<CancionesAdapter.CancionViewHolder> {

    public interface Listener {
        void onOpcionesCancion(View anchor, Cancion cancion);
        void onClickCancion(Cancion cancion);
    }

    private List<Cancion> canciones;
    private final LayoutInflater inflater;
    private final Listener listener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorCaratulas = Executors.newFixedThreadPool(2);

    private final LruCache<String, Bitmap> cacheCaratulas;
    private final Set<String> rutasCargando = ConcurrentHashMap.newKeySet();

    private String rutaCancionReproduciendo = null;

    public CancionesAdapter(Context context, ArrayList<Cancion> canciones, Listener listener) {
        this.canciones = canciones != null ? canciones : new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;

        int maxMemoryKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSizeKb = maxMemoryKb / 8;

        cacheCaratulas = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @NonNull
    @Override
    public CancionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.viewholder_cancion, parent, false);
        return new CancionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CancionViewHolder holder, int position) {
        Cancion cancion = canciones.get(position);

        holder.binding.tvNombre.setText(safe(cancion.getNombre()));

        String album = safe(cancion.getAlbum());
        String artista = safe(cancion.getArtista());

        String sub;
        if (!TextUtils.isEmpty(album) && !TextUtils.isEmpty(artista)) {
            sub = album + " • " + artista;
        } else if (!TextUtils.isEmpty(album)) {
            sub = album;
        } else if (!TextUtils.isEmpty(artista)) {
            sub = artista;
        } else {
            sub = "<unknown>";
        }

        holder.binding.tvSub.setText(sub);

        cargarCaratula(holder, cancion);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClickCancion(cancion);
            }
        });

        holder.binding.btnOpciones.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpcionesCancion(v, cancion);
            }
        });
    }

    private void cargarCaratula(@NonNull CancionViewHolder holder, Cancion cancion) {
        String ruta = safe(cancion.getRutaArchivo());

        holder.binding.ivIcono.setTag(ruta);
        holder.binding.ivIcono.setImageResource(R.drawable.imagenotfound);

        if (TextUtils.isEmpty(ruta)) {
            return;
        }

        Bitmap bitmapCache = cacheCaratulas.get(ruta);

        if (bitmapCache != null) {
            holder.binding.ivIcono.setImageBitmap(bitmapCache);
            return;
        }

        if (rutasCargando.contains(ruta)) {
            return;
        }

        rutasCargando.add(ruta);

        executorCaratulas.execute(() -> {
            Bitmap bitmap = obtenerCaratulaDesdeArchivo(ruta);

            if (bitmap != null) {
                cacheCaratulas.put(ruta, bitmap);
            }

            rutasCargando.remove(ruta);

            mainHandler.post(() -> {
                Object tagActual = holder.binding.ivIcono.getTag();

                if (tagActual != null && tagActual.equals(ruta)) {
                    if (bitmap != null) {
                        holder.binding.ivIcono.setImageBitmap(bitmap);
                    } else {
                        holder.binding.ivIcono.setImageResource(R.drawable.imagenotfound);
                    }
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return canciones != null ? canciones.size() : 0;
    }

    public void establecerLista(List<Cancion> canciones) {
        this.canciones = canciones != null ? canciones : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setCancionReproduciendo(String rutaArchivo) {
        this.rutaCancionReproduciendo = rutaArchivo;
        notifyDataSetChanged();
    }

    public void liberar() {
        executorCaratulas.shutdownNow();
        cacheCaratulas.evictAll();
        rutasCargando.clear();
    }

    private Bitmap obtenerCaratulaDesdeArchivo(String ruta) {
        if (TextUtils.isEmpty(ruta)) {
            return null;
        }

        MediaMetadataRetriever mmr = null;

        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);

            byte[] art = mmr.getEmbeddedPicture();

            if (art != null) {
                return decodificarBitmapReducido(art, 160, 160);
            }

        } catch (Exception ignored) {
        } finally {
            try {
                if (mmr != null) {
                    mmr.release();
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private Bitmap decodificarBitmapReducido(byte[] data, int reqWidth, int reqHeight) {
        if (data == null) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        options.inSampleSize = calcularInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
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

        return Math.max(inSampleSize, 1);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    public static class CancionViewHolder extends RecyclerView.ViewHolder {
        ViewholderCancionBinding binding;

        public CancionViewHolder(@NonNull View itemView) {
            super(itemView);
            binding = ViewholderCancionBinding.bind(itemView);
        }
    }
}