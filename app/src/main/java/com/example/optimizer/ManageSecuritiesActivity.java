package com.example.optimizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ManageSecuritiesActivity extends AppCompatActivity {

    private EditText etIdentifier;
    private EditText etQuantity;
    private EditText etCustomName;
    private View viewColorPreview;
    private Button btnAdd;
    private Button btnDone;
    private ProgressBar pbSearching;
    private ManageSecuritiesAdapter adapter;
    private Portfolio portfolio;
    private Security editingSecurity = null;
    private AlphaVantageService alphaVantageService;

    private static final String PREFS_NAME = "OptimizerPrefs";
    private static final String KEY_API_KEY = "api_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_securities);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Manage Portfolio");
        }

        portfolio = Portfolio.getInstance();
        alphaVantageService = new AlphaVantageService();

        etIdentifier = findViewById(R.id.etIdentifier);
        etQuantity = findViewById(R.id.etQuantity);
        etCustomName = findViewById(R.id.etCustomName);
        viewColorPreview = findViewById(R.id.viewColorPreview);
        btnAdd = findViewById(R.id.btnAddSecurity);
        btnDone = findViewById(R.id.btnDone);
        pbSearching = findViewById(R.id.pbSearching);
        RecyclerView recyclerView = findViewById(R.id.rvManageSecurities);

        adapter = new ManageSecuritiesAdapter(portfolio.getSecurities(), new ManageSecuritiesAdapter.OnSecurityActionListener() {
            @Override
            public void onSecurityRemoved(Security security) {
                portfolio.removeSecurity(security);
                portfolio.save(ManageSecuritiesActivity.this);
                adapter.notifyDataSetChanged();
                if (editingSecurity == security) {
                    clearInputs();
                }
                Toast.makeText(ManageSecuritiesActivity.this, "Removed " + security.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSecurityClicked(Security security) {
                editingSecurity = security;
                etIdentifier.setText(security.getIdentifier());
                etQuantity.setText(String.valueOf(security.getQuantity()));
                etCustomName.setText(security.getCustomName() != null ? security.getCustomName() : "");
                viewColorPreview.setBackgroundColor(security.getColor());
                btnAdd.setText("Update");
                etIdentifier.setEnabled(false);
            }
        });

        etIdentifier.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (editingSecurity == null) {
                    viewColorPreview.setBackgroundColor(Security.generateConsistentColor(s.toString().trim().toUpperCase()));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> searchAndAddOrUpdateSecurity());
        btnDone.setOnClickListener(v -> finish());
    }

    private void searchAndAddOrUpdateSecurity() {
        String identifier = etIdentifier.getText().toString().trim().toUpperCase();
        String qtyStr = etQuantity.getText().toString().trim();
        String customName = etCustomName.getText().toString().trim();

        if (identifier.isEmpty() || qtyStr.isEmpty()) {
            Toast.makeText(this, "Please enter both ID and Quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantity = Integer.parseInt(qtyStr);

        if (editingSecurity != null) {
            // Update existing
            editingSecurity.setQuantity(quantity);
            editingSecurity.setCustomName(customName.isEmpty() ? null : customName);
            portfolio.save(this);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Updated: " + editingSecurity.getName(), Toast.LENGTH_SHORT).show();
            clearInputs();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(KEY_API_KEY, "");

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please set an API Key first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (portfolio.getSecurities().size() >= 24) {
            Toast.makeText(this, "Limit of 24 securities reached", Toast.LENGTH_SHORT).show();
            return;
        }

        pbSearching.setVisibility(View.VISIBLE);
        btnAdd.setEnabled(false);

        alphaVantageService.addSecurity(identifier, quantity, apiKey, new AlphaVantageService.Callback<Security>() {
            @Override
            public void onSuccess(Security newSecurity) {
                pbSearching.setVisibility(View.GONE);
                btnAdd.setEnabled(true);

                if (!customName.isEmpty()) {
                    newSecurity.setCustomName(customName);
                }

                if (portfolio.addSecurity(newSecurity)) {
                    portfolio.save(ManageSecuritiesActivity.this);
                    adapter.notifyItemInserted(portfolio.getSecurities().size() - 1);
                    clearInputs();
                    Toast.makeText(ManageSecuritiesActivity.this, "Added: " + newSecurity.getName(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                pbSearching.setVisibility(View.GONE);
                btnAdd.setEnabled(true);
                showErrorDialog("Error Adding Security", errorMessage);
            }
        });
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void clearInputs() {
        editingSecurity = null;
        etIdentifier.setText("");
        etQuantity.setText("");
        etCustomName.setText("");
        viewColorPreview.setBackgroundColor(android.graphics.Color.GRAY);
        etIdentifier.setEnabled(true);
        btnAdd.setText("Add");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
