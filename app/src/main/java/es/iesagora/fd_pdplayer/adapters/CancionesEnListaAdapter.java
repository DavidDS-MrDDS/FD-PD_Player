package es.iesagora.fd_pdplayer.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.ViewholderCancionBinding;
import es.iesagora.fd_pdplayer.models.Cancion;

public class CancionesEnListaAdapter extends RecyclerView.Adapter<CancionesEnListaAdapter.CancionViewHolder> {

    public interface Listener {
        void onPedirQuitar(Cancion cancion);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;

    private List<Cancion> canciones = new ArrayList<>();

    // (Opcional) estado "playing"
    private String rutaCancionReproduciendo = null;

    public CancionesEnListaAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    public void setCanciones(List<Cancion> canciones) {
        this.canciones = canciones != null ? canciones : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** Opcional: para resaltar la canción en reproducción */
    public void setCancionReproduciendo(String rutaArchivo) {
        this.rutaCancionReproduciendo = rutaArchivo;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CancionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.viewholder_cancion, parent, false);
        return new CancionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CancionViewHolder holder, int position) {
        Cancion c = canciones.get(position);

        holder.binding.tvNombre.setText(c.getNombre());

        // Ahora usamos tvSub: "Álbum • Artista"
        String album = safe(c.getAlbum());
        String artista = safe(c.getArtista());

        String sub;
        if (!TextUtils.isEmpty(album) && !TextUtils.isEmpty(artista)) sub = album + " • " + artista;
        else if (!TextUtils.isEmpty(album)) sub = album;
        else sub = artista;

        holder.binding.tvSub.setText(sub);

        Bitmap caratula = obtenerCaratulaDesdeArchivo(c.getRutaArchivo());
        if (caratula != null) holder.binding.ivIcono.setImageBitmap(caratula);
        else holder.binding.ivIcono.setImageResource(R.drawable.imagenotfound);

        // (Opcional) estilo "playing"
        boolean isPlaying = rutaCancionReproduciendo != null
                && rutaCancionReproduciendo.equals(c.getRutaArchivo());
        aplicarEstiloPlaying(holder, isPlaying);

        holder.binding.btnOpciones.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, v);
            popup.inflate(R.menu.menu_cancionlista);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_remove_from_list) {
                    listener.onPedirQuitar(c);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return canciones != null ? canciones.size() : 0;
    }

    private void aplicarEstiloPlaying(@NonNull CancionViewHolder holder, boolean playing) {
        // Requiere android:id="@+id/card" en el MaterialCardView del layout
        MaterialCardView card = holder.binding.card;

        if (playing) {
            int primary = 0xFF1337EC;

            card.setStrokeWidth(dpToPx(1));
            card.setStrokeColor(0x331337EC);
            card.setCardBackgroundColor(0x141337EC);

            holder.binding.tvNombre.setTextColor(primary);
            holder.binding.tvSub.setTextColor(0x991337EC);
        } else {
            card.setStrokeWidth(0);
            card.setStrokeColor(0x00000000);
            card.setCardBackgroundColor(0x1AFFFFFF);

            holder.binding.tvNombre.setTextColor(0xFFFFFFFF);
            holder.binding.tvSub.setTextColor(0xFFB9C0FF);
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    static class CancionViewHolder extends RecyclerView.ViewHolder {
        ViewholderCancionBinding binding;

        public CancionViewHolder(@NonNull View itemView) {
            super(itemView);
            binding = ViewholderCancionBinding.bind(itemView);
        }
    }

    private Bitmap obtenerCaratulaDesdeArchivo(String ruta) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();
            if (art != null) return BitmapFactory.decodeByteArray(art, 0, art.length);
        } catch (Exception ignored) {}
        return null;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
