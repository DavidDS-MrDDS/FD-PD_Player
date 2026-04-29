package es.iesagora.fd_pdplayer.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.ViewholderCancionEnListaBinding;
import es.iesagora.fd_pdplayer.models.Cancion;

public class CancionesEnListaAdapter extends RecyclerView.Adapter<CancionesEnListaAdapter.CancionEnListaViewHolder> {

    public interface Listener {
        void onClickCancion(Cancion cancion);
        void onQuitarCancion(Cancion cancion);
    }

    private final LayoutInflater inflater;
    private final Listener listener;
    private List<Cancion> canciones = new ArrayList<>();

    public CancionesEnListaAdapter(Context context, Listener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CancionEnListaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewholderCancionEnListaBinding binding = ViewholderCancionEnListaBinding.inflate(inflater, parent, false);
        return new CancionEnListaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CancionEnListaViewHolder holder, int position) {
        Cancion cancion = canciones.get(position);

        holder.binding.tvNombre.setText(cancion.getNombre());

        String album = safe(cancion.getAlbum());
        String artista = safe(cancion.getArtista());

        String sub;
        if (!TextUtils.isEmpty(album) && !TextUtils.isEmpty(artista)) {
            sub = album + " • " + artista;
        } else if (!TextUtils.isEmpty(album)) {
            sub = album;
        } else {
            sub = artista;
        }

        holder.binding.tvSub.setText(sub);

        Bitmap caratula = obtenerCaratulaDesdeArchivo(cancion.getRutaArchivo());
        if (caratula != null) {
            holder.binding.ivIcono.setImageBitmap(caratula);
        } else {
            holder.binding.ivIcono.setImageResource(R.drawable.imagenotfound);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClickCancion(cancion);
            }
        });

        holder.binding.btnQuitar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onQuitarCancion(cancion);
            }
        });
    }

    @Override
    public int getItemCount() {
        return canciones != null ? canciones.size() : 0;
    }

    public void setCanciones(List<Cancion> canciones) {
        this.canciones = canciones != null ? canciones : new ArrayList<>();
        notifyDataSetChanged();
    }

    private Bitmap obtenerCaratulaDesdeArchivo(String ruta) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();

            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class CancionEnListaViewHolder extends RecyclerView.ViewHolder {

        private final ViewholderCancionEnListaBinding binding;

        public CancionEnListaViewHolder(@NonNull ViewholderCancionEnListaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}