package es.iesagora.fd_pdplayer.fragments.internalFragments.itemFragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionBinding;
import es.iesagora.fd_pdplayer.funcionamiento.models.Cancion;

public class CancionFragment extends Fragment {

    private FragmentCancionBinding binding;

    private Cancion cancion;
    private ArrayList<Cancion> listaCanciones = new ArrayList<>();
    private int posicion = 0;

    private MediaPlayer mediaPlayer;
    private boolean preparada = false;

    private static final int MODO_NORMAL = 0;
    private static final int MODO_REPETIR_UNA = 1;
    private static final int MODO_MIXTA = 2;

    private int modoReproduccion = MODO_NORMAL;
    private final Random random = new Random();

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable actualizador = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer == null || !preparada || binding == null) return;

            int pos = mediaPlayer.getCurrentPosition();
            int dur = mediaPlayer.getDuration();

            binding.seekBarProgreso.setMax(dur);
            binding.seekBarProgreso.setProgress(pos);

            binding.tvTiempoActual.setText(formatearTiempo(pos));
            binding.tvTiempoFinal.setText(formatearTiempo(dur));

            if (mediaPlayer.isPlaying()) {
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCancionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recogerDatos();
        pintarCancion();
        prepararControles();
        actualizarBotonModoReproduccion();
        prepararReproductor();
    }

    private void recogerDatos() {
        if (getArguments() == null) return;

        cancion = (Cancion) getArguments().getSerializable("cancion");
        listaCanciones = (ArrayList<Cancion>) getArguments().getSerializable("listaCanciones");
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

    private void pintarCancion() {
        if (cancion == null) return;

        binding.tvNombreCancion.setText(cancion.getNombre());

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

        Bitmap bmp = obtenerCaratulaDesdeArchivo(cancion.getRutaArchivo());
        if (bmp != null) binding.ivCaratula.setImageBitmap(bmp);
        else binding.ivCaratula.setImageResource(R.drawable.imagenotfound);

        binding.btnPlayPause.setIconResource(R.drawable.ic_player_play);
        binding.seekBarProgreso.setProgress(0);
        binding.tvTiempoActual.setText("0:00");
        binding.tvTiempoFinal.setText("--:--");
    }

    private void prepararControles() {
        binding.btnPlayPause.setOnClickListener(v -> toggle());

        binding.btnSiguiente.setOnClickListener(v -> irSiguiente());
        binding.btnAnterior.setOnClickListener(v -> irAnterior());

        binding.btnModoReproduccion.setOnClickListener(v -> cambiarModoReproduccion());

        binding.seekBarProgreso.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void prepararReproductor() {
        liberar();

        if (cancion == null || TextUtils.isEmpty(cancion.getRutaArchivo())) {
            Toast.makeText(requireContext(), "No se encontró la canción", Toast.LENGTH_SHORT).show();
            return;
        }

        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(cancion.getRutaArchivo());

            mediaPlayer.setOnPreparedListener(mp -> {
                preparada = true;
                binding.tvTiempoFinal.setText(formatearTiempo(mp.getDuration()));
                mp.start();
                binding.btnPlayPause.setIconResource(R.drawable.ic_player_pause);
                handler.post(actualizador);
            });

            mediaPlayer.setOnCompletionListener(mp -> alTerminarCancion());

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggle() {
        if (mediaPlayer == null || !preparada) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            binding.btnPlayPause.setIconResource(R.drawable.ic_player_play);
        } else {
            mediaPlayer.start();
            binding.btnPlayPause.setIconResource(R.drawable.ic_player_pause);
            handler.post(actualizador);
        }
    }

    private void irSiguiente() {
        if (listaCanciones == null || listaCanciones.isEmpty()) return;

        if (modoReproduccion == MODO_MIXTA) {
            posicion = obtenerPosicionAleatoria();
        } else {
            posicion++;
            if (posicion >= listaCanciones.size()) posicion = 0;
        }

        cambiarCancion();
    }

    private void irAnterior() {
        if (listaCanciones == null || listaCanciones.isEmpty()) return;

        if (modoReproduccion == MODO_MIXTA) {
            posicion = obtenerPosicionAleatoria();
        } else {
            posicion--;
            if (posicion < 0) posicion = listaCanciones.size() - 1;
        }

        cambiarCancion();
    }

    private void cambiarCancion() {
        if (listaCanciones == null || listaCanciones.isEmpty()) return;
        if (posicion < 0 || posicion >= listaCanciones.size()) posicion = 0;

        cancion = listaCanciones.get(posicion);
        pintarCancion();
        prepararReproductor();
    }

    private void alTerminarCancion() {
        if (modoReproduccion == MODO_REPETIR_UNA) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
                binding.btnPlayPause.setIconResource(R.drawable.ic_player_pause);
                handler.post(actualizador);
            }
            return;
        }

        irSiguiente();
    }

    private void cambiarModoReproduccion() {
        modoReproduccion++;

        if (modoReproduccion > MODO_MIXTA) {
            modoReproduccion = MODO_NORMAL;
        }

        actualizarBotonModoReproduccion();
    }

    private void actualizarBotonModoReproduccion() {
        if (binding == null) return;

        if (modoReproduccion == MODO_NORMAL) {
            binding.btnModoReproduccion.setImageResource(R.drawable.ic_repeat);
            binding.btnModoReproduccion.setColorFilter(0xFFCCC8C5);
            binding.btnModoReproduccion.setContentDescription("Reproducción normal");
        } else if (modoReproduccion == MODO_REPETIR_UNA) {
            binding.btnModoReproduccion.setImageResource(R.drawable.ic_repeat_one);
            binding.btnModoReproduccion.setColorFilter(0xFFE3E0F2);
            binding.btnModoReproduccion.setContentDescription("Repetir canción");
        } else {
            binding.btnModoReproduccion.setImageResource(R.drawable.ic_shuffle);
            binding.btnModoReproduccion.setColorFilter(0xFFC0C4E7);
            binding.btnModoReproduccion.setContentDescription("Reproducción mixta");
        }
    }

    private int obtenerPosicionAleatoria() {
        if (listaCanciones == null || listaCanciones.isEmpty()) return 0;

        if (listaCanciones.size() == 1) {
            return 0;
        }

        int nuevaPosicion;
        do {
            nuevaPosicion = random.nextInt(listaCanciones.size());
        } while (nuevaPosicion == posicion);

        return nuevaPosicion;
    }

    private Bitmap obtenerCaratulaDesdeArchivo(String ruta) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ruta);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();

            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String formatearTiempo(int ms) {
        int s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private void liberar() {
        handler.removeCallbacks(actualizador);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        preparada = false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        liberar();
        binding = null;
    }
}