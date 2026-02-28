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
    private TextView tvSliderLabel;
    private TextView tvQuantities;
    private Portfolio portfolio;
    private List<Security> securities;

    private double[] currentQuantities;
    private double[] targetQuantities;
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
        tvSliderLabel = findViewById(R.id.tvSliderLabel);
        tvQuantities = findViewById(R.id.tvQuantities);
        Button btnBack = findViewById(R.id.btnBack);

        calculateOptimization();

        sbReduceVariance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateUI(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnBack.setOnClickListener(v -> finish());

        // Initial update
        updateUI(0);
    }

    /**
     * Calculates the Minimum Variance Allocation based on the visible time window.
     * 
     * Logic:
     * 1. The total value of the portfolio at the LATEST data point must remain constant.
     * 2. We calculate the variance of returns for each security over the entire visible period.
     * 3. We use Inverse-Variance Weighting to determine the target weights.
     * 4. These weights are applied to the total value to find the required target quantities.
     */
    private void calculateOptimization() {
        if (securities == null || securities.isEmpty()) return;

        int n = securities.size();
        currentQuantities = new double[n];
        targetQuantities = new double[n];
        currentTotalValue = 0;

        double[] variances = new double[n];
        double[] latestPrices = new double[n];
        
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            currentQuantities[i] = s.getQuantity();
            
            List<Double> values = s.getValuesOverTime();
            if (values.isEmpty()) {
                variances[i] = 1.0;
                continue;
            }
            
            // Identify latest price for "Budget Constraint"
            latestPrices[i] = values.get(values.size() - 1);
            currentTotalValue += currentQuantities[i] * latestPrices[i];

            // Calculate variance over the visible history (last 20 years max)
            if (values.size() > 1) {
                List<Double> returns = new ArrayList<>();
                for (int j = 1; j < values.size(); j++) {
                    returns.add((values.get(j) - values.get(j - 1)) / values.get(j - 1));
                }
                
                double sum = 0;
                for (double r : returns) sum += r;
                double mean = sum / returns.size();
                
                double sqSum = 0;
                for (double r : returns) sqSum += (r - mean) * (r - mean);
                variances[i] = sqSum / returns.size();
            } else {
                variances[i] = 1.0; 
            }
        }

        // Inverse-Variance Weighting
        double invVarSum = 0;
        for (double v : variances) {
            if (v > 0) invVarSum += (1.0 / v);
        }

        for (int i = 0; i < n; i++) {
            if (latestPrices[i] > 0) {
                double weight = (variances[i] > 0) ? (1.0 / variances[i]) / invVarSum : 0;
                // Target Quantity = (Fixed Total Value * Weight) / Latest Price
                targetQuantities[i] = (currentTotalValue * weight) / latestPrices[i];
            } else {
                targetQuantities[i] = 0;
            }
        }
    }

    private void updateUI(int progress) {
        double factor = progress / 100.0;
        tvSliderLabel.setText(String.format(Locale.getDefault(), "Minimum Variance Allocation: %d%%", progress));

        int n = securities.size();
        double[] displayQuantities = new double[n];
        StringBuilder sb = new StringBuilder("Required Quantities:\n");

        for (int i = 0; i < n; i++) {
            // Linear transition between current and target quantities
            displayQuantities[i] = currentQuantities[i] * (1.0 - factor) + targetQuantities[i] * factor;
            sb.append(String.format(Locale.getDefault(), "%s: %.2f\n", securities.get(i).getName(), displayQuantities[i]));
        }

        tvQuantities.setText(sb.toString());
        // The graph's black line recalculates based on these hypothetical historical holdings
        graphView.setSecuritiesWithQuantities(securities, displayQuantities);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
