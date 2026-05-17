package es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.ActivityMainBinding;
import es.iesagora.fd_pdplayer.funcionamiento.controlCanciones.ImagenCancionUtils;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class MiniPlayer implements ReproductorApp.Listener {

    private final AppCompatActivity activity;
    private ActivityMainBinding binding;
    private final NavController navController;
    private final ReproductorApp reproductorApp;
    private final ReproductorNotificacion reproductorNotificacion;

    private Cancion miniCancionActual;
    private String rutaMiniPintada;

    private final ExecutorService miniImagenExecutor = Executors.newSingleThreadExecutor();
    private LruCache<String, Bitmap> miniImagenCache;
    private int versionCargaImagenMini = 0;

    private boolean iniciado = false;
    private boolean liberado = false;

    private NavController.OnDestinationChangedListener destinationChangedListener;

    public MiniPlayer(
            AppCompatActivity activity,
            ActivityMainBinding binding,
            NavController navController,
            ReproductorApp reproductorApp,
            ReproductorNotificacion reproductorNotificacion
    ) {
        this.activity = activity;
        this.binding = binding;
        this.navController = navController;
        this.reproductorApp = reproductorApp;
        this.reproductorNotificacion = reproductorNotificacion;

        inicializarCacheImagenes();
    }

    public void iniciar() {
        if (iniciado || binding == null || navController == null || reproductorApp == null) {
            return;
        }

        iniciado = true;
        liberado = false;

        binding.cardMiniPlayer.setOnClickListener(v -> abrirReproductorActual());
        binding.btnMiniPlayPause.setOnClickListener(v -> reproductorApp.toggle());
        binding.btnMiniCerrar.setOnClickListener(v -> detenerReproduccion());

        destinationChangedListener = (controller, destination, arguments) -> actualizarVisibilidadMiniPlayer();
        navController.addOnDestinationChangedListener(destinationChangedListener);

        reproductorApp.addListener(this);
    }

    private void inicializarCacheImagenes() {
        miniImagenCache = ImagenCancionUtils.crearCacheImagenes(16);
    }

    private void detenerReproduccion() {
        reproductorApp.liberar();

        miniCancionActual = null;
        rutaMiniPintada = null;
        versionCargaImagenMini++;

        if (reproductorNotificacion != null) {
            reproductorNotificacion.cancelar();
        }

        actualizarVisibilidadMiniPlayer();
    }

    private void abrirReproductorActual() {
        Cancion cancion = reproductorApp.getCancionActual();

        if (cancion == null || navController == null) {
            return;
        }

        if (navController.getCurrentDestination() != null) {
            int destinoActual = navController.getCurrentDestination().getId();

            if (destinoActual == R.id.cancionFragment
                    || destinoActual == R.id.cancionEnListaFragment) {
                return;
            }
        }

        Bundle bundle = new Bundle();
        bundle.putSerializable("cancion", cancion);
        bundle.putSerializable("listaCanciones", reproductorApp.getListaCanciones());
        bundle.putInt("posicion", reproductorApp.getPosicionLista());

        if (reproductorApp.isModoLista()) {
            navController.navigate(R.id.cancionEnListaFragment, bundle);
        } else {
            navController.navigate(R.id.cancionFragment, bundle);
        }
    }

    @Override
    public void onReproductorActualizado(
            Cancion cancionActual,
            ArrayList<Cancion> listaCanciones,
            int posicionLista,
            boolean preparada,
            boolean reproduciendo,
            int progresoMs,
            int duracionMs,
            int modoReproduccion,
            boolean modoLista
    ) {
        if (liberado || binding == null) return;

        miniCancionActual = cancionActual;

        if (cancionActual == null) {
            rutaMiniPintada = null;
            versionCargaImagenMini++;
            actualizarVisibilidadMiniPlayer();
            return;
        }

        binding.tvMiniPlayerNombre.setText(safe(cancionActual.getNombre()));

        String artista = safe(cancionActual.getArtista());
        if (TextUtils.isEmpty(artista)) {
            artista = "Artista desconocido";
        }

        binding.tvMiniPlayerArtista.setText(artista);

        String rutaActual = safe(cancionActual.getRutaArchivo());

        if (!rutaActual.equals(rutaMiniPintada)) {
            rutaMiniPintada = rutaActual;
            cargarImagenMiniPlayer(rutaActual);
        }

        binding.btnMiniPlayPause.setImageResource(
                reproduciendo ? R.drawable.ic_player_pause : R.drawable.ic_player_play
        );

        actualizarVisibilidadMiniPlayer();
    }

    private void cargarImagenMiniPlayer(String ruta) {
        if (binding == null || liberado) return;

        final int versionActual = ++versionCargaImagenMini;

        if (TextUtils.isEmpty(ruta)) {
            binding.ivMiniPlayerImagen.setImageResource(R.drawable.imagenotfound);
            return;
        }

        Bitmap cacheada = miniImagenCache != null ? miniImagenCache.get(ruta) : null;

        if (cacheada != null) {
            binding.ivMiniPlayerImagen.setImageBitmap(cacheada);
            return;
        }

        binding.ivMiniPlayerImagen.setImageResource(R.drawable.imagenotfound);

        miniImagenExecutor.execute(() -> {
            Bitmap bitmap = ImagenCancionUtils.obtenerImagenDesdeArchivoReducida(
                    ruta,
                    dp(72),
                    dp(72)
            );

            if (bitmap != null && miniImagenCache != null) {
                miniImagenCache.put(ruta, bitmap);
            }

            activity.runOnUiThread(() -> {
                if (binding == null || liberado) return;
                if (versionActual != versionCargaImagenMini) return;
                if (!ruta.equals(rutaMiniPintada)) return;

                if (bitmap != null) {
                    binding.ivMiniPlayerImagen.setImageBitmap(bitmap);
                } else {
                    binding.ivMiniPlayerImagen.setImageResource(R.drawable.imagenotfound);
                }
            });
        });
    }

    public void actualizarVisibilidadMiniPlayer() {
        if (binding == null || navController == null || liberado) return;

        boolean hayCancion = miniCancionActual != null;
        boolean pantallaReproductor = false;

        if (navController.getCurrentDestination() != null) {
            int destinoActual = navController.getCurrentDestination().getId();

            pantallaReproductor =
                    destinoActual == R.id.cancionFragment
                            || destinoActual == R.id.cancionEnListaFragment;
        }

        binding.cardMiniPlayer.setVisibility(
                hayCancion && !pantallaReproductor ? View.VISIBLE : View.GONE
        );
    }

    private int dp(int value) {
        return ImagenCancionUtils.dp(activity, value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public void liberar() {
        liberado = true;

        if (reproductorApp != null) {
            reproductorApp.removeListener(this);
        }

        if (navController != null && destinationChangedListener != null) {
            navController.removeOnDestinationChangedListener(destinationChangedListener);
            destinationChangedListener = null;
        }

        miniImagenExecutor.shutdownNow();

        if (miniImagenCache != null) {
            miniImagenCache.evictAll();
            miniImagenCache = null;
        }

        miniCancionActual = null;
        rutaMiniPintada = null;
        binding = null;
    }
}