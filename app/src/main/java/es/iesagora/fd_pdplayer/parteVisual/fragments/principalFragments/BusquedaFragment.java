package es.iesagora.fd_pdplayer.parteVisual.fragments.principalFragments;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.usuarioRoom.SessionRepository;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.ApiClient;
import es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Favorites.FavoriteItem;
import es.iesagora.fd_pdplayer.databinding.FragmentBusquedaBinding;
import es.iesagora.fd_pdplayer.funcionamiento.otros.VentanasApp;
import es.iesagora.fd_pdplayer.funcionamiento.adapters.FavoritosRemotosAdapter;
import es.iesagora.fd_pdplayer.funcionamiento.controlBusqueda.BusquedaFavoritosManager;

public class BusquedaFragment extends Fragment {

    private FragmentBusquedaBinding binding;

    private FavoritosRemotosAdapter adapter;
    private SessionRepository sessionRepository;

    private BusquedaFavoritosManager busquedaFavoritosManager;

    private String tokenActual = "";

    private TextWatcher buscadorWatcher;

    private final ExecutorService descargaExecutor = Executors.newSingleThreadExecutor();

    private static final String CANAL_DESCARGAS_ID = "canal_descargas_fdpd";
    private static final int NOTIFICACION_DESCARGA_ID = 3001;

    public BusquedaFragment() {
        super(R.layout.fragment_busqueda);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentBusquedaBinding.bind(view);

        crearCanalDescargas();

        sessionRepository = new SessionRepository(requireActivity().getApplication());
        busquedaFavoritosManager = new BusquedaFavoritosManager(ApiClient.getFavoritesApiService());

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

        // Recarga el fragmento volviendo a pedir la lista de canciones.
        binding.btnRecargarBusqueda.setOnClickListener(v -> cargarFavoritosPublicos());

        // Recarga el fragmento volviendo a pedir la lista de canciones.
        binding.swipeRefreshBusqueda.setOnRefreshListener(this::cargarFavoritosPublicos);

        configurarBuscador();
        observarSesion();
    }

    private void configurarBuscador() {
        buscadorWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cargarFavoritosPublicos();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        binding.etBuscadorBusqueda.addTextChangedListener(buscadorWatcher);
    }

    private void observarSesion() {
        sessionRepository.getSessionLive().observe(getViewLifecycleOwner(), this::actualizarSesion);
    }

    private void actualizarSesion(SessionEntity session) {
        if (binding == null) return;

        if (session == null || TextUtils.isEmpty(session.token)) {
            tokenActual = "";

            if (busquedaFavoritosManager != null) {
                busquedaFavoritosManager.limpiarMemoria();
            }

            if (adapter != null) {
                adapter.setItems(new ArrayList<>());
            }

            mostrarMensajeLogin();
            setRefrescando(false);
            return;
        }

        tokenActual = session.token;
        mostrarLista();

        String busquedaActual = obtenerBusquedaParaApi();

        // Si ya hay datos en memoria los reutiliza.
        if (busquedaFavoritosManager != null
                && busquedaFavoritosManager.hayMemoriaPara(tokenActual, busquedaActual)) {

            pintarFavoritos(
                    busquedaFavoritosManager.obtenerFavoritosMemoria(),
                    busquedaActual
            );

        } else {
            cargarFavoritosPublicos();
        }
    }

    private void cargarFavoritosPublicos() {
        if (binding == null || busquedaFavoritosManager == null) return;

        // Se necesita una sesión iniciada para acceder.
        if (TextUtils.isEmpty(tokenActual)) {
            mostrarMensajeLogin();
            setRefrescando(false);
            return;
        }

        mostrarLista();

        String textoBusqueda = obtenerBusquedaParaApi();

        setRefrescando(true);

        busquedaFavoritosManager.cargarFavoritosPublicos(
                tokenActual,
                textoBusqueda,
                new BusquedaFavoritosManager.BusquedaCallback() {
                    @Override
                    public void onFavoritosCargados(List<FavoriteItem> favoritos, String busquedaActual) {
                        if (binding == null) return;

                        setRefrescando(false);
                        pintarFavoritos(favoritos, busquedaActual);
                    }

                    @Override
                    public void onError(String mensaje) {
                        if (binding == null) return;

                        setRefrescando(false);

                        VentanasApp.mostrarMensaje(
                                binding.getRoot(),
                                mensaje
                        );
                    }
                }
        );
    }

