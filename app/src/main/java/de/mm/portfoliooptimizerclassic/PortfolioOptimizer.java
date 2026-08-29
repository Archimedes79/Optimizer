package de.mm.portfoliooptimizerclassic;

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
 *
 * <p><b>Fixed securities</b> ({@link Security#isFixed()}) are excluded from all
 * three optimisations.  Their quantities stay at the original value in every
 * optimised vector, so that the budget they occupy (e.g. 4% of the portfolio)
 * is never redistributed.  Only the remaining non-fixed budget is optimised.
 * This prevents constant-value or strategic positions from being over-weighted
 * by variance or drawdown minimisation.</p>
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
    /** Below this many samples the covariance estimate is noise, not information. */
    private static final int MIN_OPTIMIZATION_POINTS = 20;
    /** Share of the average variance blended into the covariance matrix. */
    private static final double SHRINKAGE = 0.05;

    /** False while the window holds too little data for a meaningful optimisation. */
    private volatile boolean resultAvailable = false;

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
        int n = Math.min(securities.size(), initialQuantityVector.getDimension());

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

        // Common range over every security that actually carries data. Reading it
        // from securities.get(0) alone made the whole optimisation depend on which
        // position happened to be first in the list.
        if (n == 0) return null;
        int commonStart = Integer.MIN_VALUE;
        int commonEnd   = Integer.MAX_VALUE;
        boolean anyData = false;
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            if (s.getNumberOfEntries() == 0 || s.getCommonRangeLength() < 2) continue;
            commonStart = Math.max(commonStart, s.getStartDay());
            commonEnd   = Math.min(commonEnd,   s.getEndDay());
            anyData = true;
        }
        if (!anyData || commonStart >= commonEnd) return null;

        int endDay = commonEnd;
        // [startDay, endDay] is inclusive, so it spans exactly visibleWindow days.
        int startDay   = Math.max(commonStart, commonEnd - (visibleWindow - 1));
        int windowDays = endDay - startDay + 1;

        // Optimisable = not fixed, and with a price we can convert weights back into.
        List<Integer> varIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Security s = securities.get(i);
            if (s.isFixed()) {
                Log.d(TAG, "Fixed (excluded): " + s.getDisplayName());
            } else if (latestPrices[i] <= 0 || s.getCommonRangeLength() < 2) {
                // Such a security would still be handed a weight, but its value could
                // never be turned back into shares - that budget would simply vanish.
                Log.w(TAG, "No usable history (excluded): " + s.getDisplayName());
            } else {
                varIdx.add(i);
            }
        }
        int numVar = varIdx.size();
        Log.d(TAG, "Optimising " + numVar + " of " + n + " securities (rest fixed or without data)");
        if (numVar == 0) return new int[0];

        // Never sample more points than the window has days: oversampling produces
        // runs of identical days, and the resulting zero returns collapse the variance.
        int numPoints = Math.min(MAX_OPTIMIZATION_POINTS, windowDays);
        if (numPoints < MIN_OPTIMIZATION_POINTS) {
            Log.w(TAG, "Only " + numPoints + " samples in the window - not optimising");
            return null;
        }

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
        double budget = 0;
        for (int i = 0; i < mapping.length; i++) {
            int idx = mapping[i];
            budget += initialQuantityVector.getEntry(idx) * latestPrices[idx];
        }
        if (budget <= 0) {
            // Nothing to redistribute. Inventing a budget here used to conjure up
            // holdings that the portfolio does not have.
            Log.d(TAG, "Variable part is worth nothing - keeping the current quantities");
            return;
        }

        for (int i = 0; i < mapping.length; i++) {
            int idx = mapping[i];
            float price = latestPrices[idx];   // > 0, guaranteed by securitiesToMatrix
            minVarVector.setEntry(idx,    (budget * gmvW[i])    / price);
            maxSharpeVector.setEntry(idx, (budget * sharpeW[i]) / price);
            minDDVector.setEntry(idx,     (budget * ddW[i])     / price);
        }

        // ── Sanity check: portfolio value must be conserved ─────────────
        verifyValueConservation("GMV",      minVarVector);
        verifyValueConservation("MaxSharpe", maxSharpeVector);
        verifyValueConservation("MinDD",    minDDVector);
    }

    /**
     * Checks that the total portfolio value of {@code optimisedQty} equals
     * the original portfolio value.  Logs an error if the relative deviation
     * exceeds 0.1%.
     */
    private void verifyValueConservation(String label, RealVector optimisedQty) {
        int n = securities.size();
        double origValue = 0, optValue = 0;
        for (int i = 0; i < n; i++) {
            origValue += initialQuantityVector.getEntry(i) * latestPrices[i];
            optValue  += optimisedQty.getEntry(i)          * latestPrices[i];
        }
        if (origValue <= 0) return;
        double relError = Math.abs(optValue - origValue) / origValue;
        if (relError > 0.001) {
            Log.e(TAG, String.format(
                    "%s value NOT conserved! original=%.2f optimised=%.2f deviation=%.4f%%",
                    label, origValue, optValue, relError * 100.0));
        }
    }

    // ── Public entry ────────────────────────────────────────────────────────

    /**
     * Runs all three strategies.  Call when the visible window changes (zoom).
     * Slider movement only needs {@link #getBlendedQuantities}.
     */
    public void calculateOptimizations(int visibleWindow) {
        resultAvailable = false;
        if (securities == null || securities.isEmpty()) return;

        long t0 = System.currentTimeMillis();

        RealMatrix[] wrap = new RealMatrix[1];
        int[] mapping = securitiesToMatrix(wrap, visibleWindow);
        if (mapping == null) return;                       // too little usable data
        if (mapping.length == 0) {                         // everything fixed: valid result
            resultAvailable = true;
            return;
        }
        RealMatrix vm = wrap[0];
        long tMatrix = System.currentTimeMillis() - t0;

        // Pre-compute returns + covariance (shared by GMV and MaxSharpe)
        int nAssets = vm.getRowDimension();
        int nPts    = vm.getColumnDimension();
        double[][] returns = computeReturns(vm, nAssets, nPts);

        RealMatrix cov = null;
        try {
            cov = new Covariance(returns).getCovarianceMatrix();
            shrinkTowardsAverageVariance(cov, nAssets);
        } catch (Exception e) {
            Log.w(TAG, "Covariance computation failed", e);
        }

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
        resultAvailable = true;

        Log.d(TAG, String.format("Opt: Total=%dms Matrix=%dms GMV=%dms Sharpe=%dms MinDD=%dms",
                System.currentTimeMillis() - t0, tMatrix, tGmv, tSharpe, tDd));
    }

    /**
     * Shrinks the covariance matrix towards a scaled identity: {@code (1-l)*S + l*avgVar*I}.
     *
     * <p>The previous fixed ridge of {@code 1e-4} was scale dependent. A real daily
     * variance is around {@code 1e-4} for a share and {@code 3e-6} for a series
     * interpolated from monthly data, so the constant - not the data - decided the
     * weights, and the result shifted with the zoom level because the covariance
     * scales with the sampling frequency while a constant does not. Blending with
     * the average variance keeps the matrix invertible without changing its scale.</p>
     */
    private static void shrinkTowardsAverageVariance(RealMatrix cov, int n) {
        double avgVar = 0;
        for (int i = 0; i < n; i++) avgVar += cov.getEntry(i, i);
        avgVar /= n;
        if (!(avgVar > 0)) avgVar = 1e-12;   // degenerate data: keep it invertible

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = cov.getEntry(i, j) * (1.0 - SHRINKAGE);
                if (i == j) v += SHRINKAGE * avgVar;
                cov.setEntry(i, j, v);
            }
        }
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
     * Long-only maximum Sharpe ratio with rf = 0.
     *
     * <p>Clamping the negative components of the unconstrained tangency solution
     * {@code Σ⁻¹μ} and re-normalising does <b>not</b> solve the long-only problem:
     * it can put the entire portfolio into the worst-performing security while a
     * profitable one is available. So the clamped solution is only one candidate;
     * every single-security portfolio and equal weights are evaluated alongside it
     * and the one with the highest actual Sharpe ratio wins. With at most 24
     * securities that costs nothing measurable.</p>
     */
    private double[] calculateMaxSharpeWeights(int n, double[][] returns, RealMatrix cov) {
        if (cov == null || returns.length == 0) return equalWeights(n);

        // mean return per asset
        double[] mu = new double[n];
        int T = returns.length;
        for (int t = 0; t < T; t++) {
            for (int i = 0; i < n; i++) mu[i] += returns[t][i];
        }
        for (int i = 0; i < n; i++) mu[i] /= T;

        List<double[]> candidates = new ArrayList<>();

        try {
            DecompositionSolver solver = new LUDecomposition(cov).getSolver();
            RealVector sInvMu = solver.solve(new ArrayRealVector(mu));

            double[] w = new double[n];
            double posSum = 0;
            for (int i = 0; i < n; i++) {
                w[i] = Math.max(0, sInvMu.getEntry(i));
                posSum += w[i];
            }
            if (posSum > 0) {
                for (int i = 0; i < n; i++) w[i] /= posSum;
                candidates.add(w);
            }
        } catch (Exception e) {
            Log.w(TAG, "MaxSharpe tangency solve failed", e);
        }

        for (int i = 0; i < n; i++) {
            double[] w = new double[n];
            w[i] = 1.0;
            candidates.add(w);
        }
        candidates.add(equalWeights(n));

        double[] best = null;
        double bestSharpe = Double.NEGATIVE_INFINITY;
        for (double[] w : candidates) {
            double s = sharpeRatio(w, mu, cov);
            if (!Double.isNaN(s) && s > bestSharpe) {
                bestSharpe = s;
                best = w;
            }
        }
        return (best != null) ? best : equalWeights(n);
    }

    /** Sharpe ratio of a weight vector with rf = 0; NaN when the risk is degenerate. */
    private static double sharpeRatio(double[] w, double[] mu, RealMatrix cov) {
        int n = w.length;
        double ret = 0;
        for (int i = 0; i < n; i++) ret += w[i] * mu[i];

        double var = 0;
        for (int i = 0; i < n; i++) {
            if (w[i] == 0) continue;
            for (int j = 0; j < n; j++) var += w[i] * cov.getEntry(i, j) * w[j];
        }
        if (!(var > 0)) return Double.NaN;
        return ret / Math.sqrt(var);
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
        } catch (Exception e) {
            Log.w(TAG, "GMV optimisation failed", e);
        }

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
            // The weights live in a [0,1] box, so the default initial trust-region
            // radius of 10 spans the whole domain many times over: BOBYQA cannot
            // build a usable model, stops after its first step and returns a point
            // that does not depend on the price data at all.
            BOBYQAOptimizer opt = new BOBYQAOptimizer(2 * n + 1, 0.1, 1e-8);
            double[] sp = new double[n];
            double[] ub = new double[n];
            for (int i = 0; i < n; i++) { sp[i] = 1.0 / n; ub[i] = 1.0; }

            // 2n+1 interpolation points alone eat 49 evaluations at n = 24; a flat
            // budget of 500 would silently degenerate into equal weights there.
            PointValuePair res = opt.optimize(
                    new MaxEval(Math.max(500, 100 * n)),
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
        } catch (Exception e) {
            Log.w(TAG, "MinDD optimisation failed", e);
        }

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

        // ── Sanity check: blended portfolio value == original ───────────
        double[] result = blended.toArray();
        double origValue = 0, blendedValue = 0;
        for (int i = 0; i < result.length; i++) {
            origValue    += initialQuantityVector.getEntry(i) * latestPrices[i];
            blendedValue += result[i]                         * latestPrices[i];
        }
        if (origValue > 0) {
            double relError = Math.abs(blendedValue - origValue) / origValue;
            if (relError > 0.001) {
                Log.e(TAG, String.format(
                        "Blended value NOT conserved! original=%.2f blended=%.2f deviation=%.4f%% (var=%.0f%% sharpe=%.0f%% mdd=%.0f%%)",
                        origValue, blendedValue, relError * 100.0,
                        varF * 100, sharpeF * 100, mddF * 100));
            }
        }
        return result;
    }

    /**
     * Whether the last run produced usable optimisation targets. False when the
     * visible window holds too few samples or no security carries usable history.
     */
    public boolean hasResult() {
        return resultAvailable;
    }

    /** Latest known price per security (float – matches Security data). */
    public float[] getLatestPrices() {
        return latestPrices.clone();
    }
}
