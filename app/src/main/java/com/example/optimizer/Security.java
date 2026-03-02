package com.example.optimizer;

import android.graphics.Color;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * A financial security (stock, ETF, …) held in the portfolio.
 *
 * <p>Price history is stored as primitive arrays for speed:
 * {@code float[] valuesOverTime} and {@code int[] epochDays}.
 *
 * <p>{@code startIndex} / {@code endIndex} mark the sub-range that overlaps
 * with all other securities in the portfolio.  They are maintained by
 * {@link Portfolio#recalculateCommonRange()} and default to the full array.
 */
public class Security {
    private String name;       // Yahoo Finance short name
    private String alias;      // user-given custom name
    private String symbol;     // Yahoo Finance ticker
    private float[] valuesOverTime;   // daily prices in EUR
    private int[]   epochDays;        // days since 1970-01-01
    private double  quantity;
    private int     color;
    private boolean isFixed;

    /** First array index inside the portfolio-wide common date range. */
    private transient int startIndex;
    /** Last array index (inclusive) inside the common date range. */
    private transient int endIndex;

    // ── Constructors ────────────────────────────────────────────────────────

    public Security() {
        this.valuesOverTime = new float[0];
        this.epochDays = new int[0];
        this.startIndex = 0;
        this.endIndex   = 0;
    }

    public Security(String name, String symbol, double quantity) {
        this();
        this.name     = name;
        this.symbol   = symbol;
        this.quantity  = quantity;
        this.color    = generateConsistentColor(symbol);
        this.isFixed  = false;
    }

    // ── Colour ──────────────────────────────────────────────────────────────

    /** Deterministic colour derived from ticker symbol. */
    public static int generateConsistentColor(String seed) {
        if (seed == null || seed.isEmpty()) return Color.GRAY;
        Random rng = new Random(seed.hashCode());
        return Color.rgb(50 + rng.nextInt(150), 50 + rng.nextInt(150), 50 + rng.nextInt(150));
    }

    // ── Date formatting (on demand, for graph axis labels / marker) ─────────

    private Calendar getCalendarForIndex(int index) {
        if (epochDays == null || index < 0 || index >= epochDays.length) return null;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(1970, 0, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, epochDays[index]);
        return cal;
    }

    /** e.g. "24" */
    public String getYearString(int index) {
        Calendar c = getCalendarForIndex(index);
        return c != null ? new SimpleDateFormat("yy", Locale.getDefault()).format(c.getTime()) : "";
    }
    /** e.g. "Jan" */
    public String getMonthString(int index) {
        Calendar c = getCalendarForIndex(index);
        return c != null ? new SimpleDateFormat("MMM", Locale.getDefault()).format(c.getTime()) : "";
    }
    /** e.g. "07" */
    public String getDayString(int index) {
        Calendar c = getCalendarForIndex(index);
        return c != null ? new SimpleDateFormat("dd", Locale.getDefault()).format(c.getTime()) : "";
    }
    /** Day-of-month as int. */
    public int getDayInt(int index) {
        Calendar c = getCalendarForIndex(index);
        return c != null ? c.get(Calendar.DAY_OF_MONTH) : 0;
    }
    /** "yyyy-MM-dd" */
    public String getFullDateString(int index) {
        Calendar c = getCalendarForIndex(index);
        return c != null ? new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime()) : "";
    }

    // ── Basic getters / setters ─────────────────────────────────────────────

    public String  getName()        { return name; }
    public void    setName(String n){ this.name = n; }
    public String  getAlias()       { return alias; }
    public void    setAlias(String a){ this.alias = a; }
    /** Returns alias if set, otherwise name. */
    public String  getDisplayName() { return (alias != null && !alias.isEmpty()) ? alias : name; }
    public String  getSymbol()      { return symbol; }
    public void    setSymbol(String s){ this.symbol = s; }
    public String  getIdentifier()  { return symbol; }

    public float[] getValuesOverTime() { return valuesOverTime; }
    public int[]   getEpochDays()      { return epochDays; }

    /**
     * Sets price history.  Resets startIndex/endIndex to cover the full array.
     * Call {@link Portfolio#recalculateCommonRange()} afterwards if the security
     * is already part of a portfolio.
     */
    public void setHistory(float[] values, int[] days) {
        if (values == null || days == null || values.length != days.length) {
            this.valuesOverTime = new float[0];
            this.epochDays      = new int[0];
            this.startIndex = 0;
            this.endIndex   = 0;
        } else {
            this.valuesOverTime = values;
            this.epochDays      = days;
            this.startIndex = 0;
            this.endIndex   = days.length - 1;
        }
    }

    /** All dates as "yyyy-MM-dd" strings (used by MarkerView). */
    public List<String> getDates() {
        List<String> dates = new ArrayList<>();
        if (epochDays == null) return dates;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (int i = 0; i < epochDays.length; i++) {
            Calendar c = getCalendarForIndex(i);
            dates.add(c != null ? sdf.format(c.getTime()) : "");
        }
        return dates;
    }

    public double getQuantity()            { return quantity; }
    public void   setQuantity(double q)    { this.quantity = q; }
    public int    getColor()               { return color; }
    public void   setColor(int c)          { this.color = c; }
    /** Total entries in the full array (not just common range). */
    public int    getNumberOfEntries()     { return valuesOverTime != null ? valuesOverTime.length : 0; }
    public boolean isFixed()               { return isFixed; }
    public void    setFixed(boolean fixed)  { this.isFixed = fixed; }

    // ── Common-range index management ───────────────────────────────────────

    /** Epoch day at the start of the common range. */
    public int getStartDay() {
        if (epochDays == null || epochDays.length == 0) return 0;
        return epochDays[startIndex];
    }

    /** Epoch day at the end of the common range (inclusive). */
    public int getEndDay() {
        if (epochDays == null || epochDays.length == 0) return 0;
        return epochDays[endIndex];
    }

    /** Binary-search: sets startIndex to the first position with epochDay >= day. */
    public void setStartIndex(int day) {
        if (epochDays == null || epochDays.length == 0) { startIndex = 0; return; }
        int lo = 0, hi = epochDays.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (epochDays[mid] < day) lo = mid + 1; else hi = mid;
        }
        startIndex = lo;
    }

    /** Binary-search: sets endIndex to the last position with epochDay <= day. */
    public void setEndIndex(int day) {
        if (epochDays == null || epochDays.length == 0) { endIndex = 0; return; }
        int lo = 0, hi = epochDays.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (epochDays[mid] > day) hi = mid - 1; else lo = mid;
        }
        endIndex = lo;
    }

    public int getStartIndex() { return startIndex; }
    public int getEndIndex()   { return endIndex; }

    /** Number of entries in the common sub-range (inclusive). */
    public int getCommonRangeLength() {
        return (epochDays != null && epochDays.length > 0) ? endIndex - startIndex + 1 : 0;
    }

    // ── Interpolation helpers ───────────────────────────────────────────────

    /**
     * Returns {@code numberOfDays} equidistant interpolated values between
     * {@code day0} and {@code day1} (inclusive).  Allocates a new float[].
     */
    public float[] getValueVector(int day0, int day1, int numberOfDays) {
        if (numberOfDays <= 0) return new float[0];
        if (valuesOverTime == null || valuesOverTime.length == 0) return new float[numberOfDays];

        int[] targetDays = buildTargetDays(day0, day1, numberOfDays);
        return DataConverter.interpolate(epochDays, valuesOverTime, targetDays);
    }

    /**
     * Like {@link #getValueVector} but writes into the caller-provided buffers.
     * Avoids allocation when called repeatedly (e.g. per-security in a loop).
     *
     * @param day0       first epoch day
     * @param day1       last epoch day
     * @param targetBuf  pre-allocated int[numberOfDays] for target days
     * @param outBuf     pre-allocated float[numberOfDays] for results
     */
    public void getValueVectorInto(int day0, int day1, int[] targetBuf, float[] outBuf) {
        buildTargetDaysInto(day0, day1, targetBuf);
        if (valuesOverTime == null || valuesOverTime.length == 0) {
            for (int i = 0; i < outBuf.length; i++) outBuf[i] = 0f;
        } else {
            DataConverter.interpolateInto(epochDays, valuesOverTime, targetBuf, outBuf);
        }
    }

    /** Interpolated price for a single epoch day. */
    public float getInterpolatedValue(int day) {
        if (valuesOverTime == null || valuesOverTime.length == 0) return 0f;
        float[] r = DataConverter.interpolate(epochDays, valuesOverTime, new int[]{day});
        return r.length > 0 ? r[0] : 0f;
    }

    // ── Normalised values (for drawing) ─────────────────────────────────────

    /**
     * Returns the common-range slice of values normalised so the <b>last</b>
     * value equals 100.  Useful for chart drawing where every security must
     * be on the same scale.
     *
     * @return float[commonRangeLength] normalised to 100 at endIndex, or empty
     */
    public float[] getNormalizedValues() {
        int len = getCommonRangeLength();
        if (len < 1 || valuesOverTime == null) return new float[0];
        float lastVal = valuesOverTime[endIndex];
        if (lastVal == 0f) return new float[len];

        float scale = 100.0f / lastVal;
        float[] out = new float[len];
        for (int i = 0; i < len; i++) {
            out[i] = valuesOverTime[startIndex + i] * scale;
        }
        return out;
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private static int[] buildTargetDays(int day0, int day1, int n) {
        int[] t = new int[n];
        buildTargetDaysInto(day0, day1, t);
        return t;
    }

    private static void buildTargetDaysInto(int day0, int day1, int[] buf) {
        int n = buf.length;
        if (n == 1) { buf[0] = day0; return; }
        float step = (float)(day1 - day0) / (float)(n - 1);
        for (int i = 0; i < n; i++) {
            buf[i] = day0 + Math.round(i * step);
        }
    }
}