    private void pintarFavoritos(List<FavoriteItem> items, String busquedaActual) {
        if (binding == null || adapter == null) return;

        List<FavoriteItem> listaSegura = items != null ? items : new ArrayList<>();

        binding.tvTituloBusqueda.setText("Favoritos públicos (" + listaSegura.size() + ")");
        adapter.setItems(listaSegura);

        if (listaSegura.isEmpty()) {
            if (TextUtils.isEmpty(busquedaActual)) {
                mostrarMensajeSinCanciones("No hay canciones disponibles");
            } else {
                mostrarMensajeSinCanciones("No se encontraron canciones con esa búsqueda");
            }
        } else {
            binding.recyclerBusqueda.setVisibility(View.VISIBLE);
            binding.tvMensajeBusqueda.setVisibility(View.GONE);
        }
    }

    private String obtenerBusquedaParaApi() {
        if (binding == null || binding.etBuscadorBusqueda.getText() == null) {
            return null;
        }

        String textoBusqueda = binding.etBuscadorBusqueda.getText().toString().trim();

        return textoBusqueda.isEmpty() ? null : textoBusqueda;
    }

    private void setRefrescando(boolean refrescando) {
        if (binding != null) {
            binding.swipeRefreshBusqueda.setRefreshing(refrescando);
        }
    }

    private void mostrarMensajeLogin() {
        if (binding == null) return;

        binding.tvTituloBusqueda.setText("Favoritos públicos");

        binding.layoutControlesBusqueda.setVisibility(View.GONE);
        binding.recyclerBusqueda.setVisibility(View.GONE);

        binding.tvMensajeBusqueda.setVisibility(View.VISIBLE);
        binding.tvMensajeBusqueda.setText("inicia sesión para poder ver la lista de canciones favoritas públicas");
    }

    private void mostrarLista() {
        if (binding == null) return;

        binding.layoutControlesBusqueda.setVisibility(View.VISIBLE);
        binding.recyclerBusqueda.setVisibility(View.VISIBLE);
        binding.tvMensajeBusqueda.setVisibility(View.GONE);
    }

    private void mostrarMensajeSinCanciones(String mensaje) {
        if (binding == null) return;

        binding.recyclerBusqueda.setVisibility(View.GONE);
        binding.tvMensajeBusqueda.setVisibility(View.VISIBLE);
        binding.tvMensajeBusqueda.setText(mensaje);
    }

    private void mostrarDialogoDescarga(FavoriteItem item) {
        // VentanasApp muestra una confirmación antes de iniciar la descarga.
        VentanasApp.mostrarConfirmacion(
                requireContext(),
                "DescargarCancion",
                "Descargar canción",
                "¿Quieres descargar \"" + safe(item.getNombre()) + "\"?",
                "Descargar",
                () -> descargarCancion(item)
        );
    }

    private void descargarCancion(FavoriteItem item) {
        if (item == null) return;

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

        String nombreArchivo = crearNombreArchivo(item);

        mostrarNotificacionDescargaIniciada(nombreArchivo);

        if (binding != null) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "Descarga iniciada");
        }

        descargaExecutor.execute(() -> {
            try {
                descargarArchivo(url, nombreArchivo);

                if (!isAdded()) return;

                requireActivity().runOnUiThread(() -> {
                    mostrarNotificacionDescargaTerminada(nombreArchivo);

                    if (binding != null) {
                        VentanasApp.mostrarMensaje(binding.getRoot(), "Descarga completada");
                    }
                });

            } catch (Exception e) {
                if (!isAdded()) return;

                requireActivity().runOnUiThread(() -> {
                    mostrarNotificacionDescargaError(nombreArchivo);

                    if (binding != null) {
                        VentanasApp.mostrarMensaje(
                                binding.getRoot(),
                                "Error al descargar: " + e.getMessage()
                        );
                    }
                });
            }
        });
    }

    private void descargarArchivo(String urlTexto, String nombreArchivo) throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(urlTexto);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "FD-PD Player");
            connection.connect();

            int codigo = connection.getResponseCode();

            if (codigo < 200 || codigo >= 300) {
                throw new Exception("respuesta HTTP " + codigo);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                descargarConMediaStore(connection, nombreArchivo);
            } else {
                descargarConArchivoPublico(connection, nombreArchivo);
            }

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void descargarConMediaStore(HttpURLConnection connection, String nombreArchivo) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, nombreArchivo);
        values.put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = requireContext().getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
        );

        if (uri == null) {
            throw new Exception("no se pudo crear el archivo");
        }

        try {
            try (InputStream input = connection.getInputStream();
                 OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {

                if (output == null) {
                    throw new Exception("no se pudo abrir la descarga");
                }

                copiarDatos(input, output);
            }

            ContentValues finalValues = new ContentValues();
            finalValues.put(MediaStore.Downloads.IS_PENDING, 0);

            requireContext().getContentResolver().update(
                    uri,
                    finalValues,
                    null,
                    null
            );

        } catch (Exception e) {
            requireContext().getContentResolver().delete(uri, null, null);
            throw e;
        }
    }

    private void descargarConArchivoPublico(HttpURLConnection connection, String nombreArchivo) throws Exception {
        File carpetaDescargas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        if (!carpetaDescargas.exists() && !carpetaDescargas.mkdirs()) {
            throw new Exception("no se pudo abrir la carpeta Descargas");
        }

        File archivo = new File(carpetaDescargas, nombreArchivo);

        try (InputStream input = connection.getInputStream();
             OutputStream output = new FileOutputStream(archivo)) {

            copiarDatos(input, output);
        }

        MediaScannerConnection.scanFile(
                requireContext().getApplicationContext(),
                new String[]{archivo.getAbsolutePath()},
                new String[]{"audio/mpeg"},
                null
        );
    }

    private void copiarDatos(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[16 * 1024];
        int leidos;

        while ((leidos = input.read(buffer)) != -1) {
            output.write(buffer, 0, leidos);
        }

        output.flush();
    }

    private void crearCanalDescargas() {
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
        NotificationManager notificationManager =
                (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CANAL_DESCARGAS_ID)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle("Descargando canción")
                .setContentText(nombreArchivo)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Descargando \"" + nombreArchivo + "\" desde FD-PD Player."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(
                NOTIFICACION_DESCARGA_ID,
                builder.build()
        );
    }

    private void mostrarNotificacionDescargaTerminada(String nombreArchivo) {
        NotificationManager notificationManager =
                (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CANAL_DESCARGAS_ID)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle("Descarga completada")
                .setContentText(nombreArchivo)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("La canción \"" + nombreArchivo + "\" se ha guardado en Descargas."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(
                NOTIFICACION_DESCARGA_ID,
                builder.build()
        );
    }

    private void mostrarNotificacionDescargaError(String nombreArchivo) {
        NotificationManager notificationManager =
                (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CANAL_DESCARGAS_ID)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle("Error en la descarga")
                .setContentText(nombreArchivo)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("No se pudo descargar \"" + nombreArchivo + "\"."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(
                NOTIFICACION_DESCARGA_ID,
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

        // Limpia caracteres que no se pueden usar en nombres de archivo.
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

        if (busquedaFavoritosManager != null) {
            busquedaFavoritosManager.cancelar();
            busquedaFavoritosManager = null;
        }

        if (buscadorWatcher != null && binding != null) {
            binding.etBuscadorBusqueda.removeTextChangedListener(buscadorWatcher);
            buscadorWatcher = null;
        }

        if (adapter != null) {
            adapter.liberar();
            adapter = null;
        }

        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        descargaExecutor.shutdownNow();
    }
}