package com.example.optimizer;

import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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
    private List<Security> securities;
    private PortfolioOptimizer optimizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimize);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Optimize Portfolio");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Portfolio portfolio = Portfolio.getInstance();
        securities = portfolio.getSecurities();
        optimizer = new PortfolioOptimizer(securities);

        graphView = findViewById(R.id.optimizeGraph);
        sbReduceVariance = findViewById(R.id.sbReduceVariance);
        sbMaxExpectation = findViewById(R.id.sbMaxExpectation);
        sbMinDrawdown = findViewById(R.id.sbMinDrawdown);
        tvVarLabel = findViewById(R.id.tvVarLabel);
        tvExpLabel = findViewById(R.id.tvExpLabel);
        tvMddLabel = findViewById(R.id.tvMddLabel);
        tvQuantities = findViewById(R.id.tvQuantities);
        Button btnBack = findViewById(R.id.btnBack);

        // Precalculate initial optimization values
        optimizer.calculateOptimizations((int) graphView.getCurrentVisibleCount());

        graphView.setOnVisibleRangeChangeListener(visibleCount -> {
            // Only recalculate when the window/zoom is changed
            optimizer.calculateOptimizations((int) visibleCount);
            updateUI();
        });

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    adjustSliders(seekBar);
                }
                updateUI(); // Slider changes only trigger UI update (fast linear combination)
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

    private void adjustSliders(SeekBar changedSeekBar) {
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

    private void updateUI() {
        // Removed calculateOptimizations from here to make slider movement smooth.
        // It is now only called in onCreate and onVisibleRangeChanged.

        int varP = sbReduceVariance.getProgress();
        int expP = sbMaxExpectation.getProgress();
        int mddP = sbMinDrawdown.getProgress();

        tvVarLabel.setText(String.format(Locale.getDefault(), "Minimum Variance Allocation: %d%%", varP));
        tvExpLabel.setText(String.format(Locale.getDefault(), "Maximum Expectation Allocation: %d%%", expP));
        tvMddLabel.setText(String.format(Locale.getDefault(), "Minimum Drawdown Allocation: %d%%", mddP));

        // Fast calculation of blended quantities
        double[] displayQuantities = optimizer.getBlendedQuantities(varP / 100.0, expP / 100.0, mddP / 100.0);
        double[] latestPrices = optimizer.getLatestPrices();
        
        double totalValue = 0;
        for (int i = 0; i < securities.size(); i++) {
            totalValue += displayQuantities[i] * latestPrices[i];
        }

        StringBuilder sb = new StringBuilder("Portfolio Allocation:\n");
        for (int i = 0; i < securities.size(); i++) {
            Security s = securities.get(i);
            double assetValue = displayQuantities[i] * latestPrices[i];
            double percentage = (totalValue > 0) ? (assetValue / totalValue) * 100.0 : 0;
            
            String fixedTag = s.isFixed() ? " (FIXED)" : "";
            sb.append(String.format(Locale.getDefault(), "%s%s: %.2f units (%.1f%%)\n", 
                    s.getDisplayName(), fixedTag, displayQuantities[i], percentage));
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
