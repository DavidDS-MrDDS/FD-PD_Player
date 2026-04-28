package es.iesagora.fd_pdplayer.almacenamientoRemoto.accesoApi.Auth;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class AuthApiService {

    private static final String API_LOGIN = "https://fdpd-player.vercel.app/api/login";
    private static final String API_REGISTER = "https://fdpd-player.vercel.app/api/register";

    private final RequestQueue requestQueue;

    public AuthApiService(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public interface ApiCallback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    public void login(String email, String password, ApiCallback callback) {
        JSONObject jsonBody = new JSONObject();

        try {
            jsonBody.put("email", email);
            jsonBody.put("password", password);
        } catch (JSONException e) {
            callback.onError("Error creando la petición de login.");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                API_LOGIN,
                jsonBody,
                callback::onSuccess,
                error -> callback.onError(parseVolleyError(error))
        );

        requestQueue.add(request);
    }

    public void register(String usuario, String correo, String password, ApiCallback callback) {
        JSONObject jsonBody = new JSONObject();

        try {
            jsonBody.put("username", usuario);
            jsonBody.put("email", correo);
            jsonBody.put("password", password);
        } catch (JSONException e) {
            callback.onError("Error creando la petición de registro.");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                API_REGISTER,
                jsonBody,
                callback::onSuccess,
                error -> callback.onError(parseVolleyError(error))
        );

        requestQueue.add(request);
    }

    private String parseVolleyError(com.android.volley.VolleyError error) {
        try {
            if (error.networkResponse != null && error.networkResponse.data != null) {
                String body = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(body);

                if (obj.has("message")) {
                    return obj.getString("message");
                }

                return "Error " + error.networkResponse.statusCode;
            }
        } catch (Exception ignored) {
        }

        return "Error al conectar con el servidor";
    }
}