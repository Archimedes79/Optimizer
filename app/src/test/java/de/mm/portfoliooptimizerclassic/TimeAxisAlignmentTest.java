package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

/**
 * The optimiser only compares like with like if every security is resampled onto
 * the same calendar days. These tests pin that down from the outside: two series
 * that describe the same line but carry their support points at different
 * densities have to come out identical, and the interpolated values have to sit
 * on the line rather than near it.
 */
public class TimeAxisAlignmentTest {

    private static final int DAY0 = 20000;

    /** price(day) = base + slope * (day - DAY0), sampled every {@code every} days. */
    private static Security onALine(String symbol, double base, double slope, int every, int lastDay) {
        int n = (lastDay - DAY0) / every + 1;
        float[] p = new float[n];
        int[] d = new int[n];
        for (int i = 0; i < n; i++) {
            d[i] = DAY0 + i * every;
            p[i] = (float) (base + slope * (d[i] - DAY0));
        }
        Security s = new Security(symbol, symbol, 1.0);
        s.setHistory(p, d);
        return s;
    }

    @Test
    public void differentSupportDensitiesResampleToTheSameSeries() {
        // A multiple of 30 and 7, so both series really end on the same day and the
        // comparison is about alignment rather than about the trailing clamp.
        int lastDay = DAY0 + 1890;
        Security monthly = onALine("MONTHLY", 100.0, 0.05, 30, lastDay);
        Security weekly = onALine("WEEKLY", 100.0, 0.05, 7, lastDay);

        float[] a = monthly.getValueVector(DAY0, lastDay, 256);
        float[] b = weekly.getValueVector(DAY0, lastDay, 256);

        assertEquals(256, a.length);
        assertEquals(256, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals("sample " + i + " must not depend on the support density",
                    a[i], b[i], 0.01);
        }
    }

    @Test
    public void interpolatedValuesSitOnTheLine() {
        int lastDay = DAY0 + 1800;
        Security monthly = onALine("MONTHLY", 100.0, 0.05, 30, lastDay);

        int points = 256;
        float[] v = monthly.getValueVector(DAY0, lastDay, points);
        float step = (float) (lastDay - DAY0) / (points - 1);
        for (int i = 0; i < points; i++) {
            int day = DAY0 + Math.round(i * step);
            double expected = 100.0 + 0.05 * (day - DAY0);
            assertEquals(String.format(Locale.US, "sample %d (day %d)", i, day),
                    expected, v[i], 0.02);
        }
    }

    @Test
    public void valuesBeforeAndAfterTheHistoryAreClampedNotExtrapolated() {
        Security s = onALine("SHORT", 100.0, 0.05, 30, DAY0 + 300);

        // Ask for a window that starts well before and ends well after the history.
        float[] v = s.getValueVector(DAY0 - 200, DAY0 + 600, 64);

        assertEquals("leading value is clamped to the first price", 100.0, v[0], 0.01);
        double lastKnown = 100.0 + 0.05 * 300;
        assertEquals("trailing value is clamped to the last price",
                lastKnown, v[v.length - 1], 0.6);
        for (float x : v) {
            assertTrue("no value may exceed the clamped range", x >= 99.9 && x <= lastKnown + 0.6);
        }
    }

    @Test
    public void theCommonWindowIsTheOverlapOfAllHistories() {
        Security longOne = onALine("LONG", 100.0, 0.05, 30, DAY0 + 1800);
        Security shortOne = new Security("SHORT", "SHORT", 1.0);
        int n = 40;
        float[] p = new float[n];
        int[] d = new int[n];
        for (int i = 0; i < n; i++) {
            d[i] = DAY0 + 600 + i * 30;               // starts 600 days later
            p[i] = (float) (50.0 + 0.02 * (d[i] - DAY0));
        }
        shortOne.setHistory(p, d);

        assertEquals(DAY0, longOne.getStartDay());
        assertEquals(DAY0 + 600, shortOne.getStartDay());

        int commonStart = Math.max(longOne.getStartDay(), shortOne.getStartDay());
        int commonEnd = Math.min(longOne.getEndDay(), shortOne.getEndDay());
        assertTrue("the overlap must start at the later of the two starts",
                commonStart == DAY0 + 600);
        assertTrue("the overlap must be non-empty", commonEnd > commonStart);
    }
}
