package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Covers the common-range binary search and chart normalisation - the two
 * places where an off-by-one silently corrupts every allocation figure.
 */
public class SecurityTest {

    private static final float EPS = 1e-3f;

    private static Security withFiveDays() {
        Security s = new Security("Test Corp", "TST", 2.0);
        s.setHistory(new float[]{10f, 20f, 30f, 40f, 50f}, new int[]{100, 101, 102, 103, 104});
        return s;
    }

    @Test
    public void displayName_prefersTheAlias() {
        Security s = withFiveDays();
        assertEquals("Test Corp", s.getDisplayName());
        s.setAlias("My ETF");
        assertEquals("My ETF", s.getDisplayName());
    }

    @Test
    public void setHistory_spansTheWholeSeriesByDefault() {
        Security s = withFiveDays();
        assertEquals(0, s.getStartIndex());
        assertEquals(4, s.getEndIndex());
        assertEquals(5, s.getCommonRangeLength());
    }

    @Test
    public void indexSearch_snapsToTheCommonRange() {
        Security s = withFiveDays();
        s.setStartIndex(101);
        s.setEndIndex(103);
        assertEquals(1, s.getStartIndex());
        assertEquals(3, s.getEndIndex());
        assertEquals(3, s.getCommonRangeLength());
        assertEquals(101, s.getStartDay());
        assertEquals(103, s.getEndDay());
    }

    @Test
    public void indexSearch_clampsDaysOutsideTheSeries() {
        Security s = withFiveDays();
        s.setStartIndex(1);      // before the first day
        s.setEndIndex(9_999);    // after the last day
        assertEquals(0, s.getStartIndex());
        assertEquals(4, s.getEndIndex());
    }

    @Test
    public void normalizedValues_endAtOneHundred() {
        Security s = withFiveDays();
        float[] n = s.getNormalizedValues();
        assertEquals(5, n.length);
        assertEquals(100f, n[4], EPS);
        assertEquals(20f, n[0], EPS);
    }

    @Test
    public void emptyHistory_neverThrows() {
        Security s = new Security("Empty", "EMP", 0);
        s.setStartIndex(5);
        s.setEndIndex(9);
        assertEquals(0, s.getCommonRangeLength());
        assertEquals(0, s.getNormalizedValues().length);
        assertEquals(0, s.getStartDay());
        assertEquals(0, s.getEndDay());
    }
}
