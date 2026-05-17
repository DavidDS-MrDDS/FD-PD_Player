package es.iesagora.fd_pdplayer.parteVisual.fragments.internalFragments.settingFragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentContactoBinding;
import es.iesagora.fd_pdplayer.funcionamiento.otros.VentanasApp;

public class ContactoFragment extends Fragment {

    private FragmentContactoBinding binding;

    private static final String CORREO_CONTACTO = "fdpdarmy@gmail.com";
    private static final String TELEFONO_VISIBLE = "607 40 55 78";
    private static final String TELEFONO_INTERNACIONAL = "+34607405578";

    public ContactoFragment() {
        super(R.layout.fragment_contacto);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentContactoBinding.bind(view);

        binding.tvCorreoContacto.setText(CORREO_CONTACTO);
        binding.tvTelefonoContacto.setText(TELEFONO_VISIBLE);

        binding.rowCorreoContacto.setOnClickListener(v -> abrirCorreo());

        binding.rowTelefonoContacto.setOnClickListener(v -> abrirOpcionesTelefono());
    }

    private void abrirCorreo() {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + CORREO_CONTACTO));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Contacto FD-PD Player");
            intent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Hola, quería contactar con el equipo de FD-PD Player."
            );

            startActivity(Intent.createChooser(intent, "Enviar correo con"));

        } catch (ActivityNotFoundException e) {
            mostrarMensaje("No se encontró ninguna aplicación de correo");
        }
    }

    private void abrirOpcionesTelefono() {
        try {
            Intent llamadaIntent = new Intent(Intent.ACTION_DIAL);
            llamadaIntent.setData(Uri.parse("tel:" + TELEFONO_INTERNACIONAL));

            ArrayList<Intent> opcionesExtra = new ArrayList<>();

            Intent whatsappIntent = crearIntentWhatsApp();
            if (puedeAbrirse(whatsappIntent)) {
                opcionesExtra.add(whatsappIntent);
            }

            Intent telegramIntent = crearIntentTelegram();
            if (puedeAbrirse(telegramIntent)) {
                opcionesExtra.add(telegramIntent);
            }

            Intent chooser = Intent.createChooser(llamadaIntent, "Contactar con");

            if (!opcionesExtra.isEmpty()) {
                chooser.putExtra(
                        Intent.EXTRA_INITIAL_INTENTS,
                        opcionesExtra.toArray(new Intent[0])
                );
            }

            startActivity(chooser);

        } catch (ActivityNotFoundException e) {
            mostrarMensaje("No se encontró ninguna aplicación para contactar");
        }
    }

    private Intent crearIntentWhatsApp() {
        String numeroWhatsApp = TELEFONO_INTERNACIONAL.replace("+", "");
        String mensaje = "Hola, quería contactar con el equipo de FD-PD Player.";

        Uri uri = Uri.parse(
                "https://wa.me/" + numeroWhatsApp + "?text=" + Uri.encode(mensaje)
        );

        return new Intent(Intent.ACTION_VIEW, uri);
    }

    private Intent crearIntentTelegram() {
        String texto = "Contacto FD-PD Player: " + TELEFONO_VISIBLE;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, texto);
        intent.setPackage("org.telegram.messenger");

        return intent;
    }

    private boolean puedeAbrirse(Intent intent) {
        if (intent == null || getContext() == null) return false;

        return intent.resolveActivity(requireContext().getPackageManager()) != null;
    }

    private void mostrarMensaje(String mensaje) {
        if (binding != null) {
            VentanasApp.mostrarMensaje(binding.getRoot(), mensaje);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}