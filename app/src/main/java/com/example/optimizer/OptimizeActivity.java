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
    private List<Security> securities;

    private double[] currentQuantities;
    private double[] minVarQuantities;
    private double[] maxExpQuantities;
    private double[] minDrawdownQuantities;

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

        graphView = findViewById(R.id.optimizeGraph);
        sbReduceVariance = findViewById(R.id.sbReduceVariance);
        sbMaxExpectation = findViewById(R.id.sbMaxExpectation);
        sbMinDrawdown = findViewById(R.id.sbMinDrawdown);
        tvVarLabel = findViewById(R.id.tvVarLabel);
        tvExpLabel = findViewById(R.id.tvExpLabel);
        tvMddLabel = findViewById(R.id.tvMddLabel);
        tvQuantities = findViewById(R.id.tvQuantities);
        Button btnBack = findViewById(R.id.btnBack);

        // Calculate optimization whenever the visible range changes
        graphView.setOnVisibleRangeChangeListener(visibleCount -> {
            calculateOptimization((int) visibleCount);
            updateUI();
        });

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    adjustSliders(seekBar);
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
        
        // Initial setup
        updateUI();
    }

    /**
     * Passt die Regler an, wenn einer verändert wird. Wenn die Summe der Regler 100% übersteigt,
     * werden die anderen Regler proportional reduziert, um immer eine Gesamtsumme von 100% zu halten.
     */
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

    /**
     * Reduziert zwei Regler proportional zu ihren aktuellen Werten, um den Überschuss auszugleichen.
     * @param excess Die Menge, um die die Regler reduziert werden müssen
     * @param s1 Der erste Regler
     * @param s2 Der zweite Regler
     */
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
     * Berechnet die optimalen Portfoliogewichtungen basierend auf drei Strategien:
     * 1. Minimale Varianz - inverse Varianzgewichtung
     * 2. Maximale Erwartung - volle Gewichtung auf das beste Asset
     * 3. Minimales Drawdown - inverse Drawdown-Gewichtung
     * Die Berechnung erfolgt über das sichtbare Zeitfenster des Graphen.
     *
     * @param visibleWindow Die Anzahl der Datenpunkte, die auf dem Bildschirm sichtbar sind
     */
    private void calculateOptimization(int visibleWindow) {
        if (securities == null || securities.isEmpty()) return;

        int n = securities.size();
        currentQuantities = new double[n];
        minVarQuantities = new double[n];
        maxExpQuantities = new double[n];
        minDrawdownQuantities = new double[n];
        double currentTotalValue = 0;

        double[] variances = new double[n];
        double[] expectations = new double[n];
        double[] maxDrawdowns = new double[n];
        double[] latestPrices = new double[n];
        
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            currentQuantities[i] = s.getQuantity();
            List<Double> allValues = s.getValuesOverTime();
            if (allValues.isEmpty()) continue;
            
            // Align with the window shown on screen (last 'visibleWindow' points of the graph duration)
            int startIdx = Math.max(0, allValues.size() - visibleWindow);
            List<Double> values = allValues.subList(startIdx, allValues.size());
            
            latestPrices[i] = values.get(values.size() - 1);
            currentTotalValue += currentQuantities[i] * latestPrices[i];

            if (values.size() > 1) {
                // Return metric: Use Total Return over the visible period to match visual "slope"
                double firstPrice = values.get(0);
                double lastPrice = values.get(values.size() - 1);
                expectations[i] = (lastPrice / firstPrice) - 1.0;

                // Periodic returns for Variance and Drawdown
                List<Double> periodicReturns = new ArrayList<>();
                double peak = values.get(0);
                double mdd = 0;
                double sumPeriodic = 0;
                
                for (int j = 1; j < values.size(); j++) {
                    double val = values.get(j);
                    double prev = values.get(j - 1);
                    double r = (val - prev) / prev;
                    periodicReturns.add(r);
                    sumPeriodic += r;
                    
                    if (val > peak) peak = val;
                    double dd = (peak - val) / peak;
                    if (dd > mdd) mdd = dd;
                }
                
                double avgPeriodic = sumPeriodic / periodicReturns.size();
                double sqSum = 0;
                for (double r : periodicReturns) sqSum += (r - avgPeriodic) * (r - avgPeriodic);
                
                variances[i] = sqSum / periodicReturns.size();
                maxDrawdowns[i] = Math.max(0.01, mdd);
            } else {
                variances[i] = 1.0;
                expectations[i] = -1.0; // Minimal expectation for single point
                maxDrawdowns[i] = 1.0;
            }
        }

        // 1. Min Var Weights
        double invVarSum = 0;
        for (double v : variances) if (v > 0) invVarSum += (1.0 / v);
        
        // 2. Max Exp (Best single asset by Total Return in window)
        int bestExpIdx = 0;
        for (int i = 1; i < n; i++) {
            if (expectations[i] > expectations[bestExpIdx]) {
                bestExpIdx = i;
            }
        }

        // 3. Min Drawdown Weights (Inverse MDD)
        double invMddSum = 0;
        for (double m : maxDrawdowns) invMddSum += (1.0 / m);

        for (int i = 0; i < n; i++) {
            if (latestPrices[i] > 0) {
                // Min Variance Allocation
                double vWeight = (variances[i] > 0) ? (1.0 / variances[i]) / invVarSum : 0;
                minVarQuantities[i] = (currentTotalValue * vWeight) / latestPrices[i];
                
                // Max Expectation Allocation (Full weight to the steepest asset in window)
                double eWeight = (i == bestExpIdx) ? 1.0 : 0.0;
                maxExpQuantities[i] = (currentTotalValue * eWeight) / latestPrices[i];

                // Min Drawdown Allocation
                double mWeight = (1.0 / maxDrawdowns[i]) / invMddSum;
                minDrawdownQuantities[i] = (currentTotalValue * mWeight) / latestPrices[i];
            }
        }
    }

    /**
     * Aktualisiert die Benutzeroberfläche mit den aktuellen Reglerpositionen und den berechneten
     * Portfoliogewichtungen. Kombiniert die drei Optimierungsstrategien basierend auf den Schiebereglerprozentsätzen.
     */
    private void updateUI() {
        if (graphView != null) {
            calculateOptimization((int) graphView.getCurrentVisibleCount());
        }

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

        if (securities == null) return;
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

    /**
     * Verarbeitet den Klick auf den "Zurück"-Button in der Aktionsleiste.
     * Schließt die aktuelle Activity und kehrt zur vorherigen Activity zurück.
     */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
