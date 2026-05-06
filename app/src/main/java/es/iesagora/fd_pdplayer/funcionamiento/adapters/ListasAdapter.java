package es.iesagora.fd_pdplayer.funcionamiento.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.databinding.ViewholderListaBinding;

public class ListasAdapter extends RecyclerView.Adapter<ListasAdapter.ListaViewHolder> {

    public interface Listener {
        void onAbrir(ListaEntity lista);
        void onPedirBorrar(ListaEntity lista);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;
    private List<ListaEntity> listas = new ArrayList<>();

    public ListasAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    public void setListas(List<ListaEntity> listas) {
        this.listas = listas != null ? listas : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ListaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.viewholder_lista, parent, false);
        return new ListaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ListaViewHolder holder, int position) {
        ListaEntity lista = listas.get(position);

        holder.binding.tvNombreLista.setText(lista.getNombre());

        holder.itemView.setOnClickListener(v -> listener.onAbrir(lista));

        holder.binding.btnOpcionesLista.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPedirBorrar(lista);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listas != null ? listas.size() : 0;
    }

    static class ListaViewHolder extends RecyclerView.ViewHolder {
        ViewholderListaBinding binding;

        public ListaViewHolder(@NonNull View itemView) {
            super(itemView);
            binding = ViewholderListaBinding.bind(itemView);
        }
    }
}


