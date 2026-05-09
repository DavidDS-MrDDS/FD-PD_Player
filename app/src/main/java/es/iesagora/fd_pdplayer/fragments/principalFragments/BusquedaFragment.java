package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.Manifest;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionRepository;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.ApiClient;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteListResponse;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoritesApiService;
import es.iesagora.fd_pdplayer.databinding.FragmentBusquedaBinding;
import es.iesagora.fd_pdplayer.funcionamiento.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.FavoritosRemotosAdapter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BusquedaFragment extends Fragment {

    private FragmentBusquedaBinding binding;
    private FavoritosRemotosAdapter adapter;
    private FavoritesApiService apiService;
    private SessionRepository sessionRepository;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private static final int PAGE = 1;
    private static final int LIMIT = 100;

    private static final String CANAL_DESCARGAS_ID = "canal_descargas_fdpd";
    private static final int NOTIFICACION_DESCARGA_ID = 3001;

    private ActivityResultLauncher<String> permisoNotificacionesLauncher;
    private FavoriteItem descargaPendiente;

    public BusquedaFragment() {
        super(R.layout.fragment_busqueda);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permisoNotificacionesLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (descargaPendiente != null) {
                        FavoriteItem item = descargaPendiente;
                        descargaPendiente = null;

                        if (!isGranted && isAdded() && binding != null) {
                            VentanasApp.mostrarMensaje(
                                    binding.getRoot(),
                                    "No se permitió mostrar notificaciones, pero la descarga se iniciará"
                            );
                        }

                        descargarCancion(item);
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentBusquedaBinding.bind(view);

        crearCanalNotificaciones();

        apiService = ApiClient.getFavoritesApiService();
        sessionRepository = new SessionRepository(requireActivity().getApplication());

        adapter = new FavoritosRemotosAdapter(true, false, new FavoritosRemotosAdapter.Listener() {
            @Override
            public void onClick(FavoriteItem item) {
                mostrarDialogoDescarga(item);
            }

            @Override
            public void onDelete(FavoriteItem item) {
            }
        });

        binding.recyclerBusqueda.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBusqueda.setAdapter(adapter);

        binding.btnRecargarBusqueda.setOnClickListener(v -> cargarFavoritosPublicos());

        binding.etBuscadorBusqueda.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> cargarFavoritosPublicos();
                searchHandler.postDelayed(searchRunnable, 500);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

    }

    private void cargarFavoritosPublicos() {
        SessionEntity session = sessionRepository.getSessionSync();

        if (session == null || TextUtils.isEmpty(session.token)) {
            mostrarMensajeLogin();
            return;
        }

        mostrarLista();

        String textoBusqueda = binding.etBuscadorBusqueda.getText().toString().trim();

        if (textoBusqueda.isEmpty()) {
            textoBusqueda = null;
        }

        apiService.getPublicFavorites("Bearer " + session.token, textoBusqueda, PAGE, LIMIT)
                .enqueue(new Callback<FavoriteListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<FavoriteListResponse> call,
                                           @NonNull Response<FavoriteListResponse> response) {

                        if (binding == null) return;

                        if (!response.isSuccessful()) {
                            VentanasApp.mostrarMensaje(
                                    binding.getRoot(),
                                    "No se pudieron cargar los favoritos públicos"
                            );
                            return;
                        }

                        if (response.body() == null || response.body().getSongs() == null) {
                            mostrarMensajeSinCanciones("No hay canciones disponibles");
                            return;
                        }

                        List<FavoriteItem> items = response.body().getSongs();

                        binding.tvTituloBusqueda.setText("Favoritos públicos (" + items.size() + ")");
                        adapter.setItems(items);

                        if (items.isEmpty()) {
                            String busquedaActual = binding.etBuscadorBusqueda.getText().toString().trim();

                            if (busquedaActual.isEmpty()) {
                                mostrarMensajeSinCanciones("No hay canciones disponibles");
                            } else {
                                mostrarMensajeSinCanciones("No se encontraron canciones con esa búsqueda");
                            }
                        } else {
                            binding.recyclerBusqueda.setVisibility(View.VISIBLE);
                            binding.tvMensajeBusqueda.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FavoriteListResponse> call, @NonNull Throwable t) {
                        if (binding == null) return;

                        VentanasApp.mostrarMensaje(
                                binding.getRoot(),
                                "Error de red: " + t.getMessage()
                        );
                    }
                });
    }

    private void mostrarMensajeLogin() {
        binding.tvTituloBusqueda.setText("Favoritos públicos");

        binding.layoutControlesBusqueda.setVisibility(View.GONE);
        binding.recyclerBusqueda.setVisibility(View.GONE);

        binding.tvMensajeBusqueda.setVisibility(View.VISIBLE);
        binding.tvMensajeBusqueda.setText("inicia sesión para poder ver la lista de canciones favoritas públicas");
    }

    private void mostrarLista() {
        binding.layoutControlesBusqueda.setVisibility(View.VISIBLE);
        binding.recyclerBusqueda.setVisibility(View.VISIBLE);
        binding.tvMensajeBusqueda.setVisibility(View.GONE);
    }

    private void mostrarMensajeSinCanciones(String mensaje) {
        binding.recyclerBusqueda.setVisibility(View.GONE);
        binding.tvMensajeBusqueda.setVisibility(View.VISIBLE);
        binding.tvMensajeBusqueda.setText(mensaje);
    }

    private void mostrarDialogoDescarga(FavoriteItem item) {
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "DescargarCancion",
                "Descargar canción",
                "¿Quieres descargar \"" + safe(item.getNombre()) + "\"?",
                "Descargar",
                () -> iniciarDescargaConPermiso(item)
        );
    }

    private void iniciarDescargaConPermiso(FavoriteItem item) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            descargaPendiente = item;
            permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }

        descargarCancion(item);
    }

    private void descargarCancion(FavoriteItem item) {
        String url = item.getRutaArchivo();

        if (TextUtils.isEmpty(url)) {
            if (binding != null) {
                VentanasApp.mostrarMensaje(
                        binding.getRoot(),
                        "Esta canción no tiene una URL válida para descargar"
                );
            }
            return;
        }

        try {
            String nombreArchivo = crearNombreArchivo(item);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(nombreArchivo);
            request.setDescription("Descargando canción desde FD-PD Player...");
            request.setMimeType("audio/mpeg");

            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI |
                            DownloadManager.Request.NETWORK_MOBILE
            );

            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    nombreArchivo
            );

            DownloadManager downloadManager =
                    (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);

            if (downloadManager == null) {
                if (binding != null) {
                    VentanasApp.mostrarMensaje(binding.getRoot(), "No se pudo iniciar la descarga");
                }
                return;
            }

            downloadManager.enqueue(request);

            mostrarNotificacionDescargaIniciada(nombreArchivo);

            if (binding != null) {
                VentanasApp.mostrarMensaje(binding.getRoot(), "Descarga iniciada");
            }

        } catch (Exception e) {
            if (binding != null) {
                VentanasApp.mostrarMensaje(
                        binding.getRoot(),
                        "Error al descargar: " + e.getMessage()
                );
            }
        }
    }

    private void crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CANAL_DESCARGAS_ID,
                    "Descargas",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            channel.setDescription("Notificaciones de descargas de canciones");

            NotificationManager notificationManager =
                    (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void mostrarNotificacionDescargaIniciada(String nombreArchivo) {
        if (!isAdded()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CANAL_DESCARGAS_ID)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle("Descargando canción")
                .setContentText(nombreArchivo)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Descargando \"" + nombreArchivo + "\" desde FD-PD Player."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(
                NOTIFICACION_DESCARGA_ID + (int) (System.currentTimeMillis() % 1000),
                builder.build()
        );
    }

    private String crearNombreArchivo(FavoriteItem item) {
        String nombre = safe(item.getNombre());
        String artista = safe(item.getArtista());

        String nombreArchivo;

        if (!nombre.isEmpty() && !artista.isEmpty()) {
            nombreArchivo = artista + " - " + nombre;
        } else if (!nombre.isEmpty()) {
            nombreArchivo = nombre;
        } else {
            nombreArchivo = "cancion_" + System.currentTimeMillis();
        }

        nombreArchivo = nombreArchivo.replaceAll("[\\\\/:*?\"<>|]", "_");

        if (!nombreArchivo.toLowerCase().endsWith(".mp3")) {
            nombreArchivo += ".mp3";
        }

        return nombreArchivo;
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

    @Override
    public void onResume() {
        super.onResume();

        if (binding != null) {
            cargarFavoritosPublicos();
        }
    }
}