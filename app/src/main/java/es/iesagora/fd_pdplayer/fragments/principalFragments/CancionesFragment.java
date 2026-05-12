package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.MainActivity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionesBinding;
import es.iesagora.fd_pdplayer.funcionamiento.OptimizarBuscador;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.CancionesAdapter;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.CancionesAccionesManager;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.CancionesCargaManager;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.CancionesUtils;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionesFragment extends Fragment implements CancionesAdapter.Listener {

    private FragmentCancionesBinding binding;

    // Se ocupa de cargar las canciones.
    private CancionesCargaManager cargaManager;

    private CancionesAdapter adapter;
    // Lista sin filtros.
    private List<Cancion> listaCancionesTodas = new ArrayList<>();
    // Lista con filtros.
    private List<Cancion> listaCanciones = new ArrayList<>();

    // Ralentiza un poco el buscador para no sobre ejecutar el filtro.
    private OptimizarBuscador optimizarBuscador;

    // Se ocupa de abrir canciones y sus opciones.
    private CancionesAccionesManager accionesManager;

    // Pide el permiso de audio o almacenamiento según la versión de Android.
    private ActivityResultLauncher<String> permisoAudioLauncher;

    private int modoOrden = CancionesUtils.ORDEN_MAS_NUEVO;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cargaManager = new CancionesCargaManager(requireContext());

        // Se ejecuta después de pedir el permiso de audio/almacenamiento.
        permisoAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isAdded() || binding == null) return;

                    if (isGranted) {
                        cargarCanciones();
                    } else {
                        Toast.makeText(
                                requireContext(),
                                "Permiso denegado, no se pueden mostrar canciones",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCancionesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        inicializarAcciones();
        configurarRecycler();
        configurarBotones();
        configurarBuscador();

        esperarPermisoNotificacionesYComprobarAlmacenamiento();
    }

    private void inicializarAcciones() {
        ListasViewModel listasViewModel = new ViewModelProvider(requireActivity()).get(ListasViewModel.class);
        accionesManager = new CancionesAccionesManager(
                this,
                listasViewModel,
                new FavoriteUploadRepository(requireActivity().getApplication()),
                new CancionesOcultasRepository(requireContext()),
                new CancionesAccionesManager.Callback() {
                    @Override
                    public void refrescarLista() {
                        aplicarFiltroCanciones();
                    }

                    @Override
                    public void mostrarMensaje(String mensaje) {
                        CancionesFragment.this.mostrarMensaje(mensaje);
                    }
                }
        );

        listasViewModel.obtenerListas().observe(getViewLifecycleOwner(), this::actualizarListas);
    }

    private void actualizarListas(List<ListaEntity> listas) {
        // Informa sobre las listas para poder añadir canciones a estas.
        if (accionesManager != null) {
            accionesManager.setListasActuales(listas);
        }
    }

    private void configurarRecycler() {
        adapter = new CancionesAdapter(requireContext(), new ArrayList<>(), this);
        binding.recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 1));
        binding.recyclerView.setAdapter(adapter);
    }

    private void configurarBotones() {
        // Abre el menú usando VentanasApp.
        binding.btnSort.setOnClickListener(v -> mostrarMenuOrdenCanciones());

        // Vuelve a cargar canciones.
        binding.btnRecargarCanciones.setOnClickListener(v -> cargarCanciones());
    }

    private void configurarBuscador() {
        optimizarBuscador = new OptimizarBuscador(250, this::aplicarFiltroCanciones);
        binding.etBuscadorCanciones.addTextChangedListener(optimizarBuscador);
    }

    private void esperarPermisoNotificacionesYComprobarAlmacenamiento() {
        if (!isAdded() || binding == null) return;

        if (requireActivity() instanceof MainActivity) {
            // MainActivity informa cuando el "Popup" del permiso de notificaciones ya no está.
            ((MainActivity) requireActivity()).ejecutarCuandoPermisosInicialesTerminen(
                    this::comprobarPermisoAudioYCargar
            );
        } else {
            comprobarPermisoAudioYCargar();
        }
    }

    private void comprobarPermisoAudioYCargar() {
        if (!isAdded() || binding == null) return;

        // En Android 13+ se pide permiso de audio,
        // en versiones anteriores se pide permiso de lectura de almacenamiento.
        String permiso = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(requireContext(), permiso)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cargarCanciones();
        } else {
            permisoAudioLauncher.launch(permiso);
        }
    }

    private void cargarCanciones() {
        if (binding == null || cargaManager == null) return;

        setCargandoCanciones(true);

        // CancionesCargaManager carga canciones en segundo plano y devuelve el resultado aquí.
        cargaManager.cargar(modoOrden, canciones -> {
            if (!isAdded() || binding == null) return;

            listaCancionesTodas = canciones != null ? canciones : new ArrayList<>();

            aplicarFiltroCanciones();
            actualizarDescripcionOrden();
            setCargandoCanciones(false);
        });
    }

    private void aplicarFiltroCanciones() {
        if (binding == null || adapter == null) return;

        String busqueda = obtenerTextoBuscador();
        // CancionesUtils filtra la lista.
        listaCanciones = CancionesUtils.filtrar(listaCancionesTodas, busqueda);

        adapter.establecerLista(listaCanciones);
        if (accionesManager != null) {
            accionesManager.setCanciones(listaCancionesTodas, listaCanciones);
        }
        pintarEstadoLista(busqueda);
    }

    private String obtenerTextoBuscador() {
        return binding.etBuscadorCanciones.getText() != null
                ? binding.etBuscadorCanciones.getText().toString().trim()
                : "";
    }

    private void pintarEstadoLista(String busqueda) {
        boolean vacia = listaCanciones.isEmpty();

        binding.tvCount.setText("Canciones disponibles [" + listaCanciones.size() + "]");
        binding.recyclerView.setVisibility(vacia ? View.GONE : View.VISIBLE);
        binding.tvMensajeCanciones.setVisibility(vacia ? View.VISIBLE : View.GONE);

        if (vacia) {
            binding.tvMensajeCanciones.setText(
                    TextUtils.isEmpty(busqueda)
                            ? "No hay canciones disponibles"
                            : "No se encontraron canciones con esa búsqueda"
            );
        }
    }

    private void mostrarMenuOrdenCanciones() {
        if (!isAdded() || binding == null) return;

        // VentanasApp muestra el menú.
        VentanasApp.mostrarMenu(
                requireContext(),
                "OrdenCanciones",
                "Ordenar canciones",
                "Elige cómo quieres ordenar la lista.",
                CancionesUtils.getOpcionesOrden(),
                (posicion, texto) -> {
                    modoOrden = posicion;
                    cargarCanciones();
                    mostrarMensaje(texto);
                }
        );
    }

    private void actualizarDescripcionOrden() {
        if (binding != null) {
            binding.btnSort.setContentDescription(CancionesUtils.descripcionOrden(modoOrden));
        }
    }

    private void setCargandoCanciones(boolean cargando) {
        if (binding == null) return;

        binding.btnRecargarCanciones.setEnabled(!cargando);
        binding.btnSort.setEnabled(!cargando);

        binding.btnRecargarCanciones.setAlpha(cargando ? 0.55f : 1f);
        binding.btnSort.setAlpha(cargando ? 0.55f : 1f);
    }

    private void mostrarMensaje(String mensaje) {
        if (binding != null && !TextUtils.isEmpty(mensaje)) {
            VentanasApp.mostrarMensaje(binding.getRoot(), mensaje);
        }
    }

    @Override
    public void onOpcionesCancion(View anchor, Cancion cancion) {
        // CancionesAdapter llama aquí cuando se pulsa el botón de opciones de una canción.
        if (accionesManager != null) {
            accionesManager.mostrarMenuCancion(cancion);
        }
    }

    @Override
    public void onClickCancion(Cancion cancion) {
        // CancionesAdapter llama aquí cuando se pulsa una canción.
        if (accionesManager != null) {
            accionesManager.abrirCancion(cancion);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancela cargas pendientes para evitar crasheos.
        if (cargaManager != null) {
            cargaManager.cancelarPendientes();
        }
        if (optimizarBuscador != null) {
            optimizarBuscador.cancelar();

            if (binding != null) {
                binding.etBuscadorCanciones.removeTextChangedListener(optimizarBuscador);
            }

            optimizarBuscador = null;
        }
        if (adapter != null) {
            adapter.liberar();
            adapter = null;
        }
        accionesManager = null;
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cargaManager != null) {
            cargaManager.liberar();
            cargaManager = null;
        }
    }
}