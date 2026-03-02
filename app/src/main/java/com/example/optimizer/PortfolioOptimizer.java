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
 * Three portfolio-optimisation strategies plus linear blending.
 *
 * <ol>
 *   <li><b>GMV</b> – Global Minimum Variance (Markowitz, long-only, shrinkage)</li>
 *   <li><b>Max Sharpe</b> – Maximum Sharpe Ratio / Tangency Portfolio</li>
 *   <li><b>MinDD</b> – Minimise maximum drawdown (BOBYQA derivative-free)</li>
 * </ol>
 */
public class PortfolioOptimizer {
    private static final String TAG = "PortfolioOptimizer";

    private final List<Security> securities;

    private RealVector initialQuantityVector;
    private RealVector minVarVector;
    private RealVector maxSharpeVector;   // was maxExpVector
    private RealVector minDDVector;

    private float[] latestPrices;

    private static final int MAX_OPTIMIZATION_POINTS = 256;

    // Reusable buffers for getValueVector calls (avoids per-security allocation)
    private int[]   targetDaysBuf;
    private float[] valueBuf;

    public PortfolioOptimizer(List<Security> securities) {
        this.securities = securities;
        int n = (securities != null) ? securities.size() : 0;
        this.initialQuantityVector = new ArrayRealVector(n);
        this.minVarVector     = new ArrayRealVector(n);
        this.maxSharpeVector  = new ArrayRealVector(n);
        this.minDDVector      = new ArrayRealVector(n);
        this.latestPrices     = new float[n];
    }

    // ── Matrix construction ─────────────────────────────────────────────────

