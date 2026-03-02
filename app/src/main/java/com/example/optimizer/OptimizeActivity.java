package com.example.optimizer;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

/**
 * Optimisation UI: three SeekBars (Variance / Sharpe / Drawdown) whose
 * percentages sum to at most 100%.  The remainder = original portfolio.
 */
public class OptimizeActivity extends AppCompatActivity {

    private PortfolioGraphView graphView;
    private SeekBar sbReduceVariance;
    private SeekBar sbMaxSharpe;
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

        Portfolio portfolio = Portfolio.getInstance();
        securities = portfolio.getSecurities();
        optimizer = new PortfolioOptimizer(securities);

        graphView        = findViewById(R.id.optimizeGraph);
        sbReduceVariance = findViewById(R.id.sbReduceVariance);
        sbMaxSharpe      = findViewById(R.id.sbMaxExpectation);
        sbMinDrawdown    = findViewById(R.id.sbMinDrawdown);
        tvVarLabel       = findViewById(R.id.tvVarLabel);
        tvExpLabel       = findViewById(R.id.tvExpLabel);
        tvMddLabel       = findViewById(R.id.tvMddLabel);
        tvQuantities     = findViewById(R.id.tvQuantities);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Run the three optimisation strategies once at the current zoom level
        optimizer.calculateOptimizations((int) graphView.getCurrentVisibleCount());

        // Re-optimise only when the visible window (zoom) changes
        graphView.setOnVisibleRangeChangeListener(visibleCount -> {
            optimizer.calculateOptimizations((int) visibleCount);
            updateUI();
        });

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) adjustSliders(seekBar);
                updateUI();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        sbReduceVariance.setOnSeekBarChangeListener(listener);
        sbMaxSharpe.setOnSeekBarChangeListener(listener);
        sbMinDrawdown.setOnSeekBarChangeListener(listener);

        updateUI();
    }

    /** Ensures the three sliders never exceed 100% by proportionally reducing the other two. */
    private void adjustSliders(SeekBar changedSeekBar) {
        int total = sbReduceVariance.getProgress() + sbMaxSharpe.getProgress() + sbMinDrawdown.getProgress();
        if (total > 100) {
            int excess = total - 100;
            if (changedSeekBar == sbReduceVariance) {
                reduceOtherSliders(excess, sbMaxSharpe, sbMinDrawdown);
            } else if (changedSeekBar == sbMaxSharpe) {
                reduceOtherSliders(excess, sbReduceVariance, sbMinDrawdown);
            } else {
                reduceOtherSliders(excess, sbReduceVariance, sbMaxSharpe);
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

    /** Refreshes labels, allocation text, and the graph with blended quantities. */
    private void updateUI() {
        int varP    = sbReduceVariance.getProgress();
        int sharpeP = sbMaxSharpe.getProgress();
        int mddP    = sbMinDrawdown.getProgress();

        tvVarLabel.setText(String.format(Locale.getDefault(), "Minimum Variance: %d%%", varP));
        tvExpLabel.setText(String.format(Locale.getDefault(), "Max Sharpe Ratio: %d%%", sharpeP));
        tvMddLabel.setText(String.format(Locale.getDefault(), "Min Drawdown: %d%%", mddP));

        double[] displayQty = optimizer.getBlendedQuantities(varP / 100.0, sharpeP / 100.0, mddP / 100.0);
        float[] prices = optimizer.getLatestPrices();

        float totalValue = 0f;
        for (int i = 0; i < securities.size(); i++) {
            totalValue += (float) displayQty[i] * prices[i];
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < securities.size(); i++) {
            Security s = securities.get(i);
            float assetVal = (float) displayQty[i] * prices[i];
            float pct = (totalValue > 0) ? (assetVal / totalValue) * 100.0f : 0f;

            String tag = s.isFixed() ? "  ● FIXED" : "";
            sb.append(String.format(Locale.getDefault(), "%-12s %6.2f units  %5.1f%%%s\n",
                    s.getDisplayName(), displayQty[i], pct, tag));
        }

        tvQuantities.setText(sb.toString());
        graphView.setSecuritiesWithQuantities(securities, displayQty);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
