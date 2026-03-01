package com.example.optimizer;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.stat.correlation.Covariance;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the calculation of optimized portfolio allocations based on different strategies.
 * Now uses Apache Commons Math for Global Minimum Variance and Minimum Portfolio Drawdown optimization.
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
     */
    public void calculateOptimizations(int visibleWindow) {
        if (securities == null || securities.isEmpty()) return;

        int n = securities.size();
        double currentTotalValue = 0;

        double[] expectations = new double[n];
        List<double[]> allReturns = new ArrayList<>();
        List<List<Double>> windowValues = new ArrayList<>();
        
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            currentQuantities[i] = s.getQuantity();
            List<Double> allValues = s.getValuesOverTime();
            if (allValues.isEmpty()) {
                allReturns.add(new double[0]);
                windowValues.add(new ArrayList<>());
                continue;
            }
            
            int startIdx = Math.max(0, allValues.size() - visibleWindow);
            List<Double> values = allValues.subList(startIdx, allValues.size());
            windowValues.add(values);
            
            latestPrices[i] = values.get(values.size() - 1);
            currentTotalValue += currentQuantities[i] * latestPrices[i];

            if (values.size() > 1) {
                double firstPrice = values.get(0);
                double lastPrice = values.get(values.size() - 1);
                expectations[i] = (lastPrice / firstPrice) - 1.0;

                double[] periodicReturns = new double[values.size() - 1];
                for (int j = 1; j < values.size(); j++) {
                    periodicReturns[j - 1] = (values.get(j) - values.get(j - 1)) / values.get(j - 1);
                }
                allReturns.add(periodicReturns);
            } else {
                allReturns.add(new double[0]);
                expectations[i] = -1.0;
            }
        }

        // 1. Global Minimum Variance (GMV) Weights
        double[] gmvWeights = calculateGMVWeights(allReturns);

        // 2. Max Exp Index
        int bestExpIdx = 0;
        for (int i = 1; i < n; i++) {
            if (expectations[i] > expectations[bestExpIdx]) bestExpIdx = i;
        }

        // 3. Minimum Portfolio Drawdown Weights
        double[] minDDWeights = calculateMinPortfolioDrawdownWeights(windowValues);

        // Calculate total value for scaling
        // If currentTotalValue is 0 (all assets have 0 quantity), we use a nominal value of 1000.0 for scaling
        double scalingValue = (currentTotalValue > 0) ? currentTotalValue : 1000.0;

        for (int i = 0; i < n; i++) {
            if (latestPrices[i] > 0) {
                minVarQuantities[i] = (scalingValue * gmvWeights[i]) / latestPrices[i];

                double eWeight = (i == bestExpIdx) ? 1.0 : 0.0;
                maxExpQuantities[i] = (scalingValue * eWeight) / latestPrices[i];

                minDrawdownQuantities[i] = (scalingValue * minDDWeights[i]) / latestPrices[i];
            }
        }
    }

    private double[] calculateGMVWeights(List<double[]> allReturns) {
        int n = allReturns.size();
        if (n == 0) return new double[0];

        int minLen = Integer.MAX_VALUE;
        for (double[] r : allReturns) {
            if (r.length > 0) minLen = Math.min(minLen, r.length);
        }

        if (minLen < 2 || minLen < n) {
            return fallbackInverseVariance(allReturns);
        }

        double[][] matrixData = new double[minLen][n];
        for (int j = 0; j < n; j++) {
            double[] r = allReturns.get(j);
            for (int i = 0; i < minLen; i++) {
                matrixData[i][j] = r[i];
            }
        }

        try {
            RealMatrix covMatrix = new Covariance(matrixData).getCovarianceMatrix();
            double shrinkage = 1e-4;
            for (int i = 0; i < n; i++) {
                covMatrix.setEntry(i, i, covMatrix.getEntry(i, i) + shrinkage);
            }

            DecompositionSolver solver = new LUDecomposition(covMatrix).getSolver();
            RealVector ones = new ArrayRealVector(n, 1.0);
            RealVector sigmaInvOnes = solver.solve(ones);

            double sumWeights = 0;
            for (int i = 0; i < n; i++) {
                sumWeights += sigmaInvOnes.getEntry(i);
            }

            double[] weights = new double[n];
            double positiveSum = 0;
            for (int i = 0; i < n; i++) {
                weights[i] = Math.max(0, sigmaInvOnes.getEntry(i) / sumWeights);
                positiveSum += weights[i];
            }

            if (positiveSum > 0) {
                for (int i = 0; i < n; i++) weights[i] /= positiveSum;
            } else {
                return fallbackInverseVariance(allReturns);
            }
            return weights;

        } catch (Exception e) {
            return fallbackInverseVariance(allReturns);
        }
    }

    private double[] calculateMinPortfolioDrawdownWeights(final List<List<Double>> windowValues) {
        final int n = windowValues.size();
        if (n == 0) return new double[0];
        if (n == 1) return new double[]{1.0};

        int tempMinLen = Integer.MAX_VALUE;
        for (List<Double> v : windowValues) {
            if (!v.isEmpty()) tempMinLen = Math.min(tempMinLen, v.size());
        }
        final int minLen = tempMinLen;
        
        if (minLen < 2) {
            double[] fallback = new double[n];
            for (int i = 0; i < n; i++) fallback[i] = 1.0 / n;
            return fallback;
        }

        MultivariateFunction objective = new MultivariateFunction() {
            @Override
            public double value(double[] point) {
                double sum = 0;
                for (double d : point) sum += Math.max(0, d);
                if (sum == 0) return 1.0; 
                
                double[] normalized = new double[n];
                for (int i = 0; i < n; i++) normalized[i] = Math.max(0, point[i]) / sum;

                double maxDD = 0;
                double peak = 0;
                for (int t = 0; t < minLen; t++) {
                    double portfolioValue = 0;
                    for (int i = 0; i < n; i++) {
                        portfolioValue += normalized[i] * (windowValues.get(i).get(t) / windowValues.get(i).get(0));
                    }
                    if (portfolioValue > peak) peak = portfolioValue;
                    double dd = (peak > 0) ? (peak - portfolioValue) / peak : 0;
                    if (dd > maxDD) maxDD = dd;
                }
                return maxDD;
            }
        };

        try {
            int interpolationPoints = 2 * n + 1;
            BOBYQAOptimizer optimizer = new BOBYQAOptimizer(interpolationPoints);
            
            double[] startPoint = new double[n];
            double[] lowerBounds = new double[n];
            double[] upperBounds = new double[n];
            for (int i = 0; i < n; i++) {
                startPoint[i] = 1.0 / n;
                lowerBounds[i] = 0.0;
                upperBounds[i] = 1.0;
            }

            PointValuePair result = optimizer.optimize(
                new MaxEval(1000),
                new ObjectiveFunction(objective),
                GoalType.MINIMIZE,
                new InitialGuess(startPoint),
                new SimpleBounds(lowerBounds, upperBounds)
            );

            double[] bestWeights = result.getPoint();
            double sum = 0;
            for (double d : bestWeights) sum += Math.max(0, d);
            if (sum > 0) {
                for (int i = 0; i < n; i++) bestWeights[i] = Math.max(0, bestWeights[i]) / sum;
                return bestWeights;
            }
        } catch (Exception e) {
            // Optimization failed, use fallback
        }
        
        double[] fallback = new double[n];
        for (int i = 0; i < n; i++) fallback[i] = 1.0 / n;
        return fallback;
    }

    private double[] fallbackInverseVariance(List<double[]> allReturns) {
        int n = allReturns.size();
        double[] variances = new double[n];
        double invVarSum = 0;
        for (int i = 0; i < n; i++) {
            double[] r = allReturns.get(i);
            if (r.length > 1) {
                double mean = 0, var = 0;
                for (double val : r) mean += val;
                mean /= r.length;
                for (double val : r) var += (val - mean) * (val - mean);
                variances[i] = var / (r.length - 1);
            } else {
                variances[i] = 1.0;
            }
            if (variances[i] > 0) invVarSum += (1.0 / variances[i]);
        }

        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            weights[i] = (variances[i] > 0) ? (1.0 / variances[i]) / invVarSum : (1.0 / n);
        }
        return weights;
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