    /**
     * Builds a value matrix for the non-fixed securities over the visible window.
     *
     * <p>Reads the common startDay/endDay from the securities (set by Portfolio)
     * and limits the window to the last {@code visibleWindow} days.</p>
     *
     * @return mapping int[] (variable-index → original-index), null if no overlap
     */
    private int[] securitiesToMatrix(RealMatrix[] matrixOut, int visibleWindow) {
        int n = securities.size();

        // snapshot quantities and latest prices
        for (int i = 0; i < n; i++) {
            double qty = securities.get(i).getQuantity();
            initialQuantityVector.setEntry(i, qty);
            minVarVector.setEntry(i, qty);
            maxSharpeVector.setEntry(i, qty);
            minDDVector.setEntry(i, qty);

            float[] hist = securities.get(i).getValuesOverTime();
            latestPrices[i] = (hist == null || hist.length == 0) ? 0f : hist[hist.length - 1];
        }

        // use pre-computed common range (all securities share the same window)
        if (n == 0) return null;
        int commonStart = securities.get(0).getStartDay();
        int commonEnd   = securities.get(0).getEndDay();
        if (commonStart >= commonEnd) return null;

        int endDay   = commonEnd;
        int startDay = Math.max(commonStart, commonEnd - visibleWindow);

        // collect non-fixed indices
        List<Integer> varIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!securities.get(i).isFixed()) varIdx.add(i);
        }
        int numVar = varIdx.size();
        if (numVar == 0) return new int[0];

        int numPoints = Math.min(MAX_OPTIMIZATION_POINTS, visibleWindow);
        if (numPoints < 2) numPoints = 2;

        // (Re)allocate shared buffers only when size changes
        if (targetDaysBuf == null || targetDaysBuf.length != numPoints) {
            targetDaysBuf = new int[numPoints];
            valueBuf      = new float[numPoints];
        }

        RealMatrix matrix = new Array2DRowRealMatrix(numVar, numPoints);
        int[] mapping = new int[numVar];

        for (int i = 0; i < numVar; i++) {
            int origIdx = varIdx.get(i);
            mapping[i] = origIdx;

            // Write into shared buffers – no per-security allocation
            securities.get(origIdx).getValueVectorInto(startDay, endDay, targetDaysBuf, valueBuf);

            // commons-math needs double[] – unavoidable conversion
            double[] dv = new double[numPoints];
            for (int j = 0; j < numPoints; j++) dv[j] = valueBuf[j];
            matrix.setRow(i, dv);
        }

        matrixOut[0] = matrix;
        return mapping;
    }

    // ── Map weights back to quantities ──────────────────────────────────────

    /**
     * Converts normalised weight vectors back to share quantities by
     * distributing the variable-securities budget proportionally.
     */
    private void mapResultsBack(int[] mapping, double[] gmvW, double[] sharpeW, double[] ddW) {
        float budget = 0;
        for (int i = 0; i < mapping.length; i++) {
            int idx = mapping[i];
            budget += (float) initialQuantityVector.getEntry(idx) * latestPrices[idx];
        }
        if (budget <= 0) budget = 1000.0f;

        for (int i = 0; i < mapping.length; i++) {
            int idx = mapping[i];
            float price = latestPrices[idx];
            if (price > 0) {
                minVarVector.setEntry(idx,    (budget * gmvW[i])    / price);
                maxSharpeVector.setEntry(idx, (budget * sharpeW[i]) / price);
                minDDVector.setEntry(idx,     (budget * ddW[i])     / price);
            }
        }
    }

    // ── Public entry ────────────────────────────────────────────────────────

    /**
     * Runs all three strategies.  Call when the visible window changes (zoom).
     * Slider movement only needs {@link #getBlendedQuantities}.
     */
    public void calculateOptimizations(int visibleWindow) {
        if (securities == null || securities.isEmpty()) return;

        long t0 = System.currentTimeMillis();

        RealMatrix[] wrap = new RealMatrix[1];
        int[] mapping = securitiesToMatrix(wrap, visibleWindow);
        if (mapping == null || mapping.length == 0) return;
        RealMatrix vm = wrap[0];
        long tMatrix = System.currentTimeMillis() - t0;

        // Pre-compute returns + covariance (shared by GMV and MaxSharpe)
        int nAssets = vm.getRowDimension();
        int nPts    = vm.getColumnDimension();
        double[][] returns = computeReturns(vm, nAssets, nPts);

        RealMatrix cov = null;
        try {
            cov = new Covariance(returns).getCovarianceMatrix();
            for (int i = 0; i < nAssets; i++) cov.setEntry(i, i, cov.getEntry(i, i) + 1e-4);
        } catch (Exception ignored) {}

        long ts = System.currentTimeMillis();
        double[] gmv = calculateGMVWeights(nAssets, cov);
        long tGmv = System.currentTimeMillis() - ts;

        ts = System.currentTimeMillis();
        double[] sharpe = calculateMaxSharpeWeights(nAssets, returns, cov);
        long tSharpe = System.currentTimeMillis() - ts;

        ts = System.currentTimeMillis();
        double[] dd = calculateMinPortfolioDrawdownWeights(vm);
        long tDd = System.currentTimeMillis() - ts;

        mapResultsBack(mapping, gmv, sharpe, dd);

        Log.d(TAG, String.format("Opt: Total=%dms Matrix=%dms GMV=%dms Sharpe=%dms MinDD=%dms",
                System.currentTimeMillis() - t0, tMatrix, tGmv, tSharpe, tDd));
    }

    // ── Shared: compute return matrix ───────────────────────────────────────

    /** Period returns r[t][asset] = (price[t+1]-price[t])/price[t]. */
    private double[][] computeReturns(RealMatrix window, int n, int m) {
        double[][] ret = new double[m - 1][n];
        for (int t = 1; t < m; t++) {
            for (int i = 0; i < n; i++) {
                double prev = window.getEntry(i, t - 1);
                ret[t - 1][i] = (prev != 0) ? (window.getEntry(i, t) - prev) / prev : 0;
            }
        }
        return ret;
    }

    // ── Strategy 1: Maximum Sharpe Ratio (Tangency Portfolio) ────────────────

    /**
     * w = Σ⁻¹·(μ - rf) / (1ᵀ·Σ⁻¹·(μ - rf))   with rf = 0.
     * Long-only: negatives clamped to 0 then re-normalised.
     * Falls back to equal weights on numerical failure.
     */
    private double[] calculateMaxSharpeWeights(int n, double[][] returns, RealMatrix cov) {
        if (cov == null) return equalWeights(n);

        try {
            // mean return per asset
            double[] mu = new double[n];
            int T = returns.length;
            for (int t = 0; t < T; t++) {
                for (int i = 0; i < n; i++) mu[i] += returns[t][i];
            }
            for (int i = 0; i < n; i++) mu[i] /= T;

            DecompositionSolver solver = new LUDecomposition(cov).getSolver();
            RealVector muVec = new ArrayRealVector(mu);
            RealVector sInvMu = solver.solve(muVec);

            double[] w = new double[n];
            double posSum = 0;
            for (int i = 0; i < n; i++) {
                w[i] = Math.max(0, sInvMu.getEntry(i));
                posSum += w[i];
            }
            if (posSum > 0) {
                for (int i = 0; i < n; i++) w[i] /= posSum;
                return w;
            }
        } catch (Exception ignored) {}

        return equalWeights(n);
    }

    // ── Strategy 2: Global Minimum Variance ─────────────────────────────────

    /**
     * Markowitz GMV: w = Σ⁻¹·1 / (1ᵀ·Σ⁻¹·1).
     * Receives pre-computed covariance matrix with shrinkage already applied.
     */
    private double[] calculateGMVWeights(int n, RealMatrix cov) {
        if (cov == null) return equalWeights(n);

        try {
            DecompositionSolver solver = new LUDecomposition(cov).getSolver();
            RealVector ones = new ArrayRealVector(n, 1.0);
            RealVector sInv1 = solver.solve(ones);

            double sum = 0;
            for (int i = 0; i < n; i++) sum += sInv1.getEntry(i);

            double[] w = new double[n];
            double posSum = 0;
            for (int i = 0; i < n; i++) {
                w[i] = Math.max(0, sInv1.getEntry(i) / sum);
                posSum += w[i];
            }
            if (posSum > 0) {
                for (int i = 0; i < n; i++) w[i] /= posSum;
                return w;
            }
        } catch (Exception ignored) {}

        return equalWeights(n);
    }

    // ── Strategy 3: Minimum Maximum Drawdown ────────────────────────────────

    /**
     * BOBYQA derivative-free optimiser minimises the worst drawdown of the
     * combined portfolio over the window.
     */
    private double[] calculateMinPortfolioDrawdownWeights(final RealMatrix window) {
        final int n = window.getRowDimension();
        final int m = window.getColumnDimension();

        MultivariateFunction objective = point -> {
            double sum = 0;
            for (double d : point) sum += Math.max(0, d);
            if (sum == 0) return 1.0;

            double maxDD = 0, peak = 0;
            for (int t = 0; t < m; t++) {
                double pv = 0;
                for (int i = 0; i < n; i++) {
                    double w = Math.max(0, point[i]) / sum;
                    double s0 = window.getEntry(i, 0);
                    pv += (s0 != 0) ? w * (window.getEntry(i, t) / s0) : 0;
                }
                if (pv > peak) peak = pv;
                double dd = (peak > 0) ? (peak - pv) / peak : 0;
                if (dd > maxDD) maxDD = dd;
            }
            return maxDD;
        };

        try {
            BOBYQAOptimizer opt = new BOBYQAOptimizer(2 * n + 1);
            double[] sp = new double[n];
            double[] ub = new double[n];
            for (int i = 0; i < n; i++) { sp[i] = 1.0 / n; ub[i] = 1.0; }

            PointValuePair res = opt.optimize(
                    new MaxEval(500),
                    new ObjectiveFunction(objective),
                    GoalType.MINIMIZE,
                    new InitialGuess(sp),
                    new SimpleBounds(new double[n], ub));

            double[] best = res.getPoint();
            double sum = 0;
            for (double d : best) sum += Math.max(0, d);
            if (sum > 0) {
                for (int i = 0; i < n; i++) best[i] = Math.max(0, best[i]) / sum;
                return best;
            }
        } catch (Exception ignored) {}

        return equalWeights(n);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static double[] equalWeights(int n) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = 1.0 / n;
        return w;
    }

    // ── Blending ────────────────────────────────────────────────────────────

    /**
     * Blended quantities: (1-v-s-m)*original + v*GMV + s*MaxSharpe + m*MinDD.
     */
    public double[] getBlendedQuantities(double varF, double sharpeF, double mddF) {
        double curF = Math.max(0, 1.0 - varF - sharpeF - mddF);
        RealVector blended = initialQuantityVector.mapMultiply(curF)
                .add(minVarVector.mapMultiply(varF))
                .add(maxSharpeVector.mapMultiply(sharpeF))
                .add(minDDVector.mapMultiply(mddF));
        return blended.toArray();
    }

    /** Latest known price per security (float – matches Security data). */
    public float[] getLatestPrices() {
        return latestPrices;
    }
}
