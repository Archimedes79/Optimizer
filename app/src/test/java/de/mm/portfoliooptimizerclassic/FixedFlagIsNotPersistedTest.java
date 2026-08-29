package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

/**
 * The exclusion switch belongs to the current session, not to the position, so
 * it must not survive a save/load round trip - while everything else does.
 */
public class FixedFlagIsNotPersistedTest {

    private static Security sample() {
        Security s = new Security("Gold", "PPFB.DE", 3.0);
        float[] prices = {70f, 72f, 71f, 76f};
        int[] days = {20000, 20001, 20002, 20003};
        s.setHistory(prices, days);
        s.setAlias("Gold ETC");
        s.setColor(0x123456);
        return s;
    }

    @Test
    public void theExclusionSwitchDoesNotSurviveALoad() {
        Gson gson = new Gson();
        Security s = sample();
        s.setFixed(true);
        assertTrue(s.isFixed());

        Security restored = gson.fromJson(gson.toJson(s), Security.class);

        assertFalse("the switch must start cleared after a load", restored.isFixed());
    }

    @Test
    public void everythingElseStillSurvivesALoad() {
        Gson gson = new Gson();
        Security s = sample();
        s.setFixed(true);

        Security restored = gson.fromJson(gson.toJson(s), Security.class);

        assertEquals("PPFB.DE", restored.getSymbol());
        assertEquals("Gold ETC", restored.getAlias());
        assertEquals(3.0, restored.getQuantity(), 1e-9);
        assertEquals(0x123456, restored.getColor());
        assertEquals(4, restored.getNumberOfEntries());
        assertEquals(76f, restored.getValuesOverTime()[3], 1e-6f);
        assertEquals(20003, restored.getEpochDays()[3]);
    }
}
