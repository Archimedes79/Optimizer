package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks the Min Drawdown result against drawdowns computed independently:
 * a blend is only allowed to win if it really has the smaller drawdown.
 */
public class DrawdownDiagnosticTest {

    private static final int MONTHS = 60;
    private static final int DAYS_PER_MONTH = 21;
    private static final int DAYS = MONTHS * DAYS_PER_MONTH;
    private static final int WINDOW = 24 * DAYS_PER_MONTH;
    private static final int POINTS = 256;

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

    private static List<Security> portfolio() {
        List<Security> securities = new ArrayList<>();
        securities.add(monthlyInterpolated("STRONG", 10.0, 0.015, 0.04, 0.0));
        securities.add(monthlyInterpolated("MIDDLE", 10.0, 0.006, 0.10, 1.3));
        securities.add(monthlyInterpolated("WEAK", 10.0, 0.001, 0.16, 2.6));
        return securities;
    }

    /** Worst peak-to-trough drop of the blended, rebased series - the optimiser's objective. */
    private static double maxDrawdown(float[][] series, double[] w) {
        double maxDD = 0, peak = 0;
        for (int t = 0; t < POINTS; t++) {
            double pv = 0;
            for (int i = 0; i < w.length; i++) {
                double base = series[i][0];
                if (base != 0) pv += w[i] * (series[i][t] / base);
            }
            if (pv > peak) peak = pv;
            if (peak > 0) maxDD = Math.max(maxDD, (peak - pv) / peak);
        }
        return maxDD;
    }

    @Test
    public void theChosenBlendReallyHasTheSmallestDrawdown() {
        List<Security> securities = portfolio();
        PortfolioOptimizer opt = new PortfolioOptimizer(securities);
        opt.calculateOptimizations(WINDOW);
        assertTrue(opt.hasResult());

        // Same window and sampling the optimiser uses internally.
        int endDay = securities.get(0).getEndDay();
        int startDay = endDay - (WINDOW - 1);
        float[][] series = new float[3][];
        for (int i = 0; i < 3; i++) {
            series[i] = securities.get(i).getValueVector(startDay, endDay, POINTS);
        }

        double[] chosen = new double[3];
        double[] q = opt.getBlendedQuantities(0, 0, 1);
        float[] prices = opt.getLatestPrices();
        double total = 0;
        for (int i = 0; i < 3; i++) total += q[i] * prices[i];
        for (int i = 0; i < 3; i++) chosen[i] = q[i] * prices[i] / total;

        double ddChosen = maxDrawdown(series, chosen);
        double ddStrong = maxDrawdown(series, new double[]{1, 0, 0});
        double ddMiddle = maxDrawdown(series, new double[]{0, 1, 0});
        double ddWeak = maxDrawdown(series, new double[]{0, 0, 1});
        double ddEqual = maxDrawdown(series, new double[]{1 / 3.0, 1 / 3.0, 1 / 3.0});
        double bestSingle = Math.min(ddStrong, Math.min(ddMiddle, ddWeak));

        String report = String.format(Locale.US,
                "chosen %.1f/%.1f/%.1f dd=%.3f%%  |  STRONG %.3f%%  MIDDLE %.3f%%  WEAK %.3f%%  EQUAL %.3f%%",
                chosen[0] * 100, chosen[1] * 100, chosen[2] * 100,
                ddChosen * 100, ddStrong * 100, ddMiddle * 100, ddWeak * 100, ddEqual * 100);

        assertTrue("blend must not be worse than the best single position: " + report,
                ddChosen <= bestSingle + 1e-6);
    }
}
