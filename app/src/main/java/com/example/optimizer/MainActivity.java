package com.example.optimizer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private Portfolio portfolio;
    private PortfolioGraphView graphView;

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
        portfolio.load(this); // Load portfolio from JSON on startup

        graphView = findViewById(R.id.portfolioGraph);
        graphView.setSecurities(portfolio.getSecurities());

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
        for (Security s : portfolio.getSecurities()) {
            s.refreshData();
        }
        portfolio.save(this);
        graphView.setSecurities(portfolio.getSecurities());
        Toast.makeText(this, "Portfolio synced with database", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh graph when returning from ManageSecuritiesActivity
        if (graphView != null && portfolio != null) {
            graphView.setSecurities(portfolio.getSecurities());
        }
    }
}
