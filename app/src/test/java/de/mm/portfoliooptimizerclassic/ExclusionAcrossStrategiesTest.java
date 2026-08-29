package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Excluding a position from the optimisation has to hold for <em>every</em>
 * strategy and every slider mix, not just for the one a test happens to pick.
 * A fixed position keeps its quantity, a position without a price keeps its
 * quantity, and the value of the tradable part stays where it was.
 */
public class ExclusionAcrossStrategiesTest {

    private static final int DAYS = 400;

    /** Distinct shapes so the strategies genuinely disagree about the winner. */
    private static Security series(String symbol, double quantity,
                                   double drift, double amplitude, double period) {
        Security s = new Security(symbol, symbol, quantity);
        float[] p = new float[DAYS];
        int[] d = new int[DAYS];
        for (int i = 0; i < DAYS; i++) {
            p[i] = (float) (100.0 * Math.pow(1.0 + drift, i) + amplitude * Math.sin(i / period));
            d[i] = 20000 + i;
        }
        s.setHistory(p, d);
        return s;
    }

    private static Security withoutHistory(String symbol, double quantity) {
        return new Security(symbol, symbol, quantity);
    }

    /** Every corner of the slider space plus a few blends. */
    private static final double[][] MIXES = {
            {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {0.5, 0.5, 0}, {0.5, 0, 0.5}, {0, 0.5, 0.5},
            {1 / 3.0, 1 / 3.0, 1 / 3.0}, {0.2, 0.3, 0.5},
    };

    private static String label(double[] mix) {
        return String.format(Locale.US, "var=%.2f sharpe=%.2f mdd=%.2f", mix[0], mix[1], mix[2]);
    }

    @Test
    public void aFixedPositionSurvivesEveryStrategy() {
        List<Security> securities = new ArrayList<>();
        securities.add(series("RISER", 10.0, 0.0020, 2.0, 11.0));
        securities.add(series("CALM", 7.0, 0.0004, 0.5, 23.0));
        Security fixed = series("FIXED", 4.0, 0.0010, 6.0, 5.0);
        fixed.setFixed(true);
        securities.add(fixed);

        PortfolioOptimizer opt = new PortfolioOptimizer(securities);
        opt.calculateOptimizations(DAYS);
        assertTrue(opt.hasResult());

        for (double[] mix : MIXES) {
            double[] q = opt.getBlendedQuantities(mix[0], mix[1], mix[2]);
            assertEquals("fixed position must not move: " + label(mix), 4.0, q[2], 1e-9);
        }
    }

    @Test
    public void aPositionWithoutAPriceSurvivesEveryStrategy() {
        List<Security> securities = new ArrayList<>();
        securities.add(series("RISER", 10.0, 0.0020, 2.0, 11.0));
        securities.add(series("CALM", 7.0, 0.0004, 0.5, 23.0));
        securities.add(withoutHistory("NODATA", 3.0));

        PortfolioOptimizer opt = new PortfolioOptimizer(securities);
        opt.calculateOptimizations(DAYS);
        assertTrue(opt.hasResult());

        for (double[] mix : MIXES) {
            double[] q = opt.getBlendedQuantities(mix[0], mix[1], mix[2]);
            assertEquals("position without a price must not move: " + label(mix),
                    3.0, q[2], 1e-9);
        }
    }

    @Test
    public void theTradableValueIsConservedForEveryStrategy() {
        List<Security> securities = new ArrayList<>();
        securities.add(series("RISER", 10.0, 0.0020, 2.0, 11.0));
        securities.add(series("CALM", 7.0, 0.0004, 0.5, 23.0));
        Security fixed = series("FIXED", 4.0, 0.0010, 6.0, 5.0);
        fixed.setFixed(true);
        securities.add(fixed);
        securities.add(withoutHistory("NODATA", 3.0));

        PortfolioOptimizer opt = new PortfolioOptimizer(securities);
        opt.calculateOptimizations(DAYS);
        assertTrue(opt.hasResult());

        float[] prices = opt.getLatestPrices();
        double before = 0;
        for (int i = 0; i < securities.size(); i++) {
            before += securities.get(i).getQuantity() * prices[i];
        }

        for (double[] mix : MIXES) {
            double[] q = opt.getBlendedQuantities(mix[0], mix[1], mix[2]);
            double after = 0;
            for (int i = 0; i < q.length; i++) after += q[i] * prices[i];
            assertEquals("total value must be conserved: " + label(mix),
                    before, after, before * 0.001);
        }
    }

    @Test
    public void everythingFixedLeavesThePortfolioAlone() {
        List<Security> securities = new ArrayList<>();
        for (String name : new String[]{"A", "B"}) {
            Security s = series(name, 6.0, 0.0015, 3.0, 9.0);
            s.setFixed(true);
            securities.add(s);
        }

        PortfolioOptimizer opt = new PortfolioOptimizer(securities);
        opt.calculateOptimizations(DAYS);

        for (double[] mix : MIXES) {
            double[] q = opt.getBlendedQuantities(mix[0], mix[1], mix[2]);
            assertEquals("A must not move: " + label(mix), 6.0, q[0], 1e-9);
            assertEquals("B must not move: " + label(mix), 6.0, q[1], 1e-9);
        }
    }
}
