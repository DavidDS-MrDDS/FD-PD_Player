package es.iesagora.fd_pdplayer.fragments.internalFragments.itemFragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaCanciones;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentListaBinding;
import es.iesagora.fd_pdplayer.models.Cancion;
import es.iesagora.fd_pdplayer.adapters.CancionesEnListaAdapter;

public class ListaFragment extends Fragment {

    private FragmentListaBinding binding;
    private CancionesEnListaAdapter adapter;
    private ListasViewModel viewModel;
    private int listaId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentListaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ListasViewModel.class);
        listaId = getArguments() != null ? getArguments().getInt("listaId", -1) : -1;

        adapter = new CancionesEnListaAdapter(requireContext(), this::confirmarQuitarCancion);
        binding.recyclerViewCancionesLista.setAdapter(adapter);

        viewModel.obtenerListaConCanciones(listaId).observe(getViewLifecycleOwner(), this::pintarLista);
    }

    private void pintarLista(ListaCanciones data) {
        if (data == null || data.lista == null) {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.tvNombreListaDetalle.setText(data.lista.getNombre());

        ArrayList<Cancion> canciones = new ArrayList<>();
        if (data.canciones != null) {
            for (var c : data.canciones) {
                canciones.add(new Cancion(c.getNombre(), c.getArtista(), c.getAlbum(), c.getRutaArchivo()));
            }
        }
        adapter.setCanciones(canciones);
    }

    private void confirmarQuitarCancion(Cancion cancion) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Quitar canción")
                .setMessage("¿Quitar \"" + cancion.getNombre() + "\" de la lista?")
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Quitar", (d, w) -> viewModel.quitarCancionDeLista(listaId, cancion.getRutaArchivo()))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}



