package es.iesagora.fd_pdplayer.funcionamiento.adapters;

import android.content.Context;
import android.graphics.Bitmap;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.ViewholderCancionBinding;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.ImagenCancionUtils;
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

    private boolean modoOcultacion = false;
    private final Set<String> rutasSeleccionadas = new HashSet<>();

    private boolean liberado = false;

    public CancionesAdapter(Context context, ArrayList<Cancion> canciones, Listener listener) {
        this.canciones = canciones != null ? canciones : new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
        this.cacheCaratulas = ImagenCancionUtils.crearCacheImagenes(8);
    }

    @NonNull
    @Override
    public CancionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.viewholder_cancion, parent, false);
        return new CancionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CancionViewHolder holder, int position) {
        if (liberado || canciones == null || position < 0 || position >= canciones.size()) {
            return;
        }

        Cancion cancion = canciones.get(position);

        holder.binding.tvNombre.setText(safe(cancion.getNombre()));
        holder.binding.tvSub.setText(crearSubtitulo(cancion));

        actualizarModoOcultacion(holder, cancion);
        cargarCaratula(holder, cancion);

        holder.itemView.setOnClickListener(v -> {
            if (!liberado && listener != null) {
                listener.onClickCancion(cancion);
            }
        });

        holder.binding.btnOpciones.setOnClickListener(v -> {
            if (!liberado && listener != null) {
                listener.onOpcionesCancion(v, cancion);
            }
        });
    }

    private void actualizarModoOcultacion(@NonNull CancionViewHolder holder, Cancion cancion) {
        String ruta = cancion != null ? safe(cancion.getRutaArchivo()) : "";
        boolean seleccionada = rutasSeleccionadas.contains(ruta);

        if (modoOcultacion) {
            holder.binding.btnOpciones.setImageResource(
                    com.google.android.material.R.drawable.design_ic_visibility_off
            );
            holder.binding.btnOpciones.setContentDescription("Seleccionar canción para ocultar");

            if (seleccionada) {
                holder.binding.card.setCardBackgroundColor(0xFF4A2D35);
                holder.binding.card.setStrokeColor(0xFFFFB4AB);
            } else {
                holder.binding.card.setCardBackgroundColor(0xFF2D3344);
                holder.binding.card.setStrokeColor(0xFF3A4154);
            }
        } else {
            holder.binding.btnOpciones.setImageResource(R.drawable.ic_arrow_circle_down);
            holder.binding.btnOpciones.setContentDescription("Opciones canción");

            holder.binding.card.setCardBackgroundColor(0xFF2D3344);
            holder.binding.card.setStrokeColor(0xFF3A4154);
        }
    }

    private String crearSubtitulo(Cancion cancion) {
        String album = safe(cancion.getAlbum());
        String artista = safe(cancion.getArtista());

        if (!TextUtils.isEmpty(album) && !TextUtils.isEmpty(artista)) {
            return album + " • " + artista;
        }

        if (!TextUtils.isEmpty(album)) {
            return album;
        }

        if (!TextUtils.isEmpty(artista)) {
            return artista;
        }

        return "<unknown>";
    }

    private void cargarCaratula(@NonNull CancionViewHolder holder, Cancion cancion) {
        if (liberado || cancion == null) return;

        String ruta = safe(cancion.getRutaArchivo());

        holder.binding.ivIcono.setTag(ruta);
        holder.binding.ivIcono.setImageResource(R.drawable.imagenotfound);

        if (TextUtils.isEmpty(ruta)) return;

        Bitmap bitmapCache = cacheCaratulas.get(ruta);

        if (bitmapCache != null) {
            holder.binding.ivIcono.setImageBitmap(bitmapCache);
            return;
        }

        if (rutasCargando.contains(ruta)) return;

        rutasCargando.add(ruta);

        try {
            executorCaratulas.execute(() -> {
                if (liberado) {
                    rutasCargando.remove(ruta);
                    return;
                }

                Bitmap bitmap = ImagenCancionUtils.obtenerImagenDesdeArchivoReducida(
                        ruta,
                        160,
                        160
                );

                if (bitmap != null && !liberado) {
                    cacheCaratulas.put(ruta, bitmap);
                }

                rutasCargando.remove(ruta);

                mainHandler.post(() -> {
                    if (liberado) return;

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
        } catch (RejectedExecutionException e) {
            rutasCargando.remove(ruta);
        }
    }

    @Override
    public int getItemCount() {
        return canciones != null ? canciones.size() : 0;
    }

    public void establecerLista(List<Cancion> canciones) {
        if (liberado) return;

        this.canciones = canciones != null ? canciones : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setModoOcultacion(boolean modoOcultacion) {
        if (liberado) return;

        this.modoOcultacion = modoOcultacion;

        if (!modoOcultacion) {
            rutasSeleccionadas.clear();
        }

        notifyDataSetChanged();
    }

    public void setCancionesSeleccionadas(Set<String> rutasSeleccionadas) {
        if (liberado) return;

        this.rutasSeleccionadas.clear();

        if (rutasSeleccionadas != null) {
            this.rutasSeleccionadas.addAll(rutasSeleccionadas);
        }

        notifyDataSetChanged();
    }

    public void liberar() {
        liberado = true;

        mainHandler.removeCallbacksAndMessages(null);
        executorCaratulas.shutdownNow();

        cacheCaratulas.evictAll();
        rutasCargando.clear();
        rutasSeleccionadas.clear();
        canciones = new ArrayList<>();
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