package es.iesagora.fd_pdplayer;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.databinding.ActivityMainBinding;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorNotificacion;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class MainActivity extends AppCompatActivity implements ReproductorApp.Listener {

    ActivityMainBinding binding;
    private NavController navController;

    private final ReproductorApp reproductorApp = ReproductorApp.getInstance();

    private ReproductorNotificacion reproductorNotificacion;

    private Cancion miniCancionActual;
    private String rutaMiniPintada;

    private ActivityResultLauncher<String> permisoNotificacionesLauncher;

    private boolean permisosInicialesTerminados = false;
    private final ArrayList<Runnable> callbacksPermisosIniciales = new ArrayList<>();

    private final ExecutorService miniImagenExecutor = Executors.newSingleThreadExecutor();
    private LruCache<String, Bitmap> miniImagenCache;
    private int versionCargaImagenMini = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView((binding = ActivityMainBinding.inflate(getLayoutInflater())).getRoot());

        inicializarCacheImagenes();
        prepararPermisoNotificaciones();

        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        navController = ((NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment))
                .getNavController();

        reproductorNotificacion = new ReproductorNotificacion(this);
        reproductorNotificacion.crearCanal();

        pedirPermisoNotificacionesSiHaceFalta();

        binding.btnBackToolbar.setImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material);

        binding.btnBackToolbar.setOnClickListener(v -> navController.navigateUp());

        binding.cardMiniPlayer.setOnClickListener(v -> abrirReproductorActual());

        binding.btnMiniPlayPause.setOnClickListener(v -> reproductorApp.toggle());

        binding.btnMiniCerrar.setOnClickListener(v -> {
            reproductorApp.liberar();
            miniCancionActual = null;
            rutaMiniPintada = null;
            versionCargaImagenMini++;

            if (reproductorNotificacion != null) {
                reproductorNotificacion.cancelar();
            }

            actualizarVisibilidadMiniPlayer();
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean mostrarFlecha = destination.getId() != R.id.principalFragment;

            binding.btnBackToolbar.setVisibility(mostrarFlecha ? View.VISIBLE : View.INVISIBLE);

            actualizarVisibilidadMiniPlayer();
        });

        reproductorApp.addListener(this);

        if (reproductorNotificacion != null) {
            reproductorApp.addListener(reproductorNotificacion);
        }
    }

    private void inicializarCacheImagenes() {
        int memoriaMaximaKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int tamanoCacheKb = memoriaMaximaKb / 16;

        miniImagenCache = new LruCache<String, Bitmap>(tamanoCacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    private void prepararPermisoNotificaciones() {
        permisoNotificacionesLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> marcarPermisosInicialesTerminados()
        );
    }

    private void pedirPermisoNotificacionesSiHaceFalta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            marcarPermisosInicialesTerminados();
            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);

        } else {
            marcarPermisosInicialesTerminados();
        }
    }

    public void ejecutarCuandoPermisosInicialesTerminen(Runnable callback) {
        if (callback == null) return;

        if (permisosInicialesTerminados) {
            callback.run();
        } else {
            callbacksPermisosIniciales.add(callback);
        }
    }

    private void marcarPermisosInicialesTerminados() {
        if (permisosInicialesTerminados) return;

        permisosInicialesTerminados = true;

        for (Runnable callback : callbacksPermisosIniciales) {
            if (callback != null) {
                callback.run();
            }
        }

        callbacksPermisosIniciales.clear();
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
    public void onReproductorActualizado(Cancion cancionActual,
                                         ArrayList<Cancion> listaCanciones,
                                         int posicionLista,
                                         boolean preparada,
                                         boolean reproduciendo,
                                         int progresoMs,
                                         int duracionMs,
                                         int modoReproduccion,
                                         boolean modoLista) {
        miniCancionActual = cancionActual;

        if (binding == null) return;

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
        if (binding == null) return;

        final int versionActual = ++versionCargaImagenMini;

        if (TextUtils.isEmpty(ruta)) {
            binding.ivMiniPlayerImagen.setImageResource(R.drawable.imagenotfound);
            return;
        }

        Bitmap cacheada = miniImagenCache.get(ruta);

        if (cacheada != null) {
            binding.ivMiniPlayerImagen.setImageBitmap(cacheada);
            return;
        }

        binding.ivMiniPlayerImagen.setImageResource(R.drawable.imagenotfound);

        miniImagenExecutor.execute(() -> {
            Bitmap bitmap = obtenerImagenDesdeArchivoReducida(
                    ruta,
                    dp(72),
                    dp(72)
            );

            if (bitmap != null) {
                miniImagenCache.put(ruta, bitmap);
            }

            runOnUiThread(() -> {
                if (binding == null) return;
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

    private void actualizarVisibilidadMiniPlayer() {
        if (binding == null || navController == null) return;

        boolean hayCancion = miniCancionActual != null;
        boolean pantallaReproductor = false;

        if (navController.getCurrentDestination() != null) {
            int destinoActual = navController.getCurrentDestination().getId();

            pantallaReproductor = destinoActual == R.id.cancionFragment
                    || destinoActual == R.id.cancionEnListaFragment;
        }

        binding.cardMiniPlayer.setVisibility(
                hayCancion && !pantallaReproductor ? View.VISIBLE : View.GONE
        );
    }

    private Bitmap obtenerImagenDesdeArchivoReducida(String ruta, int anchoDeseado, int altoDeseado) {
        if (TextUtils.isEmpty(ruta)) return null;

        MediaMetadataRetriever mmr = null;

        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);

            byte[] art = mmr.getEmbeddedPicture();

            if (art == null) {
                return null;
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(art, 0, art.length, bounds);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcularInSampleSize(bounds, anchoDeseado, altoDeseado);
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeByteArray(art, 0, art.length, options);

        } catch (Exception ignored) {
            return null;

        } finally {
            try {
                if (mmr != null) {
                    mmr.release();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private int calcularInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return Math.max(1, inSampleSize);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        reproductorApp.removeListener(this);

        if (reproductorNotificacion != null) {
            reproductorApp.removeListener(reproductorNotificacion);
        }

        if (isFinishing()) {
            reproductorApp.liberar();

            if (reproductorNotificacion != null) {
                reproductorNotificacion.cancelar();
            }
        }

        if (reproductorNotificacion != null) {
            reproductorNotificacion.liberar();
        }

        miniImagenExecutor.shutdownNow();

        if (miniImagenCache != null) {
            miniImagenCache.evictAll();
        }

        callbacksPermisosIniciales.clear();

        binding = null;
    }
}