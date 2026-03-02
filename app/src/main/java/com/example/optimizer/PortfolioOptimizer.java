package com.example.optimizer;

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
 * Uses a value matrix (Securities x Days) and quantity vectors for efficient processing.
 */
public class PortfolioOptimizer {

    private final List<Security> securities;
    private RealVector currentVector;
    private RealVector minVarVector;
    private RealVector maxExpVector;
    private RealVector minDDVector;
    private double[] latestPrices;

    public PortfolioOptimizer(List<Security> securities) {
        this.securities = securities;
        int n = (securities != null) ? securities.size() : 0;
        this.currentVector = new ArrayRealVector(n);
        this.minVarVector = new ArrayRealVector(n);
        this.maxExpVector = new ArrayRealVector(n);
        this.minDDVector = new ArrayRealVector(n);
        this.latestPrices = new double[n];
    }

    /**
     * Initializes target quantity vectors and prepares the value matrix for non-fixed securities.
     * @param matrixOut An array to return the resulting RealMatrix.
     * @return The mapping index vector (array of original security indices).
     */
    private int[] securitiesToMatrix(RealMatrix[] matrixOut) {
        int n = securities.size();
        
        // 1. Initialize quantity vectors with current quantity
        currentVector = new ArrayRealVector(n);
        minVarVector = new ArrayRealVector(n);
        maxExpVector = new ArrayRealVector(n);
        minDDVector = new ArrayRealVector(n);
        
        for (int i = 0; i < n; i++) {
            double qty = securities.get(i).getQuantity();
            currentVector.setEntry(i, qty);
            minVarVector.setEntry(i, qty);
            maxExpVector.setEntry(i, qty);
            minDDVector.setEntry(i, qty);
            
            List<Double> values = securities.get(i).getValuesOverTime();
            latestPrices[i] = values.isEmpty() ? 0 : values.get(values.size() - 1);
        }

        // 2. Identify start and end date
        int firstCommonDay = Integer.MIN_VALUE;
        int lastCommonDay = Integer.MAX_VALUE;
        for (Security s : securities) {
            List<Integer> days = s.getEpochDays();
            if (days.isEmpty()) continue;
            firstCommonDay = Math.max(firstCommonDay, days.get(0));
            lastCommonDay = Math.min(lastCommonDay, days.get(days.size() - 1));
        }

        if (firstCommonDay > lastCommonDay) return null;

        // 3. Create mapping index vector for non-fixed securities
        List<Integer> variableIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!securities.get(i).isFixed()) {
                variableIndices.add(i);
            }
        }

        int numVar = variableIndices.size();
        if (numVar == 0) return new int[0];

        int numDays = lastCommonDay - firstCommonDay + 1;
        RealMatrix matrix = new Array2DRowRealMatrix(numVar, numDays);
        int[] mapping = new int[numVar];

        // 4. Fill matrix rows and mapping vector
        for (int i = 0; i < numVar; i++) {
            int originalIdx = variableIndices.get(i);
            mapping[i] = originalIdx;
            matrix.setRow(i, securities.get(originalIdx).getValueVector(firstCommonDay, lastCommonDay));
        }

        matrixOut[0] = matrix;
        return mapping;
    }

    /**
     * Re-calculates the target quantities for each optimization strategy.
     */
    public void calculateOptimizations(int visibleWindow) {
        RealMatrix[] matrixWrapper = new RealMatrix[1];
        int[] mapping = securitiesToMatrix(matrixWrapper);
        
        if (mapping == null || mapping.length == 0) return;

        RealMatrix variableMatrix = matrixWrapper[0];
        int numVar = mapping.length;
        int numDays = variableMatrix.getColumnDimension();

        // 1. Extract sub-window for optimization
        int windowSize = Math.min(visibleWindow, numDays);
        int windowStart = numDays - windowSize;
        RealMatrix windowMatrix = variableMatrix.getSubMatrix(0, numVar - 1, windowStart, numDays - 1);

        // 2. Perform optimizations to get target weights for the variable subset
        double[] gmvWeights = calculateGMVWeights(windowMatrix);
        double[] maxExpWeights = calculateMaxExpWeights(windowMatrix);
        double[] minDDWeights = calculateMinPortfolioDrawdownWeights(windowMatrix);

        // 3. Map weights back to full target quantity vectors
        mapResultsBack(mapping, gmvWeights, maxExpWeights, minDDWeights);
    }

    /**
     * Maps optimization results from the variable subset back to the full target quantity vectors.
     */
    private void mapResultsBack(int[] mapping, double[] gmvWeights, double[] maxExpWeights, double[] minDDWeights) {
        int numVar = mapping.length;
        
        // Calculate the total value of the variable portion to keep it constant after optimization
        double variableTotalValue = 0;
        for (int i = 0; i < numVar; i++) {
            int originalIdx = mapping[i];
            variableTotalValue += currentVector.getEntry(originalIdx) * latestPrices[originalIdx];
        }
        double scalingValue = (variableTotalValue > 0) ? variableTotalValue : 1000.0;

        for (int i = 0; i < numVar; i++) {
            int originalIdx = mapping[i];
            double price = latestPrices[originalIdx];
            if (price > 0) {
                minVarVector.setEntry(originalIdx, (scalingValue * gmvWeights[i]) / price);
                maxExpVector.setEntry(originalIdx, (scalingValue * maxExpWeights[i]) / price);
                minDDVector.setEntry(originalIdx, (scalingValue * minDDWeights[i]) / price);
            } else {
                minVarVector.setEntry(originalIdx, 0);
                maxExpVector.setEntry(originalIdx, 0);
                minDDVector.setEntry(originalIdx, 0);
            }
        }
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
                new MaxEval(1000),
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
        
        RealVector blended = currentVector.mapMultiply(currentFactor)
                .add(minVarVector.mapMultiply(varFactor))
                .add(maxExpVector.mapMultiply(expFactor))
                .add(minDDVector.mapMultiply(mddFactor));

        return blended.toArray();
    }

    public double[] getLatestPrices() {
        return latestPrices;
    }
}
