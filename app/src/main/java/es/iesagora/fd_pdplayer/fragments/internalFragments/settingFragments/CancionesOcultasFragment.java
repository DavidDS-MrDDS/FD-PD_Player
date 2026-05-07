package es.iesagora.fd_pdplayer.fragments.internalFragments.settingFragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionOcultaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionesOcultasBinding;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.CancionesAdapter;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionesOcultasFragment extends Fragment {

    private FragmentCancionesOcultasBinding binding;
    private CancionesAdapter adapter;
    private CancionesOcultasRepository cancionesOcultasRepository;

    private List<Cancion> cancionesOcultasTodas = new ArrayList<>();
    private List<Cancion> cancionesOcultas = new ArrayList<>();

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public CancionesOcultasFragment() {
        super(R.layout.fragment_canciones_ocultas);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentCancionesOcultasBinding.bind(view);
        cancionesOcultasRepository = new CancionesOcultasRepository(requireContext());

        adapter = new CancionesAdapter(requireContext(), new ArrayList<>(), new CancionesAdapter.Listener() {
            @Override
            public void onOpcionesCancion(View anchor, Cancion cancion) {
                mostrarMenuCancionOculta(cancion);
            }

            @Override
            public void onClickCancion(Cancion cancion) {
                if (binding != null) {
                    VentanasApp.mostrarMensaje(binding.getRoot(), "Canción oculta");
                }
            }
        });

        binding.recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 1));
        binding.recyclerView.setAdapter(adapter);

        configurarBuscador();

        cancionesOcultasRepository.obtenerOcultasLive().observe(getViewLifecycleOwner(), this::mostrarOcultas);
    }

    private void configurarBuscador() {
        binding.btnRecargarOcultas.setOnClickListener(v -> {
            binding.etBuscadorOcultas.setText("");
            aplicarFiltroOcultas();
        });

        binding.etBuscadorOcultas.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> aplicarFiltroOcultas();
                searchHandler.postDelayed(searchRunnable, 250);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void mostrarOcultas(List<CancionOcultaEntity> ocultas) {
        cancionesOcultasTodas = new ArrayList<>();

        if (ocultas != null) {
            for (CancionOcultaEntity oculta : ocultas) {
                cancionesOcultasTodas.add(new Cancion(
                        oculta.getNombre(),
                        oculta.getArtista(),
                        oculta.getAlbum(),
                        oculta.getRutaArchivo()
                ));
            }
        }

        aplicarFiltroOcultas();
    }

    private void aplicarFiltroOcultas() {
        String busqueda = binding.etBuscadorOcultas.getText() != null
                ? binding.etBuscadorOcultas.getText().toString().trim()
                : "";

        cancionesOcultas = filtrarCanciones(cancionesOcultasTodas, busqueda);

        binding.tvCount.setText("Canciones ocultas [" + cancionesOcultas.size() + "]");
        adapter.establecerLista(cancionesOcultas);

        if (cancionesOcultas.isEmpty()) {
            binding.recyclerView.setVisibility(View.GONE);
            binding.tvMensajeOcultas.setVisibility(View.VISIBLE);

            if (TextUtils.isEmpty(busqueda)) {
                binding.tvMensajeOcultas.setText("No hay canciones ocultas");
            } else {
                binding.tvMensajeOcultas.setText("No se encontraron canciones ocultas con esa búsqueda");
            }
        } else {
            binding.recyclerView.setVisibility(View.VISIBLE);
            binding.tvMensajeOcultas.setVisibility(View.GONE);
        }
    }

    private List<Cancion> filtrarCanciones(List<Cancion> canciones, String texto) {
        List<Cancion> resultado = new ArrayList<>();

        if (canciones == null) {
            return resultado;
        }

        if (TextUtils.isEmpty(texto)) {
            resultado.addAll(canciones);
            return resultado;
        }

        String filtro = texto.toLowerCase();

        for (Cancion cancion : canciones) {
            String nombre = safe(cancion.getNombre()).toLowerCase();
            String artista = safe(cancion.getArtista()).toLowerCase();
            String album = safe(cancion.getAlbum()).toLowerCase();

            if (nombre.contains(filtro) || artista.contains(filtro) || album.contains(filtro)) {
                resultado.add(cancion);
            }
        }

        return resultado;
    }

    private void mostrarMenuCancionOculta(Cancion cancion) {
        String[] opciones = {
                "Desocultar canción",
                "Borrar del dispositivo"
        };

        VentanasApp.mostrarMenu(
                requireContext(),
                "MenuCancionOculta",
                "Canción oculta",
                safe(cancion.getNombre()),
                opciones,
                (posicion, texto) -> {
                    if (posicion == 0) {
                        desocultarCancion(cancion);
                        return;
                    }

                    if (posicion == 1) {
                        confirmarBorrarDelDispositivo(cancion);
                    }
                }
        );
    }

    private void desocultarCancion(Cancion cancion) {
        cancionesOcultasRepository.desocultarCancion(cancion.getRutaArchivo());

        if (binding != null) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "Canción desocultada");
        }
    }

    private void confirmarBorrarDelDispositivo(Cancion cancion) {
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "BorrarCancionOculta",
                "Borrar canción",
                "¿Quieres borrar \"" + safe(cancion.getNombre()) + "\" del dispositivo?\n\nEsta acción no se puede deshacer.",
                "Borrar",
                () -> borrarDelDispositivo(cancion)
        );
    }

    private void borrarDelDispositivo(Cancion cancion) {
        cancionesOcultasRepository.borrarCancionDelDispositivo(cancion, new CancionesOcultasRepository.SimpleCallback() {
            @Override
            public void onSuccess(String message) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        VentanasApp.mostrarMensaje(binding.getRoot(), message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        VentanasApp.mostrarMensaje(binding.getRoot(), message);
                    }
                });
            }
        });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }

        binding = null;
    }
}