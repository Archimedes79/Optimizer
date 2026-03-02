package com.example.optimizer;

import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Date conversion and linear interpolation for time-series data.
 *
 * <p>Design rules:
 * <ul>
 *   <li>All public APIs use primitive arrays (int[], float[]) – no Lists – for speed.</li>
 *   <li>Source values are {@code float[]} – 7 significant digits is plenty for prices.</li>
 *   <li>Three target-day flavours: int[] (exact days), float[] (sub-day precision for
 *       downsampled matrices), and write-into-caller-buffer variants to avoid GC pressure.</li>
 *   <li>Two-pointer sweep gives O(N + M) for sorted inputs.</li>
 * </ul>
 */
public class DataConverter {
    private static final String TAG = "DataConverter";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // ── Date helpers ────────────────────────────────────────────────────────

    /**
     * Converts "yyyy-MM-dd" to the number of days since 1970-01-01 (UTC).
     *
     * <p>Uses UTC explicitly so that the epoch-day number is independent of the
     * device's local timezone.  Without this, a CET/CEST device would compute
     * midnight-local → 23:00/22:00 UTC → wrong epoch day.</p>
     */
    public static int dateToDay(String dateString) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = sdf.parse(dateString);
            if (date == null) return -1;
            return (int) TimeUnit.DAYS.convert(date.getTime(), TimeUnit.MILLISECONDS);
        } catch (ParseException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /** Batch-converts a List of date strings to int[] epoch days. */
    public static int[] convertDatesToInt(List<String> dates) {
        int[] out = new int[dates.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = dateToDay(dates.get(i));
        }
        return out;
    }

    // ── Core interpolation: int[] targets → float[] ─────────────────────────

    /**
     * Linearly interpolates {@code srcValues} (sampled at {@code srcDays}) onto
     * the sorted {@code targetDays} grid.  Returns a <b>new</b> float[].
     *
     * <p>Both arrays <b>must</b> be sorted ascending.  Values outside the source
     * range are clamped to the first / last source value.</p>
     *
     * <p>Complexity: O(srcDays.length + targetDays.length) – single pass.</p>
     */
    public static float[] interpolate(int[] srcDays, float[] srcValues, int[] targetDays) {
        if (targetDays == null) return new float[0];
        float[] out = new float[targetDays.length];
        interpolateInto(srcDays, srcValues, targetDays, out);
        return out;
    }

    /**
     * Same as {@link #interpolate} but writes into the caller-provided {@code out}
     * buffer instead of allocating.  {@code out.length} must equal {@code targetDays.length}.
     */
    public static void interpolateInto(int[] srcDays, float[] srcValues,
                                        int[] targetDays, float[] out) {
        final int tLen = targetDays.length;
        if (srcDays == null || srcValues == null
                || srcDays.length == 0 || srcDays.length != srcValues.length) {
            for (int i = 0; i < tLen; i++) out[i] = 0f;
            return;
        }

        final int sLen = srcDays.length;
        final int firstDay = srcDays[0];
        final int lastDay  = srcDays[sLen - 1];

        // leading clamp
        int t = 0;
        while (t < tLen && targetDays[t] <= firstDay) {
            out[t++] = srcValues[0];
        }

        // main two-pointer sweep
        int s = 0;
        while (t < tLen && targetDays[t] < lastDay) {
            final int td = targetDays[t];
            while (s + 1 < sLen && srcDays[s + 1] <= td) s++;

            final int   d1 = srcDays[s];
            final int   d2 = srcDays[s + 1];
            final float v1 = srcValues[s];
            final float v2 = srcValues[s + 1];

            out[t++] = (d1 == d2) ? v1
                    : v1 + (v2 - v1) * ((float)(td - d1) / (float)(d2 - d1));
        }

        // trailing clamp
        final float lastVal = srcValues[sLen - 1];
        while (t < tLen) {
            out[t++] = lastVal;
        }
    }

    // ── Interpolation: int[] targets → int[] (for resampling day arrays) ────

    /**
     * Same algorithm as {@link #interpolate} but outputs {@code int[]}
     * (values rounded to nearest integer).
     */
    public static int[] interpolateToInt(int[] srcDays, float[] srcValues, int[] targetDays) {
        if (targetDays == null) return new int[0];
        final int tLen = targetDays.length;
        int[] out = new int[tLen];
        if (srcDays == null || srcValues == null
                || srcDays.length == 0 || srcDays.length != srcValues.length) {
            return out;
        }

        final int sLen = srcDays.length;
        final int firstDay = srcDays[0];
        final int lastDay  = srcDays[sLen - 1];

        int t = 0;
        while (t < tLen && targetDays[t] <= firstDay) {
            out[t++] = Math.round(srcValues[0]);
        }

        int s = 0;
        while (t < tLen && targetDays[t] < lastDay) {
            final int td = targetDays[t];
            while (s + 1 < sLen && srcDays[s + 1] <= td) s++;

            final int   d1 = srcDays[s];
            final int   d2 = srcDays[s + 1];
            final float v1 = srcValues[s];
            final float v2 = srcValues[s + 1];

            out[t++] = (d1 == d2) ? Math.round(v1)
                    : Math.round(v1 + (v2 - v1) * ((float)(td - d1) / (float)(d2 - d1)));
        }

        final float lastVal = srcValues[sLen - 1];
        while (t < tLen) {
            out[t++] = Math.round(lastVal);
        }
        return out;
    }

    // ── Interpolation: float[] targets → float[] (sub-day precision) ────────

    /**
     * Interpolates onto a <b>float[]</b> target grid (fractional days).
     * Useful for building smaller downsampled matrices where target positions
     * don't land on exact integer days.
     *
     * <p>Writes into the caller-provided {@code out} buffer to avoid allocation.</p>
     *
     * @param srcDays    original epoch-day samples (int[], sorted asc)
     * @param srcValues  values at each srcDay (same length)
     * @param targetDays fractional target days (float[], sorted asc)
     * @param out        output buffer, same length as targetDays
     */
    public static void interpolateFloatDays(int[] srcDays, float[] srcValues,
                                            float[] targetDays, float[] out) {
        final int tLen = targetDays.length;
        if (srcDays == null || srcValues == null
                || srcDays.length == 0 || srcDays.length != srcValues.length) {
            for (int i = 0; i < tLen; i++) out[i] = 0f;
            return;
        }

        final int sLen = srcDays.length;
        final float firstDay = (float) srcDays[0];
        final float lastDay  = (float) srcDays[sLen - 1];

        int t = 0;
        while (t < tLen && targetDays[t] <= firstDay) {
            out[t++] = srcValues[0];
        }

        int s = 0;
        while (t < tLen && targetDays[t] < lastDay) {
            final float td = targetDays[t];
            while (s + 1 < sLen && (float) srcDays[s + 1] <= td) s++;

            final float d1 = (float) srcDays[s];
            final float d2 = (float) srcDays[s + 1];
            final float v1 = srcValues[s];
            final float v2 = srcValues[s + 1];

            out[t++] = (d1 == d2) ? v1 : v1 + (v2 - v1) * ((td - d1) / (d2 - d1));
        }

        final float lastVal = srcValues[sLen - 1];
        while (t < tLen) {
            out[t++] = lastVal;
        }
    }

    // ── High-level: sparse API data → dense daily arrays ────────────────────

    /** Result bundle for {@link #convertAndInterpolate}. */
    public static class InterpolationResult {
        public final int[]   days;
        public final float[] values;
        public InterpolationResult(int[] days, float[] values) {
            this.days   = days;
            this.values = values;
        }
    }

    /**
     * Converts sparse date+price lists (e.g. monthly from Yahoo) into a dense
     * daily time-series via linear interpolation.
     *
     * <p>This is the only place where {@code List<String>} / {@code List<Double>}
     * enter the system – they are converted to arrays once.</p>
     */
    public static InterpolationResult convertAndInterpolate(List<String> originalDates,
                                                            List<Double> originalValues) {
        long start = System.currentTimeMillis();
        if (originalDates == null || originalValues == null || originalDates.isEmpty()
                || originalDates.size() != originalValues.size()) {
            return new InterpolationResult(new int[0], new float[0]);
        }

        int[] srcDays = convertDatesToInt(originalDates);
        float[] srcVals = new float[originalValues.size()];
        for (int i = 0; i < srcVals.length; i++) {
            srcVals[i] = originalValues.get(i).floatValue();
        }

        int startDay = srcDays[0];
        int endDay   = srcDays[srcDays.length - 1];
        int length   = endDay - startDay + 1;

        int[] targetDays = new int[length];
        for (int i = 0; i < length; i++) {
            targetDays[i] = startDay + i;
        }

        float[] outValues = interpolate(srcDays, srcVals, targetDays);

        Log.d(TAG, "Interpolation: " + (System.currentTimeMillis() - start) + "ms, " + length + " days");
        return new InterpolationResult(targetDays, outValues);
    }
}
