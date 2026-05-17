package es.iesagora.fd_pdplayer.funcionamiento.otros;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

public class VentanasApp {

    public interface OpcionCallback {
        void onClick(int posicion, String texto);
    }

    public interface TextoCallback {
        void onConfirmar(String texto);
    }

    private static final int COLOR_FONDO = 0xFF111425;
    private static final int COLOR_CARD = 0xFF1F1E2B;
    private static final int COLOR_INPUT = 0xFF292936;
    private static final int COLOR_TEXTO = 0xFFE3E0F2;
    private static final int COLOR_TEXTO_SECUNDARIO = 0xFFC4C7C9;
    private static final int COLOR_BOTON_CLARO = 0xFFE3E2E3;
    private static final int COLOR_BOTON_OSCURO = 0xFF343341;
    private static final int COLOR_AZUL = 0xFF7FA1FF;
    private static final int COLOR_ERROR = 0xFFFFB4AB;

    private VentanasApp() {
    }

    public static void mostrarMensaje(View view, String mensaje) {
        if (view == null || TextUtils.isEmpty(mensaje)) return;

        Snackbar snackbar = Snackbar.make(view, mensaje, Snackbar.LENGTH_SHORT);
        snackbar.setTextColor(COLOR_TEXTO);
        snackbar.setBackgroundTint(COLOR_CARD);
        snackbar.show();
    }

