package com.example.optimizer;

import android.util.Log;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
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
 * Uses sub-selection of non-fixed securities and mapping vectors for results.
 */
public class PortfolioOptimizer {
    private static final String TAG = "PortfolioOptimizer";

    private final List<Security> securities;
    
    // Quantity vectors for the original and optimized states
    private RealVector initialQuantityVector;
    private RealVector minVarVector;
    private RealVector maxExpVector;
    private RealVector minDDVector;
    
    private double[] latestPrices;

    // Performance tuning: Cap the number of points used for optimization to keep it fast.
    private static final int MAX_OPTIMIZATION_POINTS = 256;

    public PortfolioOptimizer(List<Security> securities) {
        this.securities = securities;
        int n = (securities != null) ? securities.size() : 0;
        this.initialQuantityVector = new ArrayRealVector(n);
        this.minVarVector = new ArrayRealVector(n);
        this.maxExpVector = new ArrayRealVector(n);
        this.minDDVector = new ArrayRealVector(n);
        this.latestPrices = new double[n];
    }

    /**
     * Prepares the optimization matrix for non-fixed securities and initializes target vectors.
     */
    private int[] securitiesToMatrix(RealMatrix[] matrixOut, int visibleWindow) {
        int n = securities.size();
        
        for (int i = 0; i < n; i++) {
            double qty = securities.get(i).getQuantity();
            initialQuantityVector.setEntry(i, qty);
            minVarVector.setEntry(i, qty);
            maxExpVector.setEntry(i, qty);
            minDDVector.setEntry(i, qty);
            
            double[] history = securities.get(i).getValuesOverTime();
            latestPrices[i] = (history == null || history.length == 0) ? 0 : history[history.length - 1];
        }

        int lastCommonDay = Integer.MAX_VALUE;
        int firstCommonDayPossible = Integer.MIN_VALUE;
        for (Security s : securities) {
            int[] days = s.getEpochDays();
            if (days == null || days.length == 0) continue;
            firstCommonDayPossible = Math.max(firstCommonDayPossible, days[0]);
            lastCommonDay = Math.min(lastCommonDay, days[days.length - 1]);
        }

        if (firstCommonDayPossible > lastCommonDay) return null;

        // Define window range based on what is visible in the graph
        int endDay = lastCommonDay;
        int startDay = Math.max(firstCommonDayPossible, lastCommonDay - visibleWindow);

        List<Integer> variableIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!securities.get(i).isFixed()) {
                variableIndices.add(i);
            }
        }

        int numVar = variableIndices.size();
        if (numVar == 0) return new int[0];

        // Cap the number of sampling points to speed up optimization without losing significant detail
        int numPoints = Math.min(MAX_OPTIMIZATION_POINTS, visibleWindow);
        if (numPoints < 2) numPoints = 2;

        RealMatrix matrix = new Array2DRowRealMatrix(numVar, numPoints);
        int[] mapping = new int[numVar];

        for (int i = 0; i < numVar; i++) {
            int originalIdx = variableIndices.get(i);
            mapping[i] = originalIdx;
            // Removed hard-coded '512' to match matrix dimensions exactly
            matrix.setRow(i, securities.get(originalIdx).getValueVector(startDay, endDay, numPoints));
        }

        matrixOut[0] = matrix;
        return mapping;
    }

    private void mapResultsBack(int[] mapping, double[] gmvWeights, double[] maxExpWeights, double[] minDDWeights) {
        int numVar = mapping.length;
        double variableBudget = 0;
        for (int i = 0; i < numVar; i++) {
            int originalIdx = mapping[i];
            variableBudget += initialQuantityVector.getEntry(originalIdx) * latestPrices[originalIdx];
        }
        if (variableBudget <= 0) variableBudget = 1000.0;

        for (int i = 0; i < numVar; i++) {
            int originalIdx = mapping[i];
            double price = latestPrices[originalIdx];
            if (price > 0) {
                minVarVector.setEntry(originalIdx, (variableBudget * gmvWeights[i]) / price);
                maxExpVector.setEntry(originalIdx, (variableBudget * maxExpWeights[i]) / price);
                minDDVector.setEntry(originalIdx, (variableBudget * minDDWeights[i]) / price);
            }
        }
    }

    public void calculateOptimizations(int visibleWindow) {
        if (securities == null || securities.isEmpty()) return;

        long start = System.currentTimeMillis();

        RealMatrix[] matrixWrapper = new RealMatrix[1];
        int[] mapping = securitiesToMatrix(matrixWrapper, visibleWindow);
        
        if (mapping == null || mapping.length == 0) return;

        RealMatrix valueMatrix = matrixWrapper[0];
        long matrixTime = System.currentTimeMillis() - start;

        long subStart = System.currentTimeMillis();
        double[] gmvWeights = calculateGMVWeights(valueMatrix);
        long gmvTime = System.currentTimeMillis() - subStart;

        subStart = System.currentTimeMillis();
        double[] maxExpWeights = calculateMaxExpWeights(valueMatrix);
        long expTime = System.currentTimeMillis() - subStart;

        subStart = System.currentTimeMillis();
        double[] minDDWeights = calculateMinPortfolioDrawdownWeights(valueMatrix);
        long ddTime = System.currentTimeMillis() - subStart;

        mapResultsBack(mapping, gmvWeights, maxExpWeights, minDDWeights);
        
        long totalTime = System.currentTimeMillis() - start;
        Log.d(TAG, String.format("Optimization Stats: Total=%dms, Matrix=%dms, GMV=%dms, Exp=%dms, MinDD=%dms", 
                totalTime, matrixTime, gmvTime, expTime, ddTime));
    }

    private double[] calculateMaxExpWeights(RealMatrix window) {
        int n = window.getRowDimension();
        int m = window.getColumnDimension();
        double[] expectations = new double[n];
        for (int i = 0; i < n; i++) {
            double start = window.getEntry(i, 0);
            expectations[i] = (start != 0) ? (window.getEntry(i, m - 1) / start) - 1.0 : -1.0;
        }
        
        double[] weights = new double[n];
        int bestIdx = 0;
        for (int i = 1; i < n; i++) {
            if (expectations[i] > expectations[bestIdx]) bestIdx = i;
        }
        weights[bestIdx] = 1.0;
        return weights;
    }

    private double[] calculateGMVWeights(RealMatrix window) {
        int n = window.getRowDimension();
        int m = window.getColumnDimension();
        
        double[][] returns = new double[m - 1][n];
        for (int t = 1; t < m; t++) {
            for (int i = 0; i < n; i++) {
                double prev = window.getEntry(i, t - 1);
                returns[t - 1][i] = (prev != 0) ? (window.getEntry(i, t) - prev) / prev : 0;
            }
        }

        try {
            RealMatrix covMatrix = new Covariance(returns).getCovarianceMatrix();
            double shrinkage = 1e-4;
            for (int i = 0; i < n; i++) {
                covMatrix.setEntry(i, i, covMatrix.getEntry(i, i) + shrinkage);
            }

            DecompositionSolver solver = new LUDecomposition(covMatrix).getSolver();
            RealVector ones = new ArrayRealVector(n, 1.0);
            RealVector sigmaInvOnes = solver.solve(ones);

            double sumWeights = 0;
            for (int i = 0; i < n; i++) sumWeights += sigmaInvOnes.getEntry(i);

            double[] weights = new double[n];
            double positiveSum = 0;
            for (int i = 0; i < n; i++) {
                weights[i] = Math.max(0, sigmaInvOnes.getEntry(i) / sumWeights);
                positiveSum += weights[i];
            }
            if (positiveSum > 0) {
                for (int i = 0; i < n; i++) weights[i] /= positiveSum;
                return weights;
            }
        } catch (Exception ignored) {}

        double[] fallback = new double[n];
        for (int i = 0; i < n; i++) fallback[i] = 1.0 / n;
        return fallback;
    }

    private double[] calculateMinPortfolioDrawdownWeights(final RealMatrix window) {
        final int n = window.getRowDimension();
        final int m = window.getColumnDimension();

        MultivariateFunction objective = point -> {
            double sum = 0;
            for (double d : point) sum += Math.max(0, d);
            if (sum == 0) return 1.0;

            double maxDD = 0;
            double peak = 0;
            for (int t = 0; t < m; t++) {
                double portfolioValue = 0;
                for (int i = 0; i < n; i++) {
                    double weight = Math.max(0, point[i]) / sum;
                    double startVal = window.getEntry(i, 0);
                    portfolioValue += (startVal != 0) ? weight * (window.getEntry(i, t) / startVal) : 0;
                }
                if (portfolioValue > peak) peak = portfolioValue;
                double dd = (peak > 0) ? (peak - portfolioValue) / peak : 0;
                if (dd > maxDD) maxDD = dd;
            }
            return maxDD;
        };

        try {
            BOBYQAOptimizer optimizer = new BOBYQAOptimizer(2 * n + 1);
            double[] startPoint = new double[n];
            double[] bounds = new double[n];
            for (int i = 0; i < n; i++) {
                startPoint[i] = 1.0 / n;
                bounds[i] = 1.0;
            }

            PointValuePair result = optimizer.optimize(
                new MaxEval(500), // Reduced evaluations for speed
                new ObjectiveFunction(objective),
                GoalType.MINIMIZE,
                new InitialGuess(startPoint),
                new SimpleBounds(new double[n], bounds)
            );

            double[] bestWeights = result.getPoint();
            double sum = 0;
            for (double d : bestWeights) sum += Math.max(0, d);
            if (sum > 0) {
                for (int i = 0; i < n; i++) bestWeights[i] = Math.max(0, bestWeights[i]) / sum;
                return bestWeights;
            }
        } catch (Exception ignored) {}

        double[] fallback = new double[n];
        for (int i = 0; i < n; i++) fallback[i] = 1.0 / n;
        return fallback;
    }

    public double[] getBlendedQuantities(double varFactor, double expFactor, double mddFactor) {
        double currentFactor = Math.max(0, 1.0 - varFactor - expFactor - mddFactor);
        RealVector blended = initialQuantityVector.mapMultiply(currentFactor)
                .add(minVarVector.mapMultiply(varFactor))
                .add(maxExpVector.mapMultiply(expFactor))
                .add(minDDVector.mapMultiply(mddFactor));
        return blended.toArray();
    }

    public double[] getLatestPrices() {
        return latestPrices;
    }
}
