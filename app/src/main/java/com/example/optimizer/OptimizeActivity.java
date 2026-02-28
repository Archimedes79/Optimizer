package com.example.optimizer;

import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OptimizeActivity extends AppCompatActivity {

    private PortfolioGraphView graphView;
    private SeekBar sbReduceVariance;
    private SeekBar sbMaxExpectation;
    private TextView tvVarLabel;
    private TextView tvExpLabel;
    private TextView tvQuantities;
    private Portfolio portfolio;
    private List<Security> securities;

    private double[] currentQuantities;
    private double[] minVarQuantities;
    private double[] maxExpQuantities;
    private double currentTotalValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimize);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Optimize Portfolio");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        portfolio = Portfolio.getInstance();
        securities = portfolio.getSecurities();

        graphView = findViewById(R.id.optimizeGraph);
        sbReduceVariance = findViewById(R.id.sbReduceVariance);
        sbMaxExpectation = findViewById(R.id.sbMaxExpectation);
        tvVarLabel = findViewById(R.id.tvVarLabel);
        tvExpLabel = findViewById(R.id.tvExpLabel);
        tvQuantities = findViewById(R.id.tvQuantities);
        Button btnBack = findViewById(R.id.btnBack);

        calculateOptimization();

        sbReduceVariance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (progress + sbMaxExpectation.getProgress() > 100) {
                        sbMaxExpectation.setProgress(100 - progress);
                    }
                }
                updateUI();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbMaxExpectation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (progress + sbReduceVariance.getProgress() > 100) {
                        sbReduceVariance.setProgress(100 - progress);
                    }
                }
                updateUI();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnBack.setOnClickListener(v -> finish());
        updateUI();
    }

    private void calculateOptimization() {
        if (securities == null || securities.isEmpty()) return;

        int n = securities.size();
        currentQuantities = new double[n];
        minVarQuantities = new double[n];
        maxExpQuantities = new double[n];
        currentTotalValue = 0;

        double[] variances = new double[n];
        double[] expectations = new double[n];
        double[] latestPrices = new double[n];
        
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            currentQuantities[i] = s.getQuantity();
            
            List<Double> values = s.getValuesOverTime();
            if (values.isEmpty()) continue;
            
            latestPrices[i] = values.get(values.size() - 1);
            currentTotalValue += currentQuantities[i] * latestPrices[i];

            if (values.size() > 1) {
                List<Double> returns = new ArrayList<>();
                for (int j = 1; j < values.size(); j++) {
                    returns.add((values.get(j) - values.get(j - 1)) / values.get(j - 1));
                }
                
                double sum = 0;
                for (double r : returns) sum += r;
                double mean = sum / returns.size();
                expectations[i] = mean;
                
                double sqSum = 0;
                for (double r : returns) sqSum += (r - mean) * (r - mean);
                variances[i] = sqSum / returns.size();
            } else {
                variances[i] = 1.0;
                expectations[i] = 0;
            }
        }

        // 1. Min Variance Allocation (Inverse-Variance Weighting)
        double invVarSum = 0;
        for (double v : variances) if (v > 0) invVarSum += (1.0 / v);
        
        // 2. Max Expectation Allocation (Weight is 100% on the asset with max expectation)
        int bestIdx = 0;
        for (int i = 1; i < n; i++) {
            if (expectations[i] > expectations[bestIdx]) bestIdx = i;
        }

        for (int i = 0; i < n; i++) {
            if (latestPrices[i] > 0) {
                double minVarWeight = (variances[i] > 0) ? (1.0 / variances[i]) / invVarSum : 0;
                minVarQuantities[i] = (currentTotalValue * minVarWeight) / latestPrices[i];
                
                double maxExpWeight = (i == bestIdx) ? 1.0 : 0.0;
                maxExpQuantities[i] = (currentTotalValue * maxExpWeight) / latestPrices[i];
            }
        }
    }

    private void updateUI() {
        int varProgress = sbReduceVariance.getProgress();
        int expProgress = sbMaxExpectation.getProgress();
        int currentWeightProgress = 100 - varProgress - expProgress;

        tvVarLabel.setText(String.format(Locale.getDefault(), "Minimum Variance Allocation: %d%%", varProgress));
        tvExpLabel.setText(String.format(Locale.getDefault(), "Maximum Expectation Allocation: %d%%", expProgress));

        double varFactor = varProgress / 100.0;
        double expFactor = expProgress / 100.0;
        double curFactor = currentWeightProgress / 100.0;

        int n = securities.size();
        double[] displayQuantities = new double[n];
        StringBuilder sb = new StringBuilder("Required Quantities:\n");

        for (int i = 0; i < n; i++) {
            displayQuantities[i] = (currentQuantities[i] * curFactor) + 
                                  (minVarQuantities[i] * varFactor) + 
                                  (maxExpQuantities[i] * expFactor);
            
            sb.append(String.format(Locale.getDefault(), "%s: %.2f\n", securities.get(i).getName(), displayQuantities[i]));
        }

        tvQuantities.setText(sb.toString());
        graphView.setSecuritiesWithQuantities(securities, displayQuantities);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
