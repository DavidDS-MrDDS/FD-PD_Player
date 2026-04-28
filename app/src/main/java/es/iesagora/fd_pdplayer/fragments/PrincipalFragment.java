package es.iesagora.fd_pdplayer.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.jetbrains.annotations.Nullable;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.databinding.FragmentPrincipalBinding;
import es.iesagora.fd_pdplayer.fragments.principalFragments.AjustesFragment;
import es.iesagora.fd_pdplayer.fragments.principalFragments.BusquedaFragment;
import es.iesagora.fd_pdplayer.fragments.principalFragments.CancionesFragment;
import es.iesagora.fd_pdplayer.fragments.principalFragments.ListasFragment;

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
                    case 0: return new CancionesFragment();
                    case 1: return new ListasFragment();
                    case 2: return new BusquedaFragment();
                    case 3: return new AjustesFragment();
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
                        case 0: tv.setText("Canciones"); break;
                        case 1: tv.setText("Listas"); break;
                        case 2: tv.setText("Búsqueda"); break;
                        case 3: tv.setText("Ajustes"); break;
                    }
                    tab.setCustomView(custom);
                }).attach();

        // aplica estado inicial + listener
        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                actualizarTab(tab, true);
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                actualizarTab(tab, false);
            }
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        // fuerza estilo correcto para la primera tab
        TabLayout.Tab first = binding.tabLayout.getTabAt(0);
        if (first != null) actualizarTab(first, true);
    }

    private void actualizarTab(com.google.android.material.tabs.TabLayout.Tab tab, boolean selected) {
        View v = tab.getCustomView();
        if (v == null) return;

        View root = v.findViewById(R.id.tabRoot);
        TextView tv = v.findViewById(R.id.tabText);

        if (selected) {
            root.setBackgroundResource(R.drawable.bg_segment_selected);
            tv.setTextColor(0xFF1337EC); // primary (#1337ec)
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            root.setBackgroundResource(R.drawable.bg_segment_unselected);
            tv.setTextColor(0xFFB7C3FF);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.NORMAL);
        }
    }

}