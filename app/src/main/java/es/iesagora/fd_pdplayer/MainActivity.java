package es.iesagora.fd_pdplayer;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import es.iesagora.fd_pdplayer.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView((binding = ActivityMainBinding.inflate(getLayoutInflater())).getRoot());

        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        navController = ((NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment))
                .getNavController();

        binding.btnBackToolbar.setImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material);

        binding.btnBackToolbar.setOnClickListener(v -> navController.navigateUp());

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean mostrarFlecha = destination.getId() != R.id.principalFragment;

            binding.btnBackToolbar.setVisibility(mostrarFlecha ? View.VISIBLE : View.INVISIBLE);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}