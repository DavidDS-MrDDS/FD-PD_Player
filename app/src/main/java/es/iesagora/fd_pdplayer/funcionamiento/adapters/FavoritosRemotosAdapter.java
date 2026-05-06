package es.iesagora.fd_pdplayer.funcionamiento.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;
import es.iesagora.fd_pdplayer.databinding.ViewholderBusquedaCancionBinding;

public class FavoritosRemotosAdapter extends RecyclerView.Adapter<FavoritosRemotosAdapter.RemoteViewHolder> {

    public interface Listener {
        void onClick(FavoriteItem item);
        void onDelete(FavoriteItem item);
    }

    private final Listener listener;
    private final boolean mostrarUsuario;
    private final boolean mostrarEliminar;
    private List<FavoriteItem> items = new ArrayList<>();

    public FavoritosRemotosAdapter(boolean mostrarUsuario, boolean mostrarEliminar, Listener listener) {
        this.mostrarUsuario = mostrarUsuario;
        this.mostrarEliminar = mostrarEliminar;
        this.listener = listener;
    }

    public void setItems(List<FavoriteItem> items) {
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

        Bitmap caratula = obtenerCaratulaDesdeArchivo(item.getRutaArchivo());

        if (caratula != null) {
            holder.binding.ivIcono.setImageBitmap(caratula);
        } else {
            holder.binding.ivIcono.setImageResource(R.drawable.imagenotfound);
        }

        holder.binding.btnEliminar.setVisibility(mostrarEliminar ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });

        holder.binding.btnEliminar.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RemoteViewHolder extends RecyclerView.ViewHolder {
        private final ViewholderBusquedaCancionBinding binding;

        public RemoteViewHolder(@NonNull ViewholderBusquedaCancionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private Bitmap obtenerCaratulaDesdeArchivo(String ruta) {
        if (TextUtils.isEmpty(ruta)) return null;

        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);

            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();

            if (art != null && art.length > 0) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }

        } catch (Exception ignored) {
        }

        return null;
    }
}