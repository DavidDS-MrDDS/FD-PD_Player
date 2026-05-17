package es.iesagora.fd_pdplayer.parteVisual.fragments.internalFragments.settingFragments;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionOcultaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.cancionesOcultasRoom.CancionesOcultasRepository;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionesOcultasBinding;
import es.iesagora.fd_pdplayer.funcionamiento.otros.VentanasApp;
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

    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;

    private Cancion cancionPendienteBorrar;
    private Uri uriPendienteBorrar;
    private boolean reintentarBorradoAndroid10 = false;

    public CancionesOcultasFragment() {
        super(R.layout.fragment_canciones_ocultas);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deleteRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        continuarBorradoTrasPermiso();
                    } else {
                        limpiarBorradoPendiente();

                        if (binding != null) {
                            VentanasApp.mostrarMensaje(binding.getRoot(), "Borrado cancelado");
                        }
                    }
                }
        );
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

        cancionesOcultasRepository.obtenerOcultasLive()
                .observe(getViewLifecycleOwner(), this::mostrarOcultas);
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
        if (binding == null) return;

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
        if (cancion == null) return;

        cancionesOcultasRepository.desocultarCancion(cancion.getRutaArchivo());

        if (binding != null) {
            VentanasApp.mostrarMensaje(binding.getRoot(), "Canción desocultada");
        }
    }

    private void confirmarBorrarDelDispositivo(Cancion cancion) {
        if (cancion == null) return;

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
        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            mostrarMensaje("No se encontró la ruta del archivo");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pedirPermisoBorradoAndroid11(cancion);
            return;
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            borrarAndroid10(cancion);
            return;
        }

        borrarConRepository(cancion);
    }

    private void pedirPermisoBorradoAndroid11(Cancion cancion) {
        if (!isAdded() || binding == null) return;

        try {
            Uri uri = obtenerUriMediaStorePorRuta(requireContext(), cancion.getRutaArchivo());

            if (uri == null) {
                mostrarMensaje("No se pudo localizar la canción en MediaStore");
                return;
            }

            cancionPendienteBorrar = cancion;
            uriPendienteBorrar = uri;
            reintentarBorradoAndroid10 = false;

            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(uri);

            PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                    requireContext().getContentResolver(),
                    uris
            );

            IntentSenderRequest request = new IntentSenderRequest.Builder(
                    pendingIntent.getIntentSender()
            ).build();

            deleteRequestLauncher.launch(request);

        } catch (Exception e) {
            limpiarBorradoPendiente();
            mostrarMensaje("No se pudo pedir permiso para borrar la canción");
        }
    }

    private void borrarAndroid10(Cancion cancion) {
        if (!isAdded() || binding == null) return;

        try {
            Uri uri = obtenerUriMediaStorePorRuta(requireContext(), cancion.getRutaArchivo());

            if (uri == null) {
                borrarConRepository(cancion);
                return;
            }

            int borradas = requireContext().getContentResolver().delete(uri, null, null);

            if (borradas > 0) {
                finalizarBorradoCorrecto(cancion);
            } else {
                mostrarMensaje("No se pudo borrar la canción del dispositivo");
            }

        } catch (RecoverableSecurityException e) {
            try {
                cancionPendienteBorrar = cancion;
                uriPendienteBorrar = obtenerUriMediaStorePorRuta(requireContext(), cancion.getRutaArchivo());
                reintentarBorradoAndroid10 = true;

                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        e.getUserAction().getActionIntent().getIntentSender()
                ).build();

                deleteRequestLauncher.launch(request);

            } catch (Exception ex) {
                limpiarBorradoPendiente();
                mostrarMensaje("No se pudo pedir permiso para borrar la canción");
            }

        } catch (Exception e) {
            mostrarMensaje("No se pudo borrar la canción del dispositivo");
        }
    }

    private void continuarBorradoTrasPermiso() {
        if (cancionPendienteBorrar == null) {
            limpiarBorradoPendiente();
            return;
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && reintentarBorradoAndroid10) {
            reintentarBorradoAndroid10();
            return;
        }

        finalizarBorradoCorrecto(cancionPendienteBorrar);
    }

    private void reintentarBorradoAndroid10() {
        if (!isAdded() || binding == null || cancionPendienteBorrar == null || uriPendienteBorrar == null) {
            limpiarBorradoPendiente();
            return;
        }

        try {
            int borradas = requireContext().getContentResolver().delete(uriPendienteBorrar, null, null);

            if (borradas > 0 || !existeArchivo(cancionPendienteBorrar.getRutaArchivo())) {
                finalizarBorradoCorrecto(cancionPendienteBorrar);
            } else {
                mostrarMensaje("No se pudo borrar la canción del dispositivo");
                limpiarBorradoPendiente();
            }

        } catch (Exception e) {
            mostrarMensaje("No se pudo borrar la canción del dispositivo");
            limpiarBorradoPendiente();
        }
    }

    private void finalizarBorradoCorrecto(Cancion cancion) {
        if (cancion == null) return;

        String ruta = cancion.getRutaArchivo();

        cancionesOcultasRepository.desocultarCancion(ruta);

        if (!TextUtils.isEmpty(ruta) && isAdded()) {
            MediaScannerConnection.scanFile(
                    requireContext().getApplicationContext(),
                    new String[]{ruta},
                    null,
                    null
            );
        }

        mostrarMensaje("Canción borrada del dispositivo");
        limpiarBorradoPendiente();
    }

    private void borrarConRepository(Cancion cancion) {
        cancionesOcultasRepository.borrarCancionDelDispositivo(cancion, new CancionesOcultasRepository.SimpleCallback() {
            @Override
            public void onSuccess(String message) {
                if (!isAdded()) return;

                requireActivity().runOnUiThread(() -> mostrarMensaje(message));
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                requireActivity().runOnUiThread(() -> mostrarMensaje(message));
            }
        });
    }

    private Uri obtenerUriMediaStorePorRuta(Context context, String ruta) {
        if (context == null || TextUtils.isEmpty(ruta)) {
            return null;
        }

        Cursor cursor = null;

        try {
            String[] projection = {MediaStore.Audio.Media._ID};
            String selection = MediaStore.Audio.Media.DATA + " = ?";
            String[] args = {ruta};

            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

                return Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id)
                );
            }

            return null;

        } catch (Exception e) {
            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean existeArchivo(String ruta) {
        if (TextUtils.isEmpty(ruta)) {
            return false;
        }

        try {
            File archivo = new File(ruta);
            return archivo.exists();
        } catch (Exception e) {
            return false;
        }
    }

    private void limpiarBorradoPendiente() {
        cancionPendienteBorrar = null;
        uriPendienteBorrar = null;
        reintentarBorradoAndroid10 = false;
    }

    private void mostrarMensaje(String mensaje) {
        if (binding != null && !TextUtils.isEmpty(mensaje)) {
            VentanasApp.mostrarMensaje(binding.getRoot(), mensaje);
        }
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

        if (adapter != null) {
            adapter.liberar();
        }

        binding = null;
    }
}