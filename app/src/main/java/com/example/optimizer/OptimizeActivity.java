package com.example.optimizer;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

/**
 * Optimisation UI: three SeekBars (Variance / Sharpe / Drawdown) whose
 * percentages sum to at most 100%.  The remainder = original portfolio.
 *
 * <p>The allocation table shows delta units and delta percentages vs the
 * original portfolio.  Positive deltas are green, negative are red.</p>
 */
public class OptimizeActivity extends AppCompatActivity {

    private static final int COLOR_POS = 0xFF2E7D32;  // green 800
    private static final int COLOR_NEG = 0xFFC62828;  // red 800
    private static final int COLOR_ZERO = 0xFF5A6478; // textSecondary

    private PortfolioGraphView graphView;
    private SeekBar sbReduceVariance;
    private SeekBar sbMaxSharpe;
    private SeekBar sbMinDrawdown;
    private TextView tvVarLabel;
    private TextView tvExpLabel;
    private TextView tvMddLabel;
    private TableLayout optimizeTable;
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
        optimizeTable    = findViewById(R.id.optimizeTable);

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

    /**
     * Refreshes slider labels and the allocation table.
     * Shows delta units (blended − original) and delta percentage with
     * green / red colouring.
     */
    private void updateUI() {
        int varP    = sbReduceVariance.getProgress();
        int sharpeP = sbMaxSharpe.getProgress();
        int mddP    = sbMinDrawdown.getProgress();

        tvVarLabel.setText(String.format(Locale.getDefault(), "Minimum Variance: %d%%", varP));
        tvExpLabel.setText(String.format(Locale.getDefault(), "Max Sharpe Ratio: %d%%", sharpeP));
        tvMddLabel.setText(String.format(Locale.getDefault(), "Min Drawdown: %d%%", mddP));

        double[] blendedQty = optimizer.getBlendedQuantities(varP / 100.0, sharpeP / 100.0, mddP / 100.0);

        // Rebuild table
        optimizeTable.removeAllViews();

        int hintColor = getColor(R.color.textSecondary);
        int textColor = getColor(R.color.textPrimary);
        float textSizeSp = 11f;

        // --- header ---
        TableRow header = new TableRow(this);
        header.setPadding(0, 0, 0, dpToPx(2));
        header.addView(makeText("Name", hintColor, textSizeSp, Gravity.START, true));
        header.addView(makeText("ΔUnits", hintColor, textSizeSp, Gravity.END, true));
        header.addView(makeText("ΔPct", hintColor, textSizeSp, Gravity.END, true));
        optimizeTable.addView(header);

        // --- data rows ---
        for (int i = 0; i < securities.size(); i++) {
            Security s = securities.get(i);
            double origQty = s.getQuantity();
            double delta = blendedQty[i] - origQty;
            double deltaPct = (origQty != 0) ? (delta / origQty) * 100.0 : 0.0;

            int deltaColor = (Math.abs(delta) < 0.005) ? COLOR_ZERO
                           : (delta > 0) ? COLOR_POS : COLOR_NEG;

            TableRow row = new TableRow(this);
            row.setPadding(0, dpToPx(1), 0, dpToPx(1));

            // Name (stretches – column 0)
            String tag = s.isFixed() ? " ●" : "";
            row.addView(makeText(s.getDisplayName() + tag, textColor, textSizeSp, Gravity.START, false));

            // ΔUnits
            String deltaStr = String.format(Locale.getDefault(), "%+.2f", delta);
            row.addView(makeText(deltaStr, deltaColor, textSizeSp, Gravity.END, false));

            // ΔPct
            String deltaPctStr = String.format(Locale.getDefault(), "%+.1f%%", deltaPct);
            row.addView(makeText(deltaPctStr, deltaColor, textSizeSp, Gravity.END, false));

            optimizeTable.addView(row);
        }

        graphView.setSecuritiesWithQuantities(securities, blendedQty);
    }

    /** Creates a styled TextView for table cells. */
    private TextView makeText(String text, int color, float sizeSp, int gravity, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setGravity(gravity);
        tv.setSingleLine(true);
        tv.setPadding(dpToPx(4), dpToPx(1), dpToPx(4), dpToPx(1));
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
