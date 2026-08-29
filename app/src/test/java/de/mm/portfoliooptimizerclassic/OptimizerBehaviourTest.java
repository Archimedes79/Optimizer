package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Behaviour, not just invariants: with three series whose ranking is obvious by
 * construction, each strategy has to pick the security it is named after.
 *
 * <p>RISER climbs steadily and never gives anything back. WILD climbs a little
 * faster on average but swings hard and drops deep. FLAT barely moves.</p>
 */
public class OptimizerBehaviourTest {

    private static final int DAYS = 250;

    private static Security riser(double quantity) {
        Security s = new Security("RISER", "RISER", quantity);
        float[] p = new float[DAYS];
        int[] d = new int[DAYS];
        for (int i = 0; i < DAYS; i++) {
            p[i] = (float) (100.0 * Math.pow(1.0010, i));   // +0.10 % a day, monotone
            d[i] = 20000 + i;
        }
        s.setHistory(p, d);
        return s;
    }

    private static Security wild(double quantity) {
        Security s = new Security("WILD", "WILD", quantity);
        float[] p = new float[DAYS];
        int[] d = new int[DAYS];
        for (int i = 0; i < DAYS; i++) {
            p[i] = (float) (100.0 * Math.pow(1.0012, i) * (1.0 + 0.30 * Math.sin(i / 4.0)));
            d[i] = 20000 + i;
        }
        s.setHistory(p, d);
        return s;
    }

    private static Security flat(double quantity) {
        Security s = new Security("FLAT", "FLAT", quantity);
        float[] p = new float[DAYS];
        int[] d = new int[DAYS];
        for (int i = 0; i < DAYS; i++) {
            p[i] = (float) (100.0 + 0.5 * Math.sin(i / 9.0));   // sideways, tiny wobble
            d[i] = 20000 + i;
        }
        s.setHistory(p, d);
        return s;
    }

    /** Share of the portfolio value each position holds under the given mix. */
    private static double[] weights(PortfolioOptimizer opt, double varF, double sharpeF, double mddF) {
        double[] q = opt.getBlendedQuantities(varF, sharpeF, mddF);
        float[] prices = opt.getLatestPrices();
        double total = 0;
        for (int i = 0; i < q.length; i++) total += q[i] * prices[i];
        double[] w = new double[q.length];
        for (int i = 0; i < q.length; i++) w[i] = q[i] * prices[i] / total;
        return w;
    }

    private static String describe(String label, double[] w) {
        return String.format(Locale.US, "%s -> RISER %.1f%%, WILD %.1f%%, FLAT %.1f%%",
                label, w[0] * 100, w[1] * 100, w[2] * 100);
    }

    private static PortfolioOptimizer optimizerOverAllThree() {
        List<Security> securities = new ArrayList<>();
        securities.add(riser(10.0));
        securities.add(wild(10.0));
        securities.add(flat(10.0));

        PortfolioOptimizer opt = new PortfolioOptimizer(securities);
        opt.calculateOptimizations(DAYS);
        assertTrue("optimisation should have produced a result", opt.hasResult());
        return opt;
    }

    @Test
    public void maxSharpeGoesToTheSteadyRiser() {
        double[] w = weights(optimizerOverAllThree(), 0, 1, 0);
        assertTrue(describe("max sharpe", w), w[0] > 0.5);
    }

    @Test
    public void minDrawdownGoesToTheSeriesThatNeverDrops() {
        double[] w = weights(optimizerOverAllThree(), 0, 0, 1);
        assertTrue(describe("min drawdown", w), w[0] + w[2] > 0.8 && w[1] < 0.2);
    }

    @Test
    public void minVarianceAvoidsTheWildOne() {
        double[] w = weights(optimizerOverAllThree(), 1, 0, 0);
        assertTrue(describe("min variance", w), w[1] < 0.2);
    }
}
