package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Guards the invariants the optimiser must never break: the portfolio keeps its
 * value, fixed positions stay untouched, and positions without a usable price are
 * left alone instead of being handed a weight whose value would vanish.
 */
public class PortfolioOptimizerTest {

    private static final int DAYS = 60;

    /** A security with a smooth, non-constant daily price series. */
    private static Security withPrices(String symbol, double quantity,
                                       double base, double amplitude, double period) {
        Security s = new Security(symbol, symbol, quantity);
        float[] prices = new float[DAYS];
        int[] days = new int[DAYS];
        for (int i = 0; i < DAYS; i++) {
            prices[i] = (float) (base + amplitude * Math.sin(i / period));
            days[i] = 20000 + i;
        }
        s.setHistory(prices, days);
        return s;
    }

    private static Security withoutHistory(String symbol, double quantity) {
        return new Security(symbol, symbol, quantity);
    }

    private static double totalValue(List<Security> securities, double[] quantities,
                                     float[] prices) {
        double total = 0;
        for (int i = 0; i < securities.size(); i++) total += quantities[i] * prices[i];
        return total;
    }

    private static double[] originalQuantities(List<Security> securities) {
        double[] q = new double[securities.size()];
        for (int i = 0; i < securities.size(); i++) q[i] = securities.get(i).getQuantity();
        return q;
    }

    @Test
    public void everyStrategyConservesThePortfolioValue() {
        List<Security> securities = new ArrayList<>();
        securities.add(withPrices("AAA", 10.0, 100.0, 10.0, 3.0));
        securities.add(withPrices("BBB", 5.0, 50.0, 4.0, 7.0));

        PortfolioOptimizer optimizer = new PortfolioOptimizer(securities);
        optimizer.calculateOptimizations(DAYS);
        assertTrue("optimisation should have produced a result", optimizer.hasResult());

        float[] prices = optimizer.getLatestPrices();
        double expected = totalValue(securities, originalQuantities(securities), prices);

        double[][] mixes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0.3, 0.3, 0.3}};
        for (double[] mix : mixes) {
            double[] blended = optimizer.getBlendedQuantities(mix[0], mix[1], mix[2]);
            assertEquals("value must be conserved for mix " + mix[0] + "/" + mix[1] + "/" + mix[2],
                    expected, totalValue(securities, blended, prices), expected * 0.001);
        }
    }

    @Test
    public void fixedPositionsAreLeftUntouched() {
        List<Security> securities = new ArrayList<>();
        securities.add(withPrices("AAA", 10.0, 100.0, 10.0, 3.0));
        Security fixed = withPrices("BBB", 5.0, 50.0, 4.0, 7.0);
        fixed.setFixed(true);
        securities.add(fixed);

        PortfolioOptimizer optimizer = new PortfolioOptimizer(securities);
        optimizer.calculateOptimizations(DAYS);

        double[] blended = optimizer.getBlendedQuantities(1.0, 0, 0);
        assertEquals(5.0, blended[1], 1e-9);
    }

    @Test
    public void positionsWithoutAPriceKeepTheirQuantityAndDoNotEatTheBudget() {
        List<Security> securities = new ArrayList<>();
        securities.add(withPrices("AAA", 10.0, 100.0, 10.0, 3.0));
        securities.add(withPrices("BBB", 5.0, 50.0, 4.0, 7.0));
        securities.add(withoutHistory("CCC", 3.0));   // never fetched, no price

        PortfolioOptimizer optimizer = new PortfolioOptimizer(securities);
        optimizer.calculateOptimizations(DAYS);
        assertTrue(optimizer.hasResult());

        float[] prices = optimizer.getLatestPrices();
        assertEquals("a security without history has no price", 0f, prices[2], 0f);

        double expected = totalValue(securities, originalQuantities(securities), prices);
        double[] blended = optimizer.getBlendedQuantities(1.0, 0, 0);

        assertEquals("its quantity must survive untouched", 3.0, blended[2], 1e-9);
        assertEquals("the tradable budget must stay in the tradable positions",
                expected, totalValue(securities, blended, prices), expected * 0.001);
    }

    @Test
    public void aWindowWithTooFewSamplesProducesNoResult() {
        List<Security> securities = new ArrayList<>();
        securities.add(withPrices("AAA", 10.0, 100.0, 10.0, 3.0));
        securities.add(withPrices("BBB", 5.0, 50.0, 4.0, 7.0));

        PortfolioOptimizer optimizer = new PortfolioOptimizer(securities);
        optimizer.calculateOptimizations(5);

        assertFalse("five samples are not enough to estimate a covariance",
                optimizer.hasResult());
        double[] blended = optimizer.getBlendedQuantities(1.0, 0, 0);
        assertEquals(10.0, blended[0], 1e-9);
        assertEquals(5.0, blended[1], 1e-9);
    }

    @Test
    public void anEmptyPortfolioIsHandled() {
        PortfolioOptimizer optimizer = new PortfolioOptimizer(new ArrayList<>());
        optimizer.calculateOptimizations(DAYS);
        assertFalse(optimizer.hasResult());
        assertEquals(0, optimizer.getBlendedQuantities(1.0, 0, 0).length);
    }
}
