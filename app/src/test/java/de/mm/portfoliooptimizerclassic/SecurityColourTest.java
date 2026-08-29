package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Every position must arrive with a colour that is actually visible. The
 * no-argument constructor is the one the search uses, and an unset colour is 0 -
 * fully transparent, which left the row stripe and the chart line blank.
 */
public class SecurityColourTest {

    @Test
    public void aFreshlySearchedSecurityHasAColour() {
        Security s = new Security();
        s.setSymbol("AAPL");
        s.setName("Apple Inc.");

        assertNotEquals("an unset colour is transparent and therefore invisible", 0, s.getColor());
    }

    @Test
    public void aNamedSecurityHasAColour() {
        assertNotEquals(0, new Security("Apple Inc.", "AAPL", 1.0).getColor());
    }

    @Test
    public void derivedColoursAreStableForTheSameSymbol() {
        assertEquals(Security.colourFor("VOO"), Security.colourFor("VOO"));
        assertNotEquals(Security.colourFor("VOO"), Security.colourFor("CSH.PA"));
    }

    @Test
    public void everyChannelStaysInTheVisibleBand() {
        // Color is stubbed to 0 in plain unit tests, so check the generator's own
        // arithmetic instead: 100..250 per channel, for both entry points.
        for (int i = 0; i < 200; i++) {
            assertInBand(channelsOf(Security.randomColour()));
            assertInBand(channelsOf(Security.colourFor("SYM" + i)));
        }
    }

    private static int[] channelsOf(int argb) {
        return new int[]{(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF};
    }

    private static void assertInBand(int[] rgb) {
        for (int c : rgb) {
            assertTrue("channel " + c + " must stay between 100 and 250", c >= 100 && c <= 250);
        }
    }
}
