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
    private SeekBar sbMinDrawdown;
    private TextView tvVarLabel;
    private TextView tvExpLabel;
    private TextView tvMddLabel;
    private TextView tvQuantities;
    private Portfolio portfolio;
    private List<Security> securities;

    private double[] currentQuantities;
    private double[] minVarQuantities;
    private double[] maxExpQuantities;
    private double[] minDrawdownQuantities;
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
        sbMinDrawdown = findViewById(R.id.sbMinDrawdown);
        tvVarLabel = findViewById(R.id.tvVarLabel);
        tvExpLabel = findViewById(R.id.tvExpLabel);
        tvMddLabel = findViewById(R.id.tvMddLabel);
        tvQuantities = findViewById(R.id.tvQuantities);
        Button btnBack = findViewById(R.id.btnBack);

        calculateOptimization();

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    adjustSliders(seekBar, progress);
                }
                updateUI();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        sbReduceVariance.setOnSeekBarChangeListener(listener);
        sbMaxExpectation.setOnSeekBarChangeListener(listener);
        sbMinDrawdown.setOnSeekBarChangeListener(listener);

        btnBack.setOnClickListener(v -> finish());
        updateUI();
    }

    private void adjustSliders(SeekBar changedSeekBar, int progress) {
        int total = sbReduceVariance.getProgress() + sbMaxExpectation.getProgress() + sbMinDrawdown.getProgress();
        if (total > 100) {
            int excess = total - 100;
            if (changedSeekBar == sbReduceVariance) {
                reduceOtherSliders(excess, sbMaxExpectation, sbMinDrawdown);
            } else if (changedSeekBar == sbMaxExpectation) {
                reduceOtherSliders(excess, sbReduceVariance, sbMinDrawdown);
            } else {
                reduceOtherSliders(excess, sbReduceVariance, sbMaxExpectation);
            }
        }
    }

    private void reduceOtherSliders(int excess, SeekBar s1, SeekBar s2) {
        int p1 = s1.getProgress();
        int p2 = s2.getProgress();
        if (p1 + p2 == 0) return;
        
        double ratio1 = (double) p1 / (p1 + p2);
        int red1 = (int) Math.round(excess * ratio1);
        int red2 = excess - red1;
        
        s1.setProgress(Math.max(0, p1 - red1));
        s2.setProgress(Math.max(0, p2 - red2));
    }

    /**
     * Reasoning:
     * Maximum Drawdown (MDD) is the maximum loss from a peak to a trough of a portfolio, before a new peak is attained.
     * Logic:
     * 1. For each security, we track its cumulative peak price over the visible time window.
     * 2. Drawdown at time t = (Peak_Price_up_to_t - Price_t) / Peak_Price_up_to_t.
     * 3. Max Drawdown = Max(all historical drawdowns).
     * 
     * Strategy:
     * Similar to Min Variance, we aim to weight assets with LOW Maximum Drawdowns higher.
     * Weight_i = (1 / MDD_i) / Sum(1 / MDD_j).
     */
    private void calculateOptimization() {
        if (securities == null || securities.isEmpty()) return;

        int n = securities.size();
        currentQuantities = new double[n];
        minVarQuantities = new double[n];
        maxExpQuantities = new double[n];
        minDrawdownQuantities = new double[n];
        currentTotalValue = 0;

        double[] variances = new double[n];
        double[] expectations = new double[n];
        double[] maxDrawdowns = new double[n];
        double[] latestPrices = new double[n];
        
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            currentQuantities[i] = s.getQuantity();
            List<Double> values = s.getValuesOverTime();
            if (values.isEmpty()) continue;
            
            latestPrices[i] = values.get(values.size() - 1);
            currentTotalValue += currentQuantities[i] * latestPrices[i];

            if (values.size() > 1) {
                // Returns and Variance
                List<Double> returns = new ArrayList<>();
                double peak = values.get(0);
                double mdd = 0;
                
                for (int j = 1; j < values.size(); j++) {
                    double val = values.get(j);
                    returns.add((val - values.get(j - 1)) / values.get(j - 1));
                    
                    if (val > peak) peak = val;
                    double dd = (peak - val) / peak;
                    if (dd > mdd) mdd = dd;
                }
                
                double sum = 0;
                for (double r : returns) sum += r;
                expectations[i] = sum / returns.size();
                
                double sqSum = 0;
                for (double r : returns) sqSum += (r - expectations[i]) * (r - expectations[i]);
                variances[i] = sqSum / returns.size();
                maxDrawdowns[i] = Math.max(0.01, mdd); // Avoid div by zero
            } else {
                variances[i] = 1.0;
                expectations[i] = 0;
                maxDrawdowns[i] = 1.0;
            }
        }

        // 1. Min Var Weights
        double invVarSum = 0;
        for (double v : variances) if (v > 0) invVarSum += (1.0 / v);
        
        // 2. Max Exp (Best single asset)
        int bestExpIdx = 0;
        for (int i = 1; i < n; i++) if (expectations[i] > expectations[bestExpIdx]) bestExpIdx = i;

        // 3. Min Drawdown Weights (Inverse MDD)
        double invMddSum = 0;
        for (double m : maxDrawdowns) invMddSum += (1.0 / m);

        for (int i = 0; i < n; i++) {
            if (latestPrices[i] > 0) {
                double vWeight = (variances[i] > 0) ? (1.0 / variances[i]) / invVarSum : 0;
                minVarQuantities[i] = (currentTotalValue * vWeight) / latestPrices[i];
                
                double eWeight = (i == bestExpIdx) ? 1.0 : 0.0;
                maxExpQuantities[i] = (currentTotalValue * eWeight) / latestPrices[i];

                double mWeight = (1.0 / maxDrawdowns[i]) / invMddSum;
                minDrawdownQuantities[i] = (currentTotalValue * mWeight) / latestPrices[i];
            }
        }
    }

    private void updateUI() {
        int varP = sbReduceVariance.getProgress();
        int expP = sbMaxExpectation.getProgress();
        int mddP = sbMinDrawdown.getProgress();
        int curP = 100 - varP - expP - mddP;

        tvVarLabel.setText(String.format(Locale.getDefault(), "Minimum Variance Allocation: %d%%", varP));
        tvExpLabel.setText(String.format(Locale.getDefault(), "Maximum Expectation Allocation: %d%%", expP));
        tvMddLabel.setText(String.format(Locale.getDefault(), "Minimum Drawdown Allocation: %d%%", mddP));

        double vF = varP / 100.0;
        double eF = expP / 100.0;
        double mF = mddP / 100.0;
        double cF = curP / 100.0;

        int n = securities.size();
        double[] displayQuantities = new double[n];
        StringBuilder sb = new StringBuilder("Required Quantities:\n");

        for (int i = 0; i < n; i++) {
            displayQuantities[i] = (currentQuantities[i] * cF) + 
                                  (minVarQuantities[i] * vF) + 
                                  (maxExpQuantities[i] * eF) +
                                  (minDrawdownQuantities[i] * mF);
            
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