    public static void mostrarMenu(Context context,
                                   String origen,
                                   String titulo,
                                   String mensaje,
                                   String[] opciones,
                                   OpcionCallback callback) {
        if (context == null || opciones == null) return;

        BottomSheetDialog dialog = crearDialog(context);
        LinearLayout root = crearRoot(context);

        añadirTitulo(root, titulo, mensaje, obtenerColorOrigen(origen));

        for (int i = 0; i < opciones.length; i++) {
            int posicion = i;
            String texto = opciones[i];

            TextView row = crearFilaOpcion(context, texto, obtenerColorFila(texto));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) {
                    callback.onClick(posicion, texto);
                }
            });

            root.addView(row);
        }

        añadirEspacio(root, 8);

        dialog.setContentView(root);
        mostrarDialog(dialog);
    }

    public static void mostrarConfirmacion(Context context,
                                           String origen,
                                           String titulo,
                                           String mensaje,
                                           String textoConfirmar,
                                           Runnable onConfirmar) {
        if (context == null) return;

        BottomSheetDialog dialog = crearDialog(context);
        LinearLayout root = crearRoot(context);

        int colorOrigen = obtenerColorOrigen(origen);

        añadirTitulo(root, titulo, mensaje, colorOrigen);

        LinearLayout filaBotones = new LinearLayout(context);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaBotones.setPadding(0, dp(context, 18), 0, 0);

        MaterialButton btnCancelar = crearBoton(
                context,
                "Cancelar",
                COLOR_BOTON_OSCURO,
                COLOR_TEXTO
        );

        MaterialButton btnConfirmar = crearBoton(
                context,
                textoConfirmar,
                colorOrigen == COLOR_ERROR ? COLOR_ERROR : COLOR_BOTON_CLARO,
                COLOR_FONDO
        );

        LinearLayout.LayoutParams lpCancelar = new LinearLayout.LayoutParams(
                0,
                dp(context, 52),
                1
        );
        lpCancelar.setMarginEnd(dp(context, 8));

        LinearLayout.LayoutParams lpConfirmar = new LinearLayout.LayoutParams(
                0,
                dp(context, 52),
                1
        );
        lpConfirmar.setMarginStart(dp(context, 8));

        filaBotones.addView(btnCancelar, lpCancelar);
        filaBotones.addView(btnConfirmar, lpConfirmar);

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnConfirmar.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirmar != null) {
                onConfirmar.run();
            }
        });

        root.addView(filaBotones);

        dialog.setContentView(root);
        mostrarDialog(dialog);
    }

    public static void mostrarInput(Context context,
                                    String origen,
                                    String titulo,
                                    String mensaje,
                                    String hint,
                                    String textoConfirmar,
                                    TextoCallback callback) {
        if (context == null) return;

        BottomSheetDialog dialog = crearDialog(context);
        LinearLayout root = crearRoot(context);

        int colorOrigen = obtenerColorOrigen(origen);

        añadirTitulo(root, titulo, mensaje, colorOrigen);

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(hint);
        input.setTextColor(COLOR_TEXTO);
        input.setHintTextColor(0xFF8E9193);
        input.setTextSize(16);
        input.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        input.setBackground(crearFondo(COLOR_INPUT, dp(context, 16)));

        LinearLayout.LayoutParams lpInput = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 56)
        );
        lpInput.setMargins(0, dp(context, 18), 0, 0);
        root.addView(input, lpInput);

        LinearLayout filaBotones = new LinearLayout(context);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaBotones.setPadding(0, dp(context, 18), 0, 0);

        MaterialButton btnCancelar = crearBoton(
                context,
                "Cancelar",
                COLOR_BOTON_OSCURO,
                COLOR_TEXTO
        );

        MaterialButton btnCrear = crearBoton(
                context,
                textoConfirmar,
                COLOR_BOTON_CLARO,
                COLOR_FONDO
        );

        LinearLayout.LayoutParams lpCancelar = new LinearLayout.LayoutParams(
                0,
                dp(context, 52),
                1
        );
        lpCancelar.setMarginEnd(dp(context, 8));

        LinearLayout.LayoutParams lpCrear = new LinearLayout.LayoutParams(
                0,
                dp(context, 52),
                1
        );
        lpCrear.setMarginStart(dp(context, 8));

        filaBotones.addView(btnCancelar, lpCancelar);
        filaBotones.addView(btnCrear, lpCrear);

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnCrear.setOnClickListener(v -> {
            String texto = input.getText() != null ? input.getText().toString().trim() : "";

            if (TextUtils.isEmpty(texto)) {
                input.setError("Campo obligatorio");
                return;
            }

            dialog.dismiss();

            if (callback != null) {
                callback.onConfirmar(texto);
            }
        });

        root.addView(filaBotones);

        dialog.setContentView(root);
        mostrarDialog(dialog);

        input.requestFocus();
    }

    private static BottomSheetDialog crearDialog(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);

        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });

        return dialog;
    }

    private static void mostrarDialog(BottomSheetDialog dialog) {
        dialog.show();
    }

    private static LinearLayout crearRoot(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(context, 24),
                dp(context, 22),
                dp(context, 24),
                dp(context, 28)
        );

        root.setBackground(crearFondoPopupInferiorRecto(context));

        return root;
    }

    private static GradientDrawable crearFondoPopupInferiorRecto(Context context) {
        int radioSuperior = dp(context, 28);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_CARD);

        drawable.setCornerRadii(new float[]{
                radioSuperior, radioSuperior,
                radioSuperior, radioSuperior,
                0, 0,
                0, 0
        });

        return drawable;
    }

    private static void añadirTitulo(LinearLayout root, String titulo, String mensaje, int colorOrigen) {
        Context context = root.getContext();

        TextView tvTitulo = new TextView(context);
        tvTitulo.setText(titulo);
        tvTitulo.setTextColor(COLOR_TEXTO);
        tvTitulo.setTextSize(24);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setMaxLines(2);

        root.addView(tvTitulo);

        if (!TextUtils.isEmpty(mensaje)) {
            TextView tvMensaje = new TextView(context);
            tvMensaje.setText(mensaje);
            tvMensaje.setTextColor(COLOR_TEXTO_SECUNDARIO);
            tvMensaje.setTextSize(15);
            tvMensaje.setLineSpacing(dp(context, 2), 1f);
            tvMensaje.setPadding(0, dp(context, 8), 0, dp(context, 6));

            root.addView(tvMensaje);
        }

        View linea = new View(context);
        linea.setBackgroundColor(colorOrigen);

        LinearLayout.LayoutParams lpLinea = new LinearLayout.LayoutParams(
                dp(context, 48),
                dp(context, 3)
        );
        lpLinea.setMargins(0, dp(context, 12), 0, dp(context, 12));
        root.addView(linea, lpLinea);
    }

    private static TextView crearFilaOpcion(Context context, String texto, int colorTexto) {
        TextView row = new TextView(context);
        row.setText(texto);
        row.setTextColor(colorTexto);
        row.setTextSize(16);
        row.setTypeface(null, Typeface.BOLD);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(
                dp(context, 16),
                0,
                dp(context, 16),
                0
        );
        row.setBackground(crearFondo(COLOR_INPUT, dp(context, 18)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 56)
        );
        lp.setMargins(0, dp(context, 8), 0, 0);
        row.setLayoutParams(lp);

        return row;
    }

    private static MaterialButton crearBoton(Context context,
                                             String texto,
                                             int colorFondo,
                                             int colorTexto) {
        MaterialButton boton = new MaterialButton(context);
        boton.setText(texto);
        boton.setAllCaps(false);
        boton.setTextSize(15);
        boton.setTypeface(null, Typeface.BOLD);
        boton.setTextColor(colorTexto);
        boton.setBackgroundTintList(ColorStateList.valueOf(colorFondo));
        boton.setCornerRadius(dp(context, 26));
        boton.setInsetTop(0);
        boton.setInsetBottom(0);
        boton.setMinHeight(0);
        boton.setMinWidth(0);

        return boton;
    }

    private static void añadirEspacio(LinearLayout root, int dp) {
        View espacio = new View(root.getContext());
        root.addView(espacio, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(root.getContext(), dp)
        ));
    }

    private static GradientDrawable crearFondo(int color, int radio) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radio);
        return drawable;
    }

    private static int obtenerColorOrigen(String origen) {
        if (origen == null) return COLOR_AZUL;

        String o = origen.toLowerCase();

        if (o.contains("borrar") || o.contains("eliminar") || o.contains("quitar") || o.contains("ocultar")) {
            return COLOR_ERROR;
        }

        return COLOR_AZUL;
    }

    private static int obtenerColorFila(String texto) {
        String t = texto != null ? texto.toLowerCase() : "";

        if (t.contains("borrar") || t.contains("eliminar") || t.contains("quitar") || t.contains("ocultar")) {
            return COLOR_ERROR;
        }

        return COLOR_TEXTO;
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}