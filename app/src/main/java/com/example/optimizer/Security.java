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
 * Represents a financial security (e.g., Stock, ETF) in the portfolio.
 * Stores values and epoch days using primitive arrays for performance.
 */
public class Security {
    private String name;       // Yahoo Finance Name (Shortname)
    private String alias;      // Original search term or custom name
    private String symbol;     // Yahoo Finance Ticker
    private double[] valuesOverTime;
    private int[] epochDays;   // Days since 1970-01-01
    private double quantity;
    private int color;
    private boolean isFixed;

    public Security() {
        this.valuesOverTime = new double[0];
        this.epochDays = new int[0];
    }

    public Security(String name, String symbol, double quantity) {
        this();
        this.name = name;
        this.symbol = symbol;
        this.quantity = quantity;
        this.color = generateConsistentColor(symbol);
        this.isFixed = false;
    }

    public static int generateConsistentColor(String seed) {
        if (seed == null || seed.isEmpty()) return Color.GRAY;
        Random random = new Random(seed.hashCode());
        return Color.rgb(50 + random.nextInt(150), 50 + random.nextInt(150), 50 + random.nextInt(150));
    }

    private Calendar getCalendarForIndex(int index) {
        if (epochDays == null || index < 0 || index >= epochDays.length) return null;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(1970, 0, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, epochDays[index]);
        return cal;
    }

    // On-demand calculators for specific indices
    public String getYearString(int index) {
        Calendar cal = getCalendarForIndex(index);
        return cal != null ? new SimpleDateFormat("yy", Locale.getDefault()).format(cal.getTime()) : "";
    }

    public String getMonthString(int index) {
        Calendar cal = getCalendarForIndex(index);
        return cal != null ? new SimpleDateFormat("MMM", Locale.getDefault()).format(cal.getTime()) : "";
    }

    public String getDayString(int index) {
        Calendar cal = getCalendarForIndex(index);
        return cal != null ? new SimpleDateFormat("dd", Locale.getDefault()).format(cal.getTime()) : "";
    }

    public int getDayInt(int index) {
        Calendar cal = getCalendarForIndex(index);
        return cal != null ? cal.get(Calendar.DAY_OF_MONTH) : 0;
    }

    public String getFullDateString(int index) {
        Calendar cal = getCalendarForIndex(index);
        return cal != null ? new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime()) : "";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getDisplayName() { return (alias != null && !alias.isEmpty()) ? alias : name; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getIdentifier() { return symbol; }

    public double[] getValuesOverTime() { return valuesOverTime; }
    public int[] getEpochDays() { return epochDays; }

    /**
     * Unified setter to ensure values and days always have equal length.
     * Stores primitive arrays directly.
     */
    public void setHistory(double[] values, int[] days) {
        if (values == null || days == null || values.length != days.length) {
            this.valuesOverTime = new double[0];
            this.epochDays = new int[0];
        } else {
            this.valuesOverTime = values;
            this.epochDays = days;
        }
    }

    // Helper to get all dates as a list (used by MarkerView)
    public List<String> getDates() {
        List<String> dates = new ArrayList<>();
        if (epochDays == null) return dates;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (int i = 0; i < epochDays.length; i++) {
            Calendar cal = getCalendarForIndex(i);
            dates.add(cal != null ? sdf.format(cal.getTime()) : "");
        }
        return dates;
    }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public int getNumberOfEntries() { return valuesOverTime != null ? valuesOverTime.length : 0; }

    public boolean isFixed() { return isFixed; }
    public void setFixed(boolean fixed) { isFixed = fixed; }

    /**
     * Returns a vector of values between startDay and endDay (inclusive) by linearly
     * interpolating across numberOfDays steps.
     * Delegates calculation to DataConverter for consistency.
     */
    public double[] getValueVector(int startDay, int endDay, int numberOfDays) {
        if (numberOfDays <= 0) return new double[0];
        if (valuesOverTime == null || valuesOverTime.length == 0) return new double[numberOfDays];

        double[] targetDates = new double[numberOfDays];
        if (numberOfDays == 1) {
            targetDates[0] = (double) startDay;
        } else {
            double stepSize = (double) (endDay - startDay) / (double) (numberOfDays - 1);
            for (int i = 0; i < numberOfDays; i++) {
                targetDates[i] = startDay + i * stepSize;
            }
        }

        return DataConverter.interpolateValuesToDate(epochDays, valuesOverTime, targetDates);
    }

    /**
     * Helper to perform linear interpolation for a single double day value.
     */
    public double getInterpolatedValue(double d) {
        if (valuesOverTime == null || valuesOverTime.length == 0) return 0;
        double[] result = DataConverter.interpolateValuesToDate(epochDays, valuesOverTime, new double[]{d});
        return result.length > 0 ? result[0] : 0;
    }
}
