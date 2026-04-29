package es.iesagora.fd_pdplayer.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.ViewholderCancionBinding;
import es.iesagora.fd_pdplayer.models.Cancion;

public class CancionesAdapter extends RecyclerView.Adapter<CancionesAdapter.CancionViewHolder> {

    public interface Listener {
        void onOpcionesCancion(View anchor, Cancion cancion);
        void onClickCancion(Cancion cancion);
    }

    private List<Cancion> canciones;
    private final LayoutInflater inflater;
    private final Context context;
    private final Listener listener;

    // Para estado "playing"
    private String rutaCancionReproduciendo = null;

    public CancionesAdapter(Context context, ArrayList<Cancion> canciones, Listener listener) {
        this.context = context;
        this.canciones = canciones;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
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

        boolean isPlaying = rutaCancionReproduciendo != null
                && rutaCancionReproduciendo.equals(cancion.getRutaArchivo());

        aplicarEstiloPlaying(holder, isPlaying);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClickCancion(cancion);
        });

        holder.binding.btnOpciones.setOnClickListener(v -> {
            if (listener != null) listener.onOpcionesCancion(v, cancion);
        });
    }

    @Override
    public int getItemCount() {
        return canciones != null ? canciones.size() : 0;
    }

    public void establecerLista(List<Cancion> canciones) {
        this.canciones = canciones;
        notifyDataSetChanged();
    }

    public void setCancionReproduciendo(String rutaArchivo) {
        this.rutaCancionReproduciendo = rutaArchivo;
        notifyDataSetChanged();
    }

    private void aplicarEstiloPlaying(@NonNull CancionViewHolder holder, boolean playing) {
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

    private Bitmap obtenerCaratulaDesdeArchivo(String ruta) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();

            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception ignored) {}
        return null;
    }
}