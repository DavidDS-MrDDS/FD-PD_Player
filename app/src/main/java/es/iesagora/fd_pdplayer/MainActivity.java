package es.iesagora.fd_pdplayer;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import java.util.ArrayList;

import es.iesagora.fd_pdplayer.databinding.ActivityMainBinding;
import es.iesagora.fd_pdplayer.funcionamiento.ReproductorApp;
import es.iesagora.fd_pdplayer.funcionamiento.ReproductorNotificacion;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class MainActivity extends AppCompatActivity implements ReproductorApp.Listener {

    ActivityMainBinding binding;
    private NavController navController;

    private final ReproductorApp reproductorApp = ReproductorApp.getInstance();

    private ReproductorNotificacion reproductorNotificacion;

    private Cancion miniCancionActual;
    private String rutaMiniPintada;

    private ActivityResultLauncher<String> permisoNotificacionesLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView((binding = ActivityMainBinding.inflate(getLayoutInflater())).getRoot());

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

    private void prepararPermisoNotificaciones() {
        permisoNotificacionesLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                }
        );
    }

    private void pedirPermisoNotificacionesSiHaceFalta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
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
            actualizarVisibilidadMiniPlayer();
            return;
        }

        binding.tvMiniPlayerNombre.setText(safe(cancionActual.getNombre()));

        String artista = safe(cancionActual.getArtista());

        if (TextUtils.isEmpty(artista)) {
            artista = "Artista desconocido";
        }

        binding.tvMiniPlayerArtista.setText(artista);

        if (!safe(cancionActual.getRutaArchivo()).equals(rutaMiniPintada)) {
            rutaMiniPintada = safe(cancionActual.getRutaArchivo());

            Bitmap bitmap = obtenerImagenDesdeArchivo(rutaMiniPintada);

            if (bitmap != null) {
                binding.ivMiniPlayerImagen.setImageBitmap(bitmap);
            } else {
                binding.ivMiniPlayerImagen.setImageResource(R.drawable.imagenotfound);
            }
        }

        binding.btnMiniPlayPause.setImageResource(
                reproduciendo ? R.drawable.ic_player_pause : R.drawable.ic_player_play
        );

        actualizarVisibilidadMiniPlayer();
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

    private Bitmap obtenerImagenDesdeArchivo(String ruta) {
        if (TextUtils.isEmpty(ruta)) return null;

        MediaMetadataRetriever mmr = null;

        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);

            byte[] art = mmr.getEmbeddedPicture();

            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }

        } catch (Exception ignored) {
        } finally {
            try {
                if (mmr != null) mmr.release();
            } catch (Exception ignored) {
            }
        }

        return null;
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

        binding = null;
    }
}