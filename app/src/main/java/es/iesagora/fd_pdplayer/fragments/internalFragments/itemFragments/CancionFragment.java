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

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentCancionBinding;
import es.iesagora.fd_pdplayer.models.Cancion;

public class CancionFragment extends Fragment {

    private FragmentCancionBinding binding;

    private Cancion cancion;
    private ArrayList<Cancion> listaCanciones = new ArrayList<>();
    private int posicion = 0;

    private MediaPlayer mediaPlayer;
    private boolean preparada = false;

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
        prepararReproductor();
    }

    private void recogerDatos() {
        if (getArguments() == null) return;

        cancion = (Cancion) getArguments().getSerializable("cancion");
        listaCanciones = (ArrayList<Cancion>) getArguments().getSerializable("listaCanciones");
        posicion = getArguments().getInt("posicion", 0);
    }

    private void pintarCancion() {
        if (cancion == null) return;

        binding.tvNombreCancion.setText(cancion.getNombre());

        String sub = cancion.getAlbum() + " • " + cancion.getArtista();
        binding.tvSubCancion.setText(sub);

        Bitmap bmp = obtenerCaratulaDesdeArchivo(cancion.getRutaArchivo());
        if (bmp != null) binding.ivCaratula.setImageBitmap(bmp);
        else binding.ivCaratula.setImageResource(R.drawable.imagenotfound);

        binding.btnPlayPause.setText("▶");
    }

    private void prepararControles() {
        binding.btnPlayPause.setOnClickListener(v -> toggle());

        binding.btnSiguiente.setOnClickListener(v -> irSiguiente());
        binding.btnAnterior.setOnClickListener(v -> irAnterior());

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

        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(cancion.getRutaArchivo());

            mediaPlayer.setOnPreparedListener(mp -> {
                preparada = true;
                binding.tvTiempoFinal.setText(formatearTiempo(mp.getDuration()));
                mp.start();
                binding.btnPlayPause.setText("||");
                handler.post(actualizador);
            });

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggle() {
        if (mediaPlayer == null || !preparada) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            binding.btnPlayPause.setText("▶");
        } else {
            mediaPlayer.start();
            binding.btnPlayPause.setText("||");
            handler.post(actualizador);
        }
    }

    private void irSiguiente() {
        posicion++;
        if (posicion >= listaCanciones.size()) posicion = 0;
        cambiarCancion();
    }

    private void irAnterior() {
        posicion--;
        if (posicion < 0) posicion = listaCanciones.size() - 1;
        cambiarCancion();
    }

    private void cambiarCancion() {
        cancion = listaCanciones.get(posicion);
        pintarCancion();
        prepararReproductor();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        liberar();
        binding = null;
    }
}