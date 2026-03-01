package com.example.optimizer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private Portfolio portfolio;
    private PortfolioGraphView graphView;
    private ProgressBar pbSync;
    private AlphaVantageService alphaVantageService;

    private static final String PREFS_NAME = "OptimizerPrefs";
    private static final String KEY_API_KEY = "api_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        portfolio = Portfolio.getInstance();
        portfolio.load(this); // Load portfolio from JSON on startup (reloads old values)
        alphaVantageService = new AlphaVantageService();

        graphView = findViewById(R.id.portfolioGraph);
        pbSync = findViewById(R.id.pbSync); // Assuming there's a ProgressBar in your layout
        
        if (graphView != null) {
            graphView.setSecurities(portfolio.getSecurities());
        }

        findViewById(R.id.btnApiKey).setOnClickListener(v -> {
            Intent intent = new Intent(this, ApiKeyActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnAddRemove).setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageSecuritiesActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnSync).setOnClickListener(v -> {
            syncPortfolio();
        });

        findViewById(R.id.btnOptimize).setOnClickListener(v -> {
            Intent intent = new Intent(this, OptimizeActivity.class);
            startActivity(intent);
        });
    }

    private void syncPortfolio() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(KEY_API_KEY, "");

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please set an API Key first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (portfolio.getSecurities().isEmpty()) {
            Toast.makeText(this, "No securities to sync", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pbSync != null) pbSync.setVisibility(View.VISIBLE);
        findViewById(R.id.btnSync).setEnabled(false);

        alphaVantageService.syncPortfolio(portfolio, apiKey, new AlphaVantageService.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    if (pbSync != null) pbSync.setVisibility(View.GONE);
                    findViewById(R.id.btnSync).setEnabled(true);
                    portfolio.save(MainActivity.this);
                    if (graphView != null) {
                        graphView.setSecurities(portfolio.getSecurities());
                    }
                    Toast.makeText(MainActivity.this, "Portfolio synced successfully", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    if (pbSync != null) pbSync.setVisibility(View.GONE);
                    findViewById(R.id.btnSync).setEnabled(true);
                    showErrorDialog("Sync Error", errorMessage);
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

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh graph with saved data when returning to activity
        if (graphView != null && portfolio != null) {
            graphView.setSecurities(portfolio.getSecurities());
        }
    }
}
