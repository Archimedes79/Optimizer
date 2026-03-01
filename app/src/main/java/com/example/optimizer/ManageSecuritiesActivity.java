package com.example.optimizer;

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

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class ManageSecuritiesActivity extends AppCompatActivity {

    private EditText etIdentifier;
    private EditText etQuantity;
    private EditText etCustomName;
    private View viewColorPreview;
    private SwitchMaterial swUnit;
    private Button btnAdd;
    private Button btnDone;
    private ProgressBar pbSearching;
    private ManageSecuritiesAdapter adapter;
    private Portfolio portfolio;
    private Security editingSecurity = null;
    private YahooFinanceService yahooFinanceService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_securities);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Manage Portfolio");
        }

        portfolio = Portfolio.getInstance();
        yahooFinanceService = new YahooFinanceService();

        etIdentifier = findViewById(R.id.etIdentifier);
        etQuantity = findViewById(R.id.etQuantity);
        etCustomName = findViewById(R.id.etCustomName);
        viewColorPreview = findViewById(R.id.viewColorPreview);
        swUnit = findViewById(R.id.swUnit);
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
                etCustomName.setText(security.getAlias() != null ? security.getAlias() : "");
                viewColorPreview.setBackgroundColor(security.getColor());
                swUnit.setChecked(false); // Default to units when editing existing
                btnAdd.setText("Update");
                etIdentifier.setEnabled(true);
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
        String identifierInput = etIdentifier.getText().toString().trim().toUpperCase();
        String qtyInputStr = etQuantity.getText().toString().trim();
        String aliasInput = etCustomName.getText().toString().trim();

        if (identifierInput.isEmpty() || qtyInputStr.isEmpty()) {
            Toast.makeText(this, "Please enter both ID and Quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        double inputValue;
        try {
            inputValue = Double.parseDouble(qtyInputStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            return;
        }

        pbSearching.setVisibility(View.VISIBLE);
        btnAdd.setEnabled(false);

        boolean isEuro = swUnit.isChecked();

        yahooFinanceService.addSecurity(identifierInput, aliasInput, 1.0, new YahooFinanceService.Callback<Security>() {
            @Override
            public void onSuccess(Security newSecurity) {
                runOnUiThread(() -> {
                    pbSearching.setVisibility(View.GONE);
                    btnAdd.setEnabled(true);

                    double finalQuantity;
                    if (isEuro) {
                        List<Double> values = newSecurity.getValuesOverTime();
                        if (values.isEmpty() || values.get(values.size() - 1) == 0) {
                            finalQuantity = 0;
                            if (inputValue > 0) {
                                Toast.makeText(ManageSecuritiesActivity.this, "Price lookup failed, quantity set to 0", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            finalQuantity = inputValue / values.get(values.size() - 1);
                        }
                    } else {
                        finalQuantity = inputValue;
                    }

                    if (editingSecurity != null) {
                        editingSecurity.setName(newSecurity.getName());
                        editingSecurity.setIdentifier(newSecurity.getIdentifier());
                        editingSecurity.setAlias(newSecurity.getAlias());
                        editingSecurity.setValuesOverTime(newSecurity.getValuesOverTime());
                        editingSecurity.setDates(newSecurity.getDates());
                        editingSecurity.setQuantity(finalQuantity);
                        
                        portfolio.save(ManageSecuritiesActivity.this);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(ManageSecuritiesActivity.this, "Updated: " + editingSecurity.getName(), Toast.LENGTH_SHORT).show();
                        clearInputs();
                    } else {
                        if (portfolio.getSecurities().size() >= 24) {
                            Toast.makeText(ManageSecuritiesActivity.this, "Limit of 24 securities reached", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        newSecurity.setQuantity(finalQuantity);
                        if (portfolio.addSecurity(newSecurity)) {
                            portfolio.save(ManageSecuritiesActivity.this);
                            adapter.notifyDataSetChanged();
                            clearInputs();
                            Toast.makeText(ManageSecuritiesActivity.this, String.format(java.util.Locale.getDefault(), "Added: %s (%.2f units)", newSecurity.getName(), finalQuantity), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    pbSearching.setVisibility(View.GONE);
                    btnAdd.setEnabled(true);
                    showErrorDialog("Error", errorMessage);
                });
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
        swUnit.setChecked(false);
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
