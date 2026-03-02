package com.example.optimizer;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageSecuritiesActivity extends AppCompatActivity {

    private EditText etIdentifier;
    private EditText etQuantity;
    private EditText etCustomName;
    private View viewColorPreview;
    private TextView tvEuroSymbol;
    private SwitchMaterial swUnit;
    private SwitchMaterial swFixed;
    private Button btnAdd;
    private Button btnDone;
    private ProgressBar pbSearching;
    private ManageSecuritiesAdapter adapter;
    private Portfolio portfolio;
    private Security editingSecurity = null;
    private YahooFinanceService yahooFinanceService;
    
    private String lastSetQuantityText = "";
    private double initialQuantity = 0;

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
        tvEuroSymbol = findViewById(R.id.tvEuroSymbol);
        swUnit = findViewById(R.id.swUnit);
        swFixed = findViewById(R.id.swFixed);
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
                Toast.makeText(ManageSecuritiesActivity.this, "Removed " + security.getDisplayName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSecurityClicked(Security security) {
                editingSecurity = security;
                etIdentifier.setText(security.getSymbol());
                
                initialQuantity = security.getQuantity();
                lastSetQuantityText = String.format(Locale.US, "%.4f", initialQuantity);
                etQuantity.setText(lastSetQuantityText);
                
                etCustomName.setText(security.getAlias() != null ? security.getAlias() : "");
                viewColorPreview.setBackgroundColor(security.getColor());
                swUnit.setChecked(false);
                tvEuroSymbol.setVisibility(View.INVISIBLE);
                swFixed.setChecked(security.isFixed());
                btnAdd.setText("Update");
                etIdentifier.setEnabled(true);
            }

            @Override
            public void onSecurityColorChanged(Security security) {
                portfolio.save(ManageSecuritiesActivity.this);
            }
        });

        swUnit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvEuroSymbol.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
            if (editingSecurity == null) return;
            
            String currentText = etQuantity.getText().toString().trim();
            if (currentText.isEmpty()) return;
            
            try {
                double val = Double.parseDouble(currentText);
                List<Double> values = editingSecurity.getValuesOverTime();
                if (values.isEmpty()) return;
                double lastPrice = values.get(values.size() - 1);
                if (lastPrice <= 0) return;
                
                if (isChecked) {
                    // Shares to Euro
                    lastSetQuantityText = String.format(Locale.US, "%.2f", val * lastPrice);
                } else {
                    // Euro to Shares
                    double backToShares = val / lastPrice;
                    if (Math.abs(backToShares - initialQuantity) < 0.0001) {
                        lastSetQuantityText = String.format(Locale.US, "%.4f", initialQuantity);
                    } else {
                        lastSetQuantityText = String.format(Locale.US, "%.4f", backToShares);
                    }
                }
                etQuantity.setText(lastSetQuantityText);
            } catch (NumberFormatException ignored) {}
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
        boolean isFixed = swFixed.isChecked();

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
        boolean quantityFieldUnchanged = qtyInputStr.equals(lastSetQuantityText);

        yahooFinanceService.searchSecurities(identifierInput, aliasInput, new YahooFinanceService.Callback<List<Security>>() {
            @Override
            public void onSuccess(List<Security> results) {
                runOnUiThread(() -> {
                    pbSearching.setVisibility(View.GONE);
                    btnAdd.setEnabled(true);
                    
                    List<Security> filteredResults = new ArrayList<>();
                    for (Security s : results) {
                        if (s.getNumberOfEntries() > 1) {
                            filteredResults.add(s);
                        }
                    }

                    if (filteredResults.isEmpty()) {
                        showErrorDialog("Search failed", "No valid securities with historical data found.");
                        return;
                    }

                    if (filteredResults.size() == 1) {
                        finalizeAddition(filteredResults.get(0), inputValue, isEuro, quantityFieldUnchanged, isFixed);
                    } else {
                        showSelectionDialog(filteredResults, inputValue, isEuro, quantityFieldUnchanged, isFixed);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    pbSearching.setVisibility(View.GONE);
                    btnAdd.setEnabled(true);
                    showErrorDialog("Search failed", errorMessage);
                });
            }
        });
    }

    private void showSelectionDialog(List<Security> results, double inputValue, boolean isEuro, boolean quantityFieldUnchanged, boolean isFixed) {
        String[] options = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            Security s = results.get(i);
            options[i] = String.format(Locale.getDefault(), "%s (%s)\nData Points: %d", 
                    s.getName(), s.getSymbol(), s.getNumberOfEntries());
        }

        new AlertDialog.Builder(this)
                .setTitle("Select the correct Ticker")
                .setItems(options, (dialog, which) -> {
                    finalizeAddition(results.get(which), inputValue, isEuro, quantityFieldUnchanged, isFixed);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void finalizeAddition(Security selectedSecurity, double inputValue, boolean isEuro, boolean quantityFieldUnchanged, boolean isFixed) {
        double finalQuantity;
        
        if (editingSecurity != null && quantityFieldUnchanged) {
            finalQuantity = initialQuantity;
        } else if (isEuro) {
            List<Double> values = selectedSecurity.getValuesOverTime();
            if (values.isEmpty() || values.get(values.size() - 1) == 0) {
                finalQuantity = 0;
                Toast.makeText(this, "Price lookup failed, quantity set to 0", Toast.LENGTH_LONG).show();
            } else {
                finalQuantity = inputValue / values.get(values.size() - 1);
            }
        } else {
            finalQuantity = inputValue;
        }

        if (editingSecurity != null) {
            editingSecurity.setName(selectedSecurity.getName());
            editingSecurity.setSymbol(selectedSecurity.getSymbol());
            editingSecurity.setAlias(selectedSecurity.getAlias());
            
            // Using unified setter
            editingSecurity.setHistory(selectedSecurity.getValuesOverTime(), selectedSecurity.getEpochDays());

            editingSecurity.setQuantity(finalQuantity);
            editingSecurity.setFixed(isFixed);
            
            portfolio.save(this);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Updated: " + editingSecurity.getDisplayName(), Toast.LENGTH_SHORT).show();
            clearInputs();
        } else {
            if (portfolio.getSecurities().size() >= 24) {
                Toast.makeText(this, "Limit reached", Toast.LENGTH_SHORT).show();
                return;
            }

            selectedSecurity.setQuantity(finalQuantity);
            selectedSecurity.setFixed(isFixed);
            if (portfolio.addSecurity(selectedSecurity)) {
                portfolio.save(this);
                adapter.notifyDataSetChanged();
                clearInputs();
                Toast.makeText(this, "Added: " + selectedSecurity.getDisplayName(), Toast.LENGTH_SHORT).show();
            }
        }
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
        swFixed.setChecked(false);
        tvEuroSymbol.setVisibility(View.INVISIBLE);
        viewColorPreview.setBackgroundColor(android.graphics.Color.GRAY);
        etIdentifier.setEnabled(true);
        btnAdd.setText("Add");
        lastSetQuantityText = "";
        initialQuantity = 0;
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
