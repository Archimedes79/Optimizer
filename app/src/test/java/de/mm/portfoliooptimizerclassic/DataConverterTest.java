package de.mm.portfoliooptimizerclassic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

/**
 * Covers the date and interpolation maths, which every chart and every
 * optimisation run depends on.
 */
public class DataConverterTest {

    private static final float EPS = 1e-4f;

    @Test
    public void dateToDay_isIndependentOfTheDeviceTimezone() {
        assertEquals(0, DataConverter.dateToDay("1970-01-01"));
        assertEquals(1, DataConverter.dateToDay("1970-01-02"));
        assertEquals(19723, DataConverter.dateToDay("2024-01-01"));
    }

    @Test
    public void dateToDay_returnsMinusOneForUnparsableInput() {
        assertEquals(-1, DataConverter.dateToDay("not-a-date"));
    }

    @Test
    public void convertDatesToInt_mapsEveryEntry() {
        assertArrayEquals(new int[]{0, 10},
                DataConverter.convertDatesToInt(Arrays.asList("1970-01-01", "1970-01-11")));
    }

    @Test
    public void interpolate_isLinearBetweenSamples() {
        float[] out = DataConverter.interpolate(
                new int[]{0, 10}, new float[]{100f, 200f}, new int[]{0, 5, 10});
        assertArrayEquals(new float[]{100f, 150f, 200f}, out, EPS);
    }

    @Test
    public void interpolate_clampsOutsideTheSourceRange() {
        float[] out = DataConverter.interpolate(
                new int[]{10, 20}, new float[]{50f, 60f}, new int[]{0, 5, 25});
        assertArrayEquals(new float[]{50f, 50f, 60f}, out, EPS);
    }

    @Test
    public void interpolate_yieldsZeroesWhenTheSourceIsUnusable() {
        assertArrayEquals(new float[]{0f, 0f},
                DataConverter.interpolate(new int[0], new float[0], new int[]{1, 2}), EPS);
        assertArrayEquals(new float[]{0f},
                DataConverter.interpolate(new int[]{1, 2}, new float[]{1f}, new int[]{1}), EPS);
    }

    @Test
    public void interpolate_handlesAnEmptyTargetGrid() {
        assertEquals(0,
                DataConverter.interpolate(new int[]{1}, new float[]{1f}, new int[0]).length);
    }
}
