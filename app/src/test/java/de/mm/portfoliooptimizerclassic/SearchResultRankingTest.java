package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Typing a ticker must not quietly produce a different instrument. Yahoo answers
 * "AAPL" with Apple plus a row of leveraged products built on it, so the exact
 * hit has to come first and a longer history has to beat a shorter one.
 */
public class SearchResultRankingTest {

    private static Security hit(String symbol, int entries) {
        Security s = new Security(symbol, symbol, 1.0);
        float[] p = new float[entries];
        int[] d = new int[entries];
        for (int i = 0; i < entries; i++) {
            p[i] = 100f + i;
            d[i] = 20000 + i;
        }
        s.setHistory(p, d);
        return s;
    }

    private static List<String> symbols(List<Security> list) {
        List<String> out = new ArrayList<>();
        for (Security s : list) out.add(s.getSymbol());
        return out;
    }

    @Test
    public void theExactTickerWinsEvenWithAShorterHistory() {
        List<Security> results = new ArrayList<>(Arrays.asList(
                hit("AAPU", 900), hit("AAPW", 400), hit("AAPL", 300)));

        YahooFinanceService.rankResults(results, "AAPL");

        assertEquals("AAPL", results.get(0).getSymbol());
    }

    @Test
    public void withoutAnExactHitTheLongerHistoryComesFirst() {
        List<Security> results = new ArrayList<>(Arrays.asList(
                hit("SHORT", 120), hit("LONG", 4000), hit("MIDDLE", 900)));

        YahooFinanceService.rankResults(results, "SOMETHING");

        assertEquals(Arrays.asList("LONG", "MIDDLE", "SHORT"), symbols(results));
    }

    @Test
    public void rankingCopesWithCaseAndSurroundingSpace() {
        List<Security> results = new ArrayList<>(Arrays.asList(hit("AAPU", 900), hit("AAPL", 300)));

        YahooFinanceService.rankResults(results, "  aapl ");

        assertEquals("AAPL", results.get(0).getSymbol());
    }

    @Test
    public void anExactMatchIsRecognisedAndAnythingElseIsNot() {
        assertTrue(YahooFinanceService.isExactMatch(hit("AAPL", 10), "AAPL"));
        assertTrue(YahooFinanceService.isExactMatch(hit("AAPL", 10), " aapl "));
        assertFalse("a leveraged product on the same underlying is not the same paper",
                YahooFinanceService.isExactMatch(hit("AAPU", 10), "AAPL"));
        assertFalse(YahooFinanceService.isExactMatch(null, "AAPL"));
        assertFalse(YahooFinanceService.isExactMatch(hit("AAPL", 10), null));
    }

    @Test
    public void anEmptyResultListIsHandled() {
        List<Security> results = new ArrayList<>();
        YahooFinanceService.rankResults(results, "AAPL");
        assertTrue(results.isEmpty());
    }
}
