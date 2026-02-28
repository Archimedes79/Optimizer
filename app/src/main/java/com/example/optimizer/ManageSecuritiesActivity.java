package com.example.optimizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;

public class ManageSecuritiesActivity extends AppCompatActivity {

    private EditText etIdentifier;
    private EditText etQuantity;
    private EditText etCustomName;
    private Button btnAdd;
    private Button btnDone;
    private ProgressBar pbSearching;
    private ManageSecuritiesAdapter adapter;
    private Portfolio portfolio;

    private static final String PREFS_NAME = "OptimizerPrefs";
    private static final String KEY_API_KEY = "api_key";

    private static final Map<String, String> MOCK_DB = new HashMap<>();
    static {
        MOCK_DB.put("AAPL", "Apple Inc.");
        MOCK_DB.put("MSFT", "Microsoft Corp.");
        MOCK_DB.put("GOOGL", "Alphabet Inc.");
        MOCK_DB.put("AMZN", "Amazon.com Inc.");
        MOCK_DB.put("TSLA", "Tesla Inc.");
        MOCK_DB.put("VUSA", "Vanguard S&P 500 ETF");
        MOCK_DB.put("VWRL", "Vanguard FTSE All-World");
        MOCK_DB.put("840400", "Allianz SE");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_securities);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Manage Portfolio");
        }

        portfolio = Portfolio.getInstance();

        etIdentifier = findViewById(R.id.etIdentifier);
        etQuantity = findViewById(R.id.etQuantity);
        etCustomName = findViewById(R.id.etCustomName);
        btnAdd = findViewById(R.id.btnAddSecurity);
        btnDone = findViewById(R.id.btnDone);
        pbSearching = findViewById(R.id.pbSearching);
        RecyclerView recyclerView = findViewById(R.id.rvManageSecurities);

        adapter = new ManageSecuritiesAdapter(portfolio.getSecurities(), security -> {
            portfolio.removeSecurity(security);
            portfolio.save(this);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Removed " + security.getName(), Toast.LENGTH_SHORT).show();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> searchAndAddSecurity());
        btnDone.setOnClickListener(v -> finish());
    }

    private void searchAndAddSecurity() {
        String identifier = etIdentifier.getText().toString().trim().toUpperCase();
        String qtyStr = etQuantity.getText().toString().trim();
        String customName = etCustomName.getText().toString().trim();

        if (identifier.isEmpty() || qtyStr.isEmpty()) {
            Toast.makeText(this, "Please enter both ID and Quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantity = Integer.parseInt(qtyStr);

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

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            pbSearching.setVisibility(View.GONE);
            btnAdd.setEnabled(true);

            String dbName = MOCK_DB.getOrDefault(identifier, "Unknown Security (" + identifier + ")");
            
            Security newSecurity = new Security(dbName, identifier, quantity);
            if (!customName.isEmpty()) {
                newSecurity.setCustomName(customName);
            }
            
            // Use the same subfunction call to refresh data from "database"
            newSecurity.refreshData();

            if (portfolio.addSecurity(newSecurity)) {
                portfolio.save(this);
                adapter.notifyItemInserted(portfolio.getSecurities().size() - 1);
                etIdentifier.setText("");
                etQuantity.setText("");
                etCustomName.setText("");
                Toast.makeText(this, "Added: " + newSecurity.getName(), Toast.LENGTH_SHORT).show();
            }
        }, 800);
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
