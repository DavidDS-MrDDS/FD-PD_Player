package es.iesagora.fd_pdplayer.fragments.principalFragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import es.iesagora.fd_pdplayer.R;
import es.iesagora.fd_pdplayer.adapters.ListasAdapter;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListaEntity;
import es.iesagora.fd_pdplayer.almacenamientoInterno.listasRoom.ListasViewModel;
import es.iesagora.fd_pdplayer.databinding.FragmentListasBinding;

public class ListasFragment extends Fragment {

    private FragmentListasBinding binding;
    private ListasAdapter adapter;
    private ListasViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentListasBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ListasViewModel.class);

        adapter = new ListasAdapter(requireContext(), new ListasAdapter.Listener() {
            @Override
            public void onAbrir(ListaEntity lista) {
                abrirLista(lista);
            }

            @Override
            public void onPedirBorrar(ListaEntity lista) {
                confirmarBorrado(lista);
            }
        });

        binding.recyclerViewListas.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewListas.setAdapter(adapter);

        binding.btnCrearLista.setOnClickListener(v -> mostrarDialogCrearLista());
        binding.cardFavoritos.setOnClickListener(v -> abrirFavoritos());

        viewModel.obtenerListas().observe(getViewLifecycleOwner(), listas -> adapter.setListas(listas));
    }

    private void abrirFavoritos() {
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        navController.navigate(R.id.favoritosFragment);
    }

    private void abrirLista(ListaEntity lista) {
        Bundle b = new Bundle();
        b.putInt("listaId", lista.getId());

        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        navController.navigate(R.id.listaFragment, b);
    }

    private void mostrarDialogCrearLista() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nombre de la lista");

        new AlertDialog.Builder(requireContext())
                .setTitle("Crear lista")
                .setView(input)
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Crear", (d, w) -> {
                    String nombre = input.getText().toString().trim();
                    if (!nombre.isEmpty()) {
                        viewModel.crearLista(nombre);
                    }
                })
                .show();
    }

    private void confirmarBorrado(ListaEntity lista) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Borrar lista")
                .setMessage("¿Seguro que quieres borrar \"" + lista.getNombre() + "\"?")
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Borrar", (d, w) -> viewModel.borrarLista(lista.getId()))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}