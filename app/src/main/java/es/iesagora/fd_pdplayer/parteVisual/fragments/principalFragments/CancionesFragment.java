package es.iesagora.fd_pdplayer.parteVisual.fragments.principalFragments;

import android.Manifest;
import android.content.res.ColorStateList;
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
import java.util.Set;

import es.iesagora.fd_pdplayer.MainActivity;
import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteUploadRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionesBinding;
import es.iesagora.fd_pdplayer.funcionamiento.controlBusqueda.OptimizarBuscador;
import es.iesagora.fd_pdplayer.funcionamiento.otros.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.CancionesAdapter;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.CancionesAccionesManager;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.CancionesCargaManager;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.OcultacionMultipleManager;
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

    private OcultacionMultipleManager ocultacionMultipleManager;

    // Pide el permiso de audio o almacenamiento según la versión de Android.
    private ActivityResultLauncher<String> permisoAudioLauncher;

    private int modoOrden = CancionesUtils.ORDEN_MAS_NUEVO;

    private static final int COLOR_ICONO_NORMAL = 0xFFE3E0F2;
    private static final int COLOR_ICONO_OCULTACION = 0xFFFFB4AB;
    private static final int COLOR_FONDO_OCULTACION = 0xFF4A2D35;

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
                        setCargandoCanciones(false);

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
        CancionesOcultasRepository cancionesOcultasRepository = new CancionesOcultasRepository(requireContext());

        accionesManager = new CancionesAccionesManager(
                this,
                listasViewModel,
                new FavoriteUploadRepository(requireActivity().getApplication()),
                cancionesOcultasRepository,
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

        ocultacionMultipleManager = new OcultacionMultipleManager(
                requireContext(),
                cancionesOcultasRepository,
                new OcultacionMultipleManager.Callback() {
                    @Override
                    public void onModoOcultacionCambiado(boolean activo) {
                        if (adapter != null) {
                            adapter.setModoOcultacion(activo);
                        }

                        actualizarBotonOcultacion();
                    }

                    @Override
                    public void onSeleccionCambiada(Set<String> rutasSeleccionadas) {
                        if (adapter != null) {
                            adapter.setCancionesSeleccionadas(rutasSeleccionadas);
                        }

                        actualizarBotonOcultacion();
                    }

                    @Override
                    public void onCancionesOcultadas(Set<String> rutasOcultadas, int total) {
                        quitarCancionesOcultadasDeListas(rutasOcultadas);

                        if (total == 1) {
                            mostrarMensaje("Canción ocultada");
                        } else {
                            mostrarMensaje("Canciones ocultadas");
                        }
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
        binding.btnOcultarCanciones.setOnClickListener(v -> {
            if (ocultacionMultipleManager != null) {
                ocultacionMultipleManager.alPulsarBotonOcultacion();
            }
        });

        // Abre el menú usando VentanasApp.
        binding.btnSort.setOnClickListener(v -> mostrarMenuOrdenCanciones());

        // Vuelve a cargar canciones.
        binding.btnRecargarCanciones.setOnClickListener(v -> cargarCanciones());

        // Vuelve a cargar canciones al arrastrar.
        binding.swipeRefreshCanciones.setOnRefreshListener(this::cargarCanciones);

        actualizarBotonOcultacion();
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

        // CancionesCargaManager carga canciones y devuelve el resultado.
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
                }
        );
    }

    private void actualizarDescripcionOrden() {
        if (binding != null) {
            binding.btnSort.setContentDescription(CancionesUtils.descripcionOrden(modoOrden));
        }
    }

    private void actualizarBotonOcultacion() {
        if (binding == null || ocultacionMultipleManager == null) return;

        if (ocultacionMultipleManager.estaActivo()) {
            int total = ocultacionMultipleManager.getCantidadSeleccionadas();

            binding.btnOcultarCanciones.setColorFilter(COLOR_ICONO_OCULTACION);
            binding.btnOcultarCanciones.setBackgroundTintList(
                    ColorStateList.valueOf(COLOR_FONDO_OCULTACION)
            );
            binding.btnOcultarCanciones.setContentDescription(
                    "Finalizar ocultación. Seleccionadas: " + total
            );
        } else {
            binding.btnOcultarCanciones.setColorFilter(COLOR_ICONO_NORMAL);
            binding.btnOcultarCanciones.setBackgroundTintList(null);
            binding.btnOcultarCanciones.setBackgroundResource(R.drawable.bg_busqueda_boton);
            binding.btnOcultarCanciones.setContentDescription("Ocultar varias canciones");
        }
    }

    private void quitarCancionesOcultadasDeListas(Set<String> rutasOcultadas) {
        if (rutasOcultadas == null || rutasOcultadas.isEmpty()) return;

        listaCancionesTodas = quitarRutas(listaCancionesTodas, rutasOcultadas);
        listaCanciones = quitarRutas(listaCanciones, rutasOcultadas);

        aplicarFiltroCanciones();
    }

    private List<Cancion> quitarRutas(List<Cancion> canciones, Set<String> rutasOcultadas) {
        List<Cancion> resultado = new ArrayList<>();

        if (canciones == null) {
            return resultado;
        }

        for (Cancion cancion : canciones) {
            if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
                continue;
            }

            if (!rutasOcultadas.contains(cancion.getRutaArchivo())) {
                resultado.add(cancion);
            }
        }

        return resultado;
    }

    private void setCargandoCanciones(boolean cargando) {
        if (binding == null) return;

        binding.btnRecargarCanciones.setEnabled(!cargando);
        binding.btnSort.setEnabled(!cargando);
        binding.btnOcultarCanciones.setEnabled(!cargando);

        binding.btnRecargarCanciones.setAlpha(cargando ? 0.55f : 1f);
        binding.btnSort.setAlpha(cargando ? 0.55f : 1f);
        binding.btnOcultarCanciones.setAlpha(cargando ? 0.55f : 1f);

        binding.swipeRefreshCanciones.setRefreshing(cargando);
    }

    private void mostrarMensaje(String mensaje) {
        if (binding != null && !TextUtils.isEmpty(mensaje)) {
            VentanasApp.mostrarMensaje(binding.getRoot(), mensaje);
        }
    }

    @Override
    public void onOpcionesCancion(View anchor, Cancion cancion) {
        // CancionesAdapter llama aquí cuando se pulsa el botón menú de una canción.
        if (ocultacionMultipleManager != null && ocultacionMultipleManager.estaActivo()) {
            ocultacionMultipleManager.alternarSeleccion(cancion);
            return;
        }

        if (accionesManager != null) {
            accionesManager.mostrarMenuCancion(cancion);
        }
    }

    @Override
    public void onClickCancion(Cancion cancion) {
        // CancionesAdapter llama aquí cuando se pulsa una canción.
        if (ocultacionMultipleManager != null && ocultacionMultipleManager.estaActivo()) {
            return;
        }

        if (accionesManager != null) {
            accionesManager.abrirCancion(cancion);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
        if (ocultacionMultipleManager != null) {
            ocultacionMultipleManager.liberar();
            ocultacionMultipleManager = null;
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