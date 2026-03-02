package com.example.optimizer;

import android.graphics.Color;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * Represents a financial security (e.g., Stock, ETF) in the portfolio.
 * Stores only values and epoch days, calculating date components on demand.
 */
public class Security {
    private String name;       // Yahoo Finance Name (Shortname)
    private String alias;      // Original search term or custom name
    private String symbol;     // Yahoo Finance Ticker
    private List<Double> valuesOverTime;
    private List<Integer> epochDays;  // Days since 1970-01-01
    private double quantity;
    private int color;
    private boolean isFixed;

    public Security() {
        this.valuesOverTime = new ArrayList<>();
        this.epochDays = new ArrayList<>();
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
        if (epochDays == null || index < 0 || index >= epochDays.size()) return null;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(1970, 0, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, epochDays.get(index));
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

    public List<Double> getValuesOverTime() { return valuesOverTime; }
    public void setValuesOverTime(List<Double> values) { this.valuesOverTime = values; }

    public List<Integer> getEpochDays() { return epochDays; }
    public void setEpochDays(List<Integer> epochDays) { this.epochDays = epochDays; }

    // Helper to get all dates as a list (used by MarkerView)
    public List<String> getDates() {
        List<String> dates = new ArrayList<>();
        if (epochDays != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            for (int i = 0; i < epochDays.size(); i++) {
                Calendar cal = getCalendarForIndex(i);
                dates.add(cal != null ? sdf.format(cal.getTime()) : "");
            }
        }
        return dates;
    }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public int getNumberOfEntries() { return valuesOverTime.size(); }

    public boolean isFixed() { return isFixed; }
    public void setFixed(boolean fixed) { isFixed = fixed; }

    /**
     * Returns a vector of values for each day from startDay to endDay (inclusive).
     * Optimizes lookup by finding the start index and scanning forward.
     * Missing values are filled using the last known value (forward fill).
     */
    public double[] getValueVector(int startDay, int endDay) {
        int length = endDay - startDay + 1;
        if (length <= 0) return new double[0];
        double[] vector = new double[length];
        
        // Find the starting point in the sorted epochDays list
        int epochIdx = Collections.binarySearch(epochDays, startDay);
        if (epochIdx < 0) {
            epochIdx = -(epochIdx + 1);
        }
        
        // Determine the initial value (carry over from before startDay or backfill first entry)
        double currentVal = 0;
        if (epochIdx > 0) {
            currentVal = valuesOverTime.get(epochIdx - 1);
        } else if (!valuesOverTime.isEmpty()) {
            currentVal = valuesOverTime.get(0);
        }

        // Fill the vector by scanning epochDays sequentially
        for (int i = 0; i < length; i++) {
            int d = startDay + i;
            // Catch up epochIdx to the current day d
            while (epochIdx < epochDays.size() && epochDays.get(epochIdx) <= d) {
                currentVal = valuesOverTime.get(epochIdx);
                epochIdx++;
            }
            vector[i] = currentVal;
        }
        return vector;
    }
}
