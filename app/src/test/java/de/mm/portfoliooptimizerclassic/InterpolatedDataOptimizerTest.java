package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The same ranking question as {@link OptimizerBehaviourTest}, but on series
 * shaped the way the app really holds them: Yahoo delivers monthly closes and
 * DataConverter interpolates them linearly onto days. Between two month ends
 * every daily return is therefore identical, which makes the sampled return
 * series nearly collinear and the covariance matrix nearly singular.
 */
public class InterpolatedDataOptimizerTest {

    private static final int MONTHS = 60;
    private static final int DAYS_PER_MONTH = 21;
    private static final int DAYS = MONTHS * DAYS_PER_MONTH;

    /** Monthly closes interpolated linearly onto daily points, as the app does. */
    private static Security monthlyInterpolated(String symbol, double quantity,
                                                double monthlyDrift, double swing, double phase) {
        double[] monthly = new double[MONTHS + 1];
        for (int m = 0; m <= MONTHS; m++) {
            monthly[m] = 100.0 * Math.pow(1.0 + monthlyDrift, m) * (1.0 + swing * Math.sin(m / 5.0 + phase));
        }

        float[] p = new float[DAYS];
        int[] d = new int[DAYS];
        for (int i = 0; i < DAYS; i++) {
            int m = i / DAYS_PER_MONTH;
            double t = (i % DAYS_PER_MONTH) / (double) DAYS_PER_MONTH;
            p[i] = (float) (monthly[m] + t * (monthly[m + 1] - monthly[m]));
            d[i] = 20000 + i;
        }

        Security s = new Security(symbol, symbol, quantity);
        s.setHistory(p, d);
        return s;
    }

    private static double[] weights(PortfolioOptimizer opt, double varF, double sharpeF, double mddF) {
        double[] q = opt.getBlendedQuantities(varF, sharpeF, mddF);
        float[] prices = opt.getLatestPrices();
        double total = 0;
        for (int i = 0; i < q.length; i++) total += q[i] * prices[i];
        double[] w = new double[q.length];
        for (int i = 0; i < q.length; i++) w[i] = q[i] * prices[i] / total;
        return w;
    }

    private static PortfolioOptimizer optimizer(int window) {
        List<Security> securities = new ArrayList<>();
        // STRONG clearly outgrows the other two and swings the least.
        securities.add(monthlyInterpolated("STRONG", 10.0, 0.015, 0.04, 0.0));
        securities.add(monthlyInterpolated("MIDDLE", 10.0, 0.006, 0.10, 1.3));
        securities.add(monthlyInterpolated("WEAK", 10.0, 0.001, 0.16, 2.6));

        PortfolioOptimizer opt = new PortfolioOptimizer(securities);
        opt.calculateOptimizations(window);
        assertTrue("optimisation should have produced a result", opt.hasResult());
        return opt;
    }

    private static String describe(String label, int window, double[] w) {
        return String.format(Locale.US,
                "%s over a %d-day window -> STRONG %.1f%%, MIDDLE %.1f%%, WEAK %.1f%%",
                label, window, w[0] * 100, w[1] * 100, w[2] * 100);
    }

    @Test
    public void maxSharpePrefersTheStrongestOverTheFullHistory() {
        double[] w = weights(optimizer(DAYS), 0, 1, 0);
        assertTrue(describe("max sharpe", DAYS, w), w[0] > 0.5);
    }

    @Test
    public void maxSharpePrefersTheStrongestOverATwoYearWindow() {
        int window = 24 * DAYS_PER_MONTH;
        double[] w = weights(optimizer(window), 0, 1, 0);
        assertTrue(describe("max sharpe", window, w), w[0] > 0.5);
    }

    @Test
    public void maxSharpePrefersTheStrongestOverAOneYearWindow() {
        int window = 12 * DAYS_PER_MONTH;
        double[] w = weights(optimizer(window), 0, 1, 0);
        assertTrue(describe("max sharpe", window, w), w[0] > 0.5);
    }

    @Test
    public void minDrawdownPrefersTheStrongestOverATwoYearWindow() {
        int window = 24 * DAYS_PER_MONTH;
        double[] w = weights(optimizer(window), 0, 0, 1);
        assertTrue(describe("min drawdown", window, w), w[0] > 0.9);
    }
}
