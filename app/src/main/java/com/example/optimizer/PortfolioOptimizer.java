package com.example.optimizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the calculation of optimized portfolio allocations based on different strategies.
 * Separation of business logic from the UI (OptimizeActivity).
 */
public class PortfolioOptimizer {

    private final List<Security> securities;
    private double[] currentQuantities;
    private double[] minVarQuantities;
    private double[] maxExpQuantities;
    private double[] minDrawdownQuantities;
    private double[] latestPrices;

    public PortfolioOptimizer(List<Security> securities) {
        this.securities = securities;
        int n = (securities != null) ? securities.size() : 0;
        this.currentQuantities = new double[n];
        this.minVarQuantities = new double[n];
        this.maxExpQuantities = new double[n];
        this.minDrawdownQuantities = new double[n];
        this.latestPrices = new double[n];
    }

    /**
     * Calculates the 100% allocation for each optimization strategy based on the visible window.
     * Strategies:
     * 1. Minimum Variance (Inverse Variance)
     * 2. Maximum Expectation (Best single asset by return)
     * 3. Minimum Drawdown (Inverse Max Drawdown)
     */
    public void calculateOptimizations(int visibleWindow) {
        if (securities == null || securities.isEmpty()) return;

        int n = securities.size();
        double currentTotalValue = 0;

        double[] variances = new double[n];
        double[] expectations = new double[n];
        double[] maxDrawdowns = new double[n];
        
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            currentQuantities[i] = s.getQuantity();
            List<Double> allValues = s.getValuesOverTime();
            if (allValues.isEmpty()) continue;
            
            int startIdx = Math.max(0, allValues.size() - visibleWindow);
            List<Double> values = allValues.subList(startIdx, allValues.size());
            
            latestPrices[i] = values.get(values.size() - 1);
            currentTotalValue += currentQuantities[i] * latestPrices[i];

            if (values.size() > 1) {
                double firstPrice = values.get(0);
                double lastPrice = values.get(values.size() - 1);
                expectations[i] = (lastPrice / firstPrice) - 1.0;

                double peak = values.get(0);
                double mdd = 0;
                double sumPeriodic = 0;
                List<Double> periodicReturns = new ArrayList<>();

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
                expectations[i] = -1.0;
                maxDrawdowns[i] = 1.0;
            }
        }

        // 1. Min Var Weights
        double invVarSum = 0;
        for (double v : variances) if (v > 0) invVarSum += (1.0 / v);

        // 2. Max Exp Index
        int bestExpIdx = 0;
        for (int i = 1; i < n; i++) {
            if (expectations[i] > expectations[bestExpIdx]) bestExpIdx = i;
        }

        // 3. Min Drawdown Weights
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

    /**
     * Calculates the blended quantities based on the current slider positions (0.0 to 1.0).
     */
    public double[] getBlendedQuantities(double varFactor, double expFactor, double mddFactor) {
        int n = securities.size();
        double currentFactor = 1.0 - varFactor - expFactor - mddFactor;
        double[] blended = new double[n];
        
        for (int i = 0; i < n; i++) {
            blended[i] = (currentQuantities[i] * currentFactor) +
                         (minVarQuantities[i] * varFactor) +
                         (maxExpQuantities[i] * expFactor) +
                         (minDrawdownQuantities[i] * mddFactor);
        }
        return blended;
    }

    public double[] getLatestPrices() {
        return latestPrices;
    }
}
