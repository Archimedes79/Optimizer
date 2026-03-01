package com.example.optimizer;

import android.content.Intent;
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
    private YahooFinanceService yahooFinanceService;

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
        portfolio.load(this); // Reload old values at start

        yahooFinanceService = new YahooFinanceService();

        graphView = findViewById(R.id.portfolioGraph);
        pbSync = findViewById(R.id.pbSync);
        
        if (graphView != null) {
            graphView.setSecurities(portfolio.getSecurities());
        }

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
        if (portfolio.getSecurities().isEmpty()) {
            Toast.makeText(this, "No assets to sync", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pbSync != null) pbSync.setVisibility(View.VISIBLE);
        findViewById(R.id.btnSync).setEnabled(false);

        yahooFinanceService.syncPortfolio(portfolio, new YahooFinanceService.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    if (pbSync != null) pbSync.setVisibility(View.GONE);
                    findViewById(R.id.btnSync).setEnabled(true);
                    portfolio.save(MainActivity.this);
                    if (graphView != null) {
                        graphView.setSecurities(portfolio.getSecurities());
                    }
                    Toast.makeText(MainActivity.this, "Sync complete", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    if (pbSync != null) pbSync.setVisibility(View.GONE);
                    findViewById(R.id.btnSync).setEnabled(true);
                    showErrorDialog("Sync Failed", errorMessage);
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
        if (graphView != null && portfolio != null) {
            graphView.setSecurities(portfolio.getSecurities());
        }
    }
}
