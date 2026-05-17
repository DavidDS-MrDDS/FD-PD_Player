package es.iesagora.fd_pdplayer.parteVisual.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.jetbrains.annotations.Nullable;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentPrincipalBinding;
import es.iesagora.fd_pdplayer.parteVisual.fragments.principalFragments.AjustesFragment;
import es.iesagora.fd_pdplayer.parteVisual.fragments.principalFragments.BusquedaFragment;
import es.iesagora.fd_pdplayer.parteVisual.fragments.principalFragments.CancionesFragment;
import es.iesagora.fd_pdplayer.parteVisual.fragments.principalFragments.ListasFragment;

public class PrincipalFragment extends Fragment {

    private FragmentPrincipalBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPrincipalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        establecerAdaptadorViewPager();
        vincularTabLayoutConViewPager();
    }

    private void establecerAdaptadorViewPager() {
        binding.viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    default:
                    case 0:
                        return new CancionesFragment();
                    case 1:
                        return new ListasFragment();
                    case 2:
                        return new BusquedaFragment();
                    case 3:
                        return new AjustesFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 4;
            }
        });
    }

    private void vincularTabLayoutConViewPager() {
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    View custom = LayoutInflater.from(requireContext())
                            .inflate(R.layout.tab_segment, null, false);

                    TextView tv = custom.findViewById(R.id.tabText);

                    switch (position) {
                        case 0:
                            tv.setText("Canciones");
                            break;
                        case 1:
                            tv.setText("Listas");
                            break;
                        case 2:
                            tv.setText("Búsqueda");
                            break;
                        case 3:
                            tv.setText("Ajustes");
                            break;
                    }

                    tab.setCustomView(custom);
                }).attach();

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                actualizarTab(tab, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                actualizarTab(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        for (int i = 0; i < binding.tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = binding.tabLayout.getTabAt(i);
            if (tab != null) {
                actualizarTab(tab, i == binding.tabLayout.getSelectedTabPosition());
            }
        }
    }

    private void actualizarTab(TabLayout.Tab tab, boolean selected) {
        View v = tab.getCustomView();
        if (v == null) return;
        // Cambia el aspecto del "botón" de cambio de fragmento mientras está seleccionado
        // y cuando deja de estarlo.
        TextView tv = v.findViewById(R.id.tabText);

        if (selected) {
            tv.setBackgroundResource(R.drawable.bg_segment_selected);
            tv.setTextColor(0xFFF2F0FF);
        } else {
            tv.setBackgroundResource(R.drawable.bg_segment_unselected);
            tv.setTextColor(0xFFA8A6B7);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}