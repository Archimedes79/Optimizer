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
     * Returns a vector of values between startDay and endDay (inclusive) by linearly
     * interpolating across numberOfDays steps.
     *
     * @param startDay The starting epoch day.
     * @param endDay The ending epoch day.
     * @param numberOfDays The number of sample points to generate.
     * @return A double array of interpolated values.
     */
    public double[] getValueVector(int startDay, int endDay, int numberOfDays) {
        if (numberOfDays <= 0) return new double[0];
        double[] vector = new double[numberOfDays];
        if (valuesOverTime.isEmpty()) return vector;

        if (numberOfDays == 1) {
            vector[0] = getInterpolatedValue(startDay);
            return vector;
        }

        double stepSize = (double) (endDay - startDay) / (double)(numberOfDays - 1);
        int epochIdx = 0;

        for (int i = 0; i < numberOfDays; i++) {
            double d = startDay + i * stepSize;
            
            if (d <= epochDays.get(0)) {
                vector[i] = valuesOverTime.get(0);
                continue;
            }
            if (d >= epochDays.get(epochDays.size() - 1)) {
                vector[i] = valuesOverTime.get(valuesOverTime.size() - 1);
                continue;
            }

            // Advance epochIdx so that epochDays[epochIdx] <= d < epochDays[epochIdx+1]
            while (epochIdx + 1 < epochDays.size() && epochDays.get(epochIdx + 1) <= d) {
                epochIdx++;
            }
            
            double d1 = epochDays.get(epochIdx);
            double d2 = epochDays.get(epochIdx + 1);
            double v1 = valuesOverTime.get(epochIdx);
            double v2 = valuesOverTime.get(epochIdx + 1);
            
            if (d1 == d2) {
                vector[i] = v1;
            } else {
                vector[i] = v1 + (v2 - v1) * (d - d1) / (d2 - d1);
            }
        }
        return vector;
    }

    /**
     * Helper to perform linear interpolation for a single double day value.
     */
    private double getInterpolatedValue(double d) {
        if (epochDays == null || epochDays.isEmpty()) return 0;

        int n = epochDays.size();
        if (d <= epochDays.get(0)) return valuesOverTime.get(0);
        if (d >= epochDays.get(n - 1)) return valuesOverTime.get(n - 1);

        int i = Collections.binarySearch(epochDays, (int) Math.floor(d));
        if (i < 0) {
            i = -(i + 1) - 1;
        }

        double d1 = epochDays.get(i);
        double d2 = epochDays.get(i + 1);
        double v1 = valuesOverTime.get(i);
        double v2 = valuesOverTime.get(i + 1);

        if (d1 == d2) return v1;
        return v1 + (v2 - v1) * (d - d1) / (d2 - d1);
    }

    /**
     * Returns a vector of values for each day from startDay to endDay (inclusive).
     * Missing values are filled using the last known value (forward fill).
     */
    public double[] getValueVector(int startDay, int endDay) {
        int length = endDay - startDay + 1;
        if (length <= 0) return new double[0];
        double[] vector = new double[length];
        
        int epochIdx = Collections.binarySearch(epochDays, startDay);
        if (epochIdx < 0) epochIdx = -(epochIdx + 1);
        
        double currentVal = 0;
        if (epochIdx > 0) {
            currentVal = valuesOverTime.get(epochIdx - 1);
        } else if (!valuesOverTime.isEmpty()) {
            currentVal = valuesOverTime.get(0);
        }

        for (int i = 0; i < length; i++) {
            int d = startDay + i;
            while (epochIdx < epochDays.size() && epochDays.get(epochIdx) <= d) {
                currentVal = valuesOverTime.get(epochIdx);
                epochIdx++;
            }
            vector[i] = currentVal;
        }
        return vector;
    }
}
