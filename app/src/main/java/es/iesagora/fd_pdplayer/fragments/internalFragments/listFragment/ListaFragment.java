package es.iesagora.fd_pdplayer.fragments.internalFragments.listFragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import java.util.ArrayList;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.adapters.CancionesEnListaAdapter;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.CancionEnListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaCanciones;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentListaBinding;
import es.iesagora.fd_pdplayer.models.Cancion;

public class ListaFragment extends Fragment {

    private FragmentListaBinding binding;
    private CancionesEnListaAdapter adapter;
    private ListasViewModel viewModel;
    private int listaId;

    private ArrayList<Cancion> cancionesActuales = new ArrayList<>();

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

        adapter = new CancionesEnListaAdapter(requireContext(), new CancionesEnListaAdapter.Listener() {
            @Override
            public void onClickCancion(Cancion cancion) {
                abrirCancionDeLista(cancion);
            }

            @Override
            public void onQuitarCancion(Cancion cancion) {
                confirmarQuitarCancion(cancion);
            }
        });

        binding.recyclerViewCancionesLista.setAdapter(adapter);

        viewModel.obtenerListaConCanciones(listaId).observe(getViewLifecycleOwner(), this::pintarLista);
    }

    private void pintarLista(ListaCanciones data) {
        if (data == null || data.lista == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.tvNombreListaDetalle.setText(data.lista.getNombre());

        cancionesActuales = new ArrayList<>();

        if (data.canciones != null) {
            for (CancionEnListaEntity c : data.canciones) {
                cancionesActuales.add(new Cancion(
                        c.getNombre(),
                        c.getArtista(),
                        c.getAlbum(),
                        c.getRutaArchivo()
                ));
            }
        }

        binding.tvContadorListaDetalle.setText("Canciones [" + cancionesActuales.size() + "]");
        adapter.setCanciones(cancionesActuales);
    }

    private void abrirCancionDeLista(Cancion cancion) {
        if (cancionesActuales == null || cancionesActuales.isEmpty()) {
            Toast.makeText(requireContext(), "Esta lista no tiene canciones", Toast.LENGTH_SHORT).show();
            return;
        }

        int posicion = cancionesActuales.indexOf(cancion);

        if (posicion < 0) {
            posicion = 0;
        }

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);
        bundle.putSerializable("listaCanciones", new ArrayList<>(cancionesActuales));
        bundle.putInt("posicion", posicion);

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_listaFragment_to_cancionEnListaFragment, bundle);
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