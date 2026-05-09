package es.iesagora.fd_pdplayer.fragments.internalFragments.itemFragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionEnListaBinding;
import es.iesagora.fd_pdplayer.funcionamiento.reproductorSegundoPlano.ReproductorApp;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionEnListaFragment extends Fragment implements ReproductorApp.Listener {

    private FragmentCancionEnListaBinding binding;

    private Cancion cancion;
    private ArrayList<Cancion> listaCanciones = new ArrayList<>();
    private int posicion = 0;

    private final ReproductorApp reproductorApp = ReproductorApp.getInstance();

    private String rutaPintada;
    private boolean usuarioMoviendoSeekBar = false;

    private final ExecutorService imagenExecutor = Executors.newSingleThreadExecutor();
    private LruCache<String, Bitmap> imagenCache;
    private int versionCargaImagen = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inicializarCacheImagenes();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCancionEnListaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recogerDatos();

        if (cancion == null) {
            Toast.makeText(requireContext(), "No se encontró la canción", Toast.LENGTH_SHORT).show();
            return;
        }

        prepararControles();

        reproductorApp.addListener(this);

        reproductorApp.reproducir(
                requireContext(),
                cancion,
                listaCanciones,
                posicion,
                true
        );
    }

    private void inicializarCacheImagenes() {
        int memoriaMaximaKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int tamanoCacheKb = memoriaMaximaKb / 16;

        imagenCache = new LruCache<String, Bitmap>(tamanoCacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    private void recogerDatos() {
        if (getArguments() == null) return;

        cancion = obtenerCancionArgumento(getArguments(), "cancion");
        listaCanciones = obtenerListaCancionesArgumento(getArguments(), "listaCanciones");
        posicion = getArguments().getInt("posicion", 0);

        if (listaCanciones == null) {
            listaCanciones = new ArrayList<>();
        }

        if (listaCanciones.isEmpty() && cancion != null) {
            listaCanciones.add(cancion);
            posicion = 0;
        }

        if (posicion < 0 || posicion >= listaCanciones.size()) {
            posicion = 0;
        }

        if (cancion == null && !listaCanciones.isEmpty()) {
            cancion = listaCanciones.get(posicion);
        }
    }

    private void prepararControles() {
        binding.btnPlayPause.setOnClickListener(v -> reproductorApp.toggle());

        binding.btnSiguiente.setOnClickListener(v -> reproductorApp.irSiguiente());

        binding.btnAnterior.setOnClickListener(v -> reproductorApp.irAnterior());

        binding.btnModoReproduccion.setOnClickListener(v -> reproductorApp.cambiarModoReproduccion());

        binding.seekBarProgreso.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                usuarioMoviendoSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                usuarioMoviendoSeekBar = false;
                reproductorApp.seekTo(seekBar.getProgress());
            }
        });
    }

    @Override
    public void onReproductorActualizado(Cancion cancionActual,
                                         ArrayList<Cancion> listaActual,
                                         int posicionLista,
                                         boolean preparada,
                                         boolean reproduciendo,
                                         int progresoMs,
                                         int duracionMs,
                                         int modoReproduccion,
                                         boolean modoLista) {
        if (binding == null || cancionActual == null) return;

        this.cancion = cancionActual;
        this.listaCanciones = listaActual != null ? listaActual : new ArrayList<>();
        this.posicion = posicionLista;

        if (!safe(cancionActual.getRutaArchivo()).equals(rutaPintada)) {
            pintarCancion(cancionActual);
        }

        binding.btnPlayPause.setIconResource(
                reproduciendo ? R.drawable.ic_player_pause : R.drawable.ic_player_play
        );

        if (preparada) {
            binding.seekBarProgreso.setMax(Math.max(duracionMs, 1));

            if (!usuarioMoviendoSeekBar) {
                binding.seekBarProgreso.setProgress(Math.max(progresoMs, 0));
            }

            binding.tvTiempoActual.setText(formatearTiempo(progresoMs));
            binding.tvTiempoFinal.setText(formatearTiempo(duracionMs));
        } else {
            binding.seekBarProgreso.setProgress(0);
            binding.tvTiempoActual.setText("0:00");
            binding.tvTiempoFinal.setText("--:--");
        }

        actualizarBotonModoReproduccion(modoReproduccion);
    }

    private void pintarCancion(Cancion cancion) {
        if (binding == null || cancion == null) return;

        rutaPintada = safe(cancion.getRutaArchivo());

        binding.tvNombreCancion.setText(safe(cancion.getNombre()));

        String album = safe(cancion.getAlbum());
        String artista = safe(cancion.getArtista());

        String sub;

        if (!TextUtils.isEmpty(album) && !TextUtils.isEmpty(artista)) {
            sub = album + " • " + artista;
        } else if (!TextUtils.isEmpty(album)) {
            sub = album;
        } else {
            sub = artista;
        }

        binding.tvSubCancion.setText(sub);

        cargarImagenCancion(rutaPintada);
    }

    private void cargarImagenCancion(String ruta) {
        if (binding == null) return;

        final int versionActual = ++versionCargaImagen;

        if (TextUtils.isEmpty(ruta)) {
            binding.ivCaratula.setImageResource(R.drawable.imagenotfound);
            return;
        }

        Bitmap bitmapCacheado = imagenCache != null ? imagenCache.get(ruta) : null;

        if (bitmapCacheado != null) {
            binding.ivCaratula.setImageBitmap(bitmapCacheado);
            return;
        }

        binding.ivCaratula.setImageResource(R.drawable.imagenotfound);

        imagenExecutor.execute(() -> {
            Bitmap bitmap = obtenerImagenDesdeArchivoReducida(
                    ruta,
                    dp(900),
                    dp(900)
            );

            if (bitmap != null && imagenCache != null) {
                imagenCache.put(ruta, bitmap);
            }

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (versionActual != versionCargaImagen) return;
                if (!ruta.equals(rutaPintada)) return;

                if (bitmap != null) {
                    binding.ivCaratula.setImageBitmap(bitmap);
                } else {
                    binding.ivCaratula.setImageResource(R.drawable.imagenotfound);
                }
            });
        });
    }

    private void actualizarBotonModoReproduccion(int modoReproduccion) {
        if (binding == null) return;

        if (modoReproduccion == ReproductorApp.MODO_NORMAL) {
            binding.btnModoReproduccion.setImageResource(R.drawable.ic_repeat);
            binding.btnModoReproduccion.setColorFilter(0xFFCCC8C5);
            binding.btnModoReproduccion.setContentDescription("Reproducción normal");
        } else if (modoReproduccion == ReproductorApp.MODO_REPETIR_UNA) {
            binding.btnModoReproduccion.setImageResource(R.drawable.ic_repeat_one);
            binding.btnModoReproduccion.setColorFilter(0xFFE3E0F2);
            binding.btnModoReproduccion.setContentDescription("Repetir canción");
        } else {
            binding.btnModoReproduccion.setImageResource(R.drawable.ic_shuffle);
            binding.btnModoReproduccion.setColorFilter(0xFFC0C4E7);
            binding.btnModoReproduccion.setContentDescription("Reproducción mixta");
        }
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

        if (height <= 0 || width <= 0) {
            return 1;
        }

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

    private String formatearTiempo(int ms) {
        int s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("deprecation")
    private Cancion obtenerCancionArgumento(Bundle args, String key) {
        if (args == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return args.getSerializable(key, Cancion.class);
        } else {
            return (Cancion) args.getSerializable(key);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private ArrayList<Cancion> obtenerListaCancionesArgumento(Bundle args, String key) {
        if (args == null) {
            return new ArrayList<>();
        }

        Object valor;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            valor = args.getSerializable(key, ArrayList.class);
        } else {
            valor = args.getSerializable(key);
        }

        if (valor instanceof ArrayList<?>) {
            return (ArrayList<Cancion>) valor;
        }

        return new ArrayList<>();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        versionCargaImagen++;
        reproductorApp.removeListener(this);

        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        imagenExecutor.shutdownNow();

        if (imagenCache != null) {
            imagenCache.evictAll();
        }
    }
}