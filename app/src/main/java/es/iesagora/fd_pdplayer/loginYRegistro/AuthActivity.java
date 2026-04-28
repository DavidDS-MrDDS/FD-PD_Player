package es.iesagora.fd_pdplayer.loginYRegistro;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import es.iesagora.fd_pdplayer.R;

public class AuthActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("AUTH_DEBUG", "Antes de setContentView");
        setContentView(R.layout.activity_auth);
        Log.d("AUTH_DEBUG", "Después de setContentView");
    }
}