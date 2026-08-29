package de.mm.portfoliooptimizerclassic;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optimisation UI: three SeekBars (Variance / Sharpe / Drawdown) whose
 * percentages sum to at most 100%.  The remainder = original portfolio.
 *
 * <p>The allocation table shows delta units and delta percentages based on
 * normalized portfolio allocation (current weight - original weight).</p>
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
    private ProgressBar progressBar;
    private List<Security> securities;
    private PortfolioOptimizer optimizer;

    /** Optimisation is CPU-bound, so it never runs on the UI thread. */
    private final ExecutorService optimizerExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int optimizationGeneration = 0;
    private boolean optimizationReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_optimize);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.optimize_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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
        progressBar      = findViewById(R.id.optimizeProgress);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Run the three optimisation strategies once at the current zoom level
        runOptimization((int) graphView.getCurrentVisibleCount());

        // Re-optimise only when the visible window (zoom) changes
        graphView.setOnVisibleRangeChangeListener(
                visibleCount -> runOptimization((int) visibleCount));

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

        updateSliderLabels();
    }

    /**
     * Recomputes the three strategies in the background and refreshes the UI
     * once the result is in.
     *
     * <p>BOBYQA over up to 24 assets takes long enough to stutter the UI, and
     * every zoom step triggers a new run, so the sliders stay disabled while a
     * run is in flight; that also keeps the UI from reading optimiser state
     * that the worker is still writing.</p>
     */
    private void runOptimization(int visibleCount) {
        if (securities.isEmpty()) {
            optimizationReady = true;
            updateUI();
            return;
        }

        final int generation = ++optimizationGeneration;
        setSlidersEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        optimizerExecutor.execute(() -> {
            optimizer.calculateOptimizations(visibleCount);
            mainHandler.post(() -> {
                // A newer run is already queued - let that one update the UI.
                if (generation != optimizationGeneration || isFinishing() || isDestroyed()) return;
                optimizationReady = true;
                progressBar.setVisibility(View.GONE);
                setSlidersEnabled(true);
                updateUI();
            });
        });
    }

    private void setSlidersEnabled(boolean enabled) {
        sbReduceVariance.setEnabled(enabled);
        sbMaxSharpe.setEnabled(enabled);
        sbMinDrawdown.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        optimizerExecutor.shutdownNow();
        super.onDestroy();
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
     * Shows delta units and delta percentage based on normalized allocation change.
     */
    private void updateUI() {
        updateSliderLabels();
        if (!optimizationReady) return;

        if (securities.isEmpty()) {
            optimizeTable.removeAllViews();
            TableRow empty = new TableRow(this);
            empty.addView(makeText(getString(R.string.optimize_empty),
                    getColor(R.color.textSecondary), 12f, Gravity.START, false));
            optimizeTable.addView(empty);
            graphView.setSecuritiesWithQuantities(securities, new double[0]);
            return;
        }

        int varP    = sbReduceVariance.getProgress();
        int sharpeP = sbMaxSharpe.getProgress();
        int mddP    = sbMinDrawdown.getProgress();

        double[] blendedQty = optimizer.getBlendedQuantities(varP / 100.0, sharpeP / 100.0, mddP / 100.0);
        float[] latestPrices = optimizer.getLatestPrices();

        // The optimiser is sized from the security list it was built with; if the
        // two ever drift apart, skip the refresh rather than index out of bounds.
        if (blendedQty == null || latestPrices == null
                || blendedQty.length < securities.size()
                || latestPrices.length < securities.size()) {
            return;
        }

        // Calculate total values for normalization
        double totalOrigValue = 0;
        double totalBlendedValue = 0;
        for (int i = 0; i < securities.size(); i++) {
            totalOrigValue += securities.get(i).getQuantity() * latestPrices[i];
            totalBlendedValue += blendedQty[i] * latestPrices[i];
        }

        // Rebuild table
        optimizeTable.removeAllViews();

        int hintColor = getColor(R.color.textSecondary);
        int textColor = getColor(R.color.textPrimary);
        float textSizeSp = 11f;

        // --- header ---
        TableRow header = new TableRow(this);
        header.setPadding(0, 0, 0, dpToPx(2));
        header.addView(makeText(getString(R.string.common_col_name), hintColor, textSizeSp, Gravity.START, true));
        header.addView(makeText(getString(R.string.optimize_col_delta_units), hintColor, textSizeSp, Gravity.END, true));
        header.addView(makeText(getString(R.string.optimize_col_delta_alloc), hintColor, textSizeSp, Gravity.END, true));
        optimizeTable.addView(header);

        // --- data rows ---
        for (int i = 0; i < securities.size(); i++) {
            Security s = securities.get(i);
            double origQty = s.getQuantity();
            double deltaUnits = blendedQty[i] - origQty;

            // Calculate normalized allocation change
            double origAlloc = (totalOrigValue > 0) ? (origQty * latestPrices[i]) / totalOrigValue : 0;
            double blendedAlloc = (totalBlendedValue > 0) ? (blendedQty[i] * latestPrices[i]) / totalBlendedValue : 0;
            double deltaAllocPct = (blendedAlloc - origAlloc) * 100.0;

            int deltaColor = (Math.abs(deltaUnits) < 0.005) ? COLOR_ZERO
                           : (deltaUnits > 0) ? COLOR_POS : COLOR_NEG;

            TableRow row = new TableRow(this);
            row.setPadding(0, dpToPx(1), 0, dpToPx(1));

            // Name (stretches – column 0); ● marks fixed securities
            String tag = s.isFixed() ? " ●" : "";
            row.addView(makeText(s.getDisplayName() + tag, textColor, textSizeSp, Gravity.START, false));

            // ΔUnits
            String deltaStr = String.format(Locale.getDefault(), "%+.2f", deltaUnits);
            row.addView(makeText(deltaStr, deltaColor, textSizeSp, Gravity.END, false));

            // ΔAlloc (Normalized allocation change)
            String deltaAllocStr = String.format(Locale.getDefault(), "%+.1f%%", deltaAllocPct);
            row.addView(makeText(deltaAllocStr, deltaColor, textSizeSp, Gravity.END, false));

            optimizeTable.addView(row);
        }

        graphView.setSecuritiesWithQuantities(securities, blendedQty);
    }

    /** Keeps the three slider captions in sync with their current percentages. */
    private void updateSliderLabels() {
        tvVarLabel.setText(getString(R.string.optimize_var_label, sbReduceVariance.getProgress()));
        tvExpLabel.setText(getString(R.string.optimize_exp_label, sbMaxSharpe.getProgress()));
        tvMddLabel.setText(getString(R.string.optimize_mdd_label, sbMinDrawdown.getProgress()));
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
