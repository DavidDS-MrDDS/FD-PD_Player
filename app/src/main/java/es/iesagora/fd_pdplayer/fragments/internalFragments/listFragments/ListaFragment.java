package es.iesagora.fd_pdplayer.fragments.internalFragments.listFragments;

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
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.CancionEnListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaCanciones;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentListaBinding;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.CancionesEnListaAdapter;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

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

        binding.recyclerViewCancionesLista.setLayoutManager(new LinearLayoutManager(requireContext()));
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
        if (cancion == null) {
            Toast.makeText(requireContext(), "No se encontró la canción", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cancionesActuales == null || cancionesActuales.isEmpty()) {
            Toast.makeText(requireContext(), "Esta lista no tiene canciones", Toast.LENGTH_SHORT).show();
            return;
        }

        int posicion = cancionesActuales.indexOf(cancion);

        if (posicion < 0) {
            posicion = buscarPosicionPorRuta(cancion.getRutaArchivo());
        }

        if (posicion < 0) {
            posicion = 0;
        }

        ReproductorApp.getInstance().liberar();

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancionesActuales.get(posicion));
        bundle.putSerializable("listaCanciones", new ArrayList<>(cancionesActuales));
        bundle.putInt("posicion", posicion);

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_listaFragment_to_cancionEnListaFragment, bundle);
    }

    private int buscarPosicionPorRuta(String rutaArchivo) {
        if (rutaArchivo == null || cancionesActuales == null) {
            return -1;
        }

        for (int i = 0; i < cancionesActuales.size(); i++) {
            Cancion c = cancionesActuales.get(i);

            if (c != null && rutaArchivo.equals(c.getRutaArchivo())) {
                return i;
            }
        }

        return -1;
    }

    private void confirmarQuitarCancion(Cancion cancion) {
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "QuitarCancionLista",
                "Quitar canción",
                "¿Quitar \"" + cancion.getNombre() + "\" de la lista?",
                "Quitar",
                () -> viewModel.quitarCancionDeLista(listaId, cancion.getRutaArchivo())
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}