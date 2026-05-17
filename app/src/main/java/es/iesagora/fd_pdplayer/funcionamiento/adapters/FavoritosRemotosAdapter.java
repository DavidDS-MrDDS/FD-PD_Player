package es.iesagora.fd_pdplayer.funcionamiento.adapters;

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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;
import es.iesagora.fd_pdplayer.databinding.ViewholderBusquedaCancionBinding;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.ImagenCancionUtils;

public class FavoritosRemotosAdapter extends RecyclerView.Adapter<FavoritosRemotosAdapter.RemoteViewHolder> {

    public interface Listener {
        void onClick(FavoriteItem item);
        void onDelete(FavoriteItem item);
    }

    private final Listener listener;
    private final boolean mostrarUsuario;
    private final boolean mostrarEliminar;

    private List<FavoriteItem> items = new ArrayList<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ExecutorService executorCaratulas = Executors.newFixedThreadPool(2);

    private final LruCache<String, Bitmap> cacheCaratulas;

    private final Set<String> rutasCargando = ConcurrentHashMap.newKeySet();

    private boolean liberado = false;

    public FavoritosRemotosAdapter(boolean mostrarUsuario, boolean mostrarEliminar, Listener listener) {
        this.mostrarUsuario = mostrarUsuario;
        this.mostrarEliminar = mostrarEliminar;
        this.listener = listener;
        this.cacheCaratulas = ImagenCancionUtils.crearCacheImagenes(8);
    }

    public void setItems(List<FavoriteItem> items) {
        if (liberado) return;

        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RemoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewholderBusquedaCancionBinding binding = ViewholderBusquedaCancionBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );

        return new RemoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RemoteViewHolder holder, int position) {
        if (liberado || items == null || position < 0 || position >= items.size()) {
            return;
        }

        FavoriteItem item = items.get(position);

        holder.binding.tvUsuario.setVisibility(mostrarUsuario ? View.VISIBLE : View.GONE);
        holder.binding.tvUsuario.setText(
                item.getUsername() != null && !item.getUsername().isEmpty()
                        ? item.getUsername()
                        : "Usuario"
        );

        holder.binding.tvNombre.setText(
                item.getNombre() != null ? item.getNombre() : "Sin nombre"
        );

        String album = item.getAlbum() != null ? item.getAlbum() : "";
        String artista = item.getArtista() != null ? item.getArtista() : "";

        String sub;

        if (!album.isEmpty() && !artista.isEmpty()) {
            sub = album + " • " + artista;
        } else if (!album.isEmpty()) {
            sub = album;
        } else {
            sub = artista;
        }

        holder.binding.tvSub.setText(sub);

        cargarCaratula(holder, item);

        holder.binding.btnEliminar.setVisibility(mostrarEliminar ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (!liberado && listener != null) {
                listener.onClick(item);
            }
        });

        holder.binding.btnEliminar.setOnClickListener(v -> {
            if (!liberado && listener != null) {
                listener.onDelete(item);
            }
        });
    }

    private void cargarCaratula(@NonNull RemoteViewHolder holder, FavoriteItem item) {
        if (liberado || item == null) return;

        String ruta = safe(item.getRutaArchivo());

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
        return items != null ? items.size() : 0;
    }

    public void liberar() {
        liberado = true;

        mainHandler.removeCallbacksAndMessages(null);
        executorCaratulas.shutdownNow();

        cacheCaratulas.evictAll();
        rutasCargando.clear();
        items = new ArrayList<>();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    static class RemoteViewHolder extends RecyclerView.ViewHolder {

        private final ViewholderBusquedaCancionBinding binding;

        public RemoteViewHolder(@NonNull ViewholderBusquedaCancionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}