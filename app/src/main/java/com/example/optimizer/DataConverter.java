package com.example.optimizer;

import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to convert date-based data into day-based data with linear interpolation.
 */
public class DataConverter {
    private static final String TAG = "DataConverter";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    /**
     * Converts a date string to the number of days since 1970-01-01.
     */
    public static int dateToDay(String dateString) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        try {
            Date date = sdf.parse(dateString);
            if (date == null) return -1;
            
            // Standard Java epoch is 1970-01-01 00:00:00 UTC
            long diffInMillies = date.getTime();
            return (int) TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
        } catch (ParseException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Converts a list of string dates to an integer array of epoch days.
     */
    public static int[] convertDatesToInt(List<String> dates) {
        int[] outDays = new int[dates.size()];
        for (int i = 0; i < dates.size(); i++) {
            outDays[i] = dateToDay(dates.get(i));
        }
        return outDays;
    }

    /**
     * Converts a list of string dates to a double array of epoch days.
     */
    public static double[] convertDatesToDouble(List<String> dates) {
        double[] out = new double[dates.size()];
        for (int i = 0; i < dates.size(); i++) {
            out[i] = (double) dateToDay(dates.get(i));
        }
        return out;
    }

    /**
     * Interpolates values for target dates based on original dates and values.
     * Assumes both originalDays and targetDates are sorted in ascending order.
     * This is the single source of truth for interpolation logic.
     */
    public static double[] interpolateValuesToDate(int[] originalDays, double[] originalValues, double[] targetDates) {
        if (targetDates == null) return new double[0];
        double[] outValues = new double[targetDates.length];
        if (originalDays == null || originalValues == null || 
                originalDays.length == 0 || originalValues.length == 0 || originalDays.length != originalValues.length) {
            return outValues;
        }

        int originalIdx = 0;
        int n = originalDays.length;

        for (int i = 0; i < targetDates.length; i++) {
            double d = targetDates[i];
            
            // Clamp to boundaries
            if (d <= (double) originalDays[0]) {
                outValues[i] = originalValues[0];
                continue;
            }
            if (d >= (double) originalDays[n - 1]) {
                outValues[i] = originalValues[n - 1];
                continue;
            }

            // Advance pointer so that originalDays[originalIdx] <= d < originalDays[originalIdx+1]
            // Efficient for sorted targetDates (O(N + M))
            while (originalIdx + 1 < n && (double) originalDays[originalIdx + 1] <= d) {
                originalIdx++;
            }

            double d1 = (double) originalDays[originalIdx];
            double d2 = (double) originalDays[originalIdx + 1];
            double v1 = originalValues[originalIdx];
            double v2 = originalValues[originalIdx + 1];

            if (d1 == d2) {
                outValues[i] = v1;
            } else {
                // Linear interpolation formula: y = y1 + (y2 - y1) * (x - x1) / (x2 - x1)
                outValues[i] = v1 + (v2 - v1) * (d - d1) / (d2 - d1);
            }
        }
        return outValues;
    }

    public static class InterpolationResult {
        public final int[] days;
        public final double[] values;
        public InterpolationResult(int[] days, double[] values) {
            this.days = days;
            this.values = values;
        }
    }

    /**
     * Converts a list of dates and values into a continuous daily list with linear interpolation.
     * Returns a 2-element Object array containing int[] epochDays and double[] values.
     */
    public static InterpolationResult convertAndInterpolate(List<String> originalDates, List<Double> originalValues) {
        long start = System.currentTimeMillis();
        if (originalDates == null || originalValues == null || originalDates.isEmpty() 
                || originalDates.size() != originalValues.size()) {
            return new InterpolationResult(new int[0], new double[0]);
        }

        int[] originalDaysArr = convertDatesToInt(originalDates);
        double[] originalValuesArr = new double[originalValues.size()];
        for (int i = 0; i < originalValues.size(); i++) originalValuesArr[i] = originalValues.get(i);

        int startDay = originalDaysArr[0];
        int endDay = originalDaysArr[originalDaysArr.length - 1];

        int length = endDay - startDay + 1;
        int[] outDays = new int[length];
        double[] targetDates = new double[length];
        for (int i = 0; i < length; i++) {
            int d = startDay + i;
            outDays[i] = d;
            targetDates[i] = (double) d;
        }

        double[] outValues = interpolateValuesToDate(originalDaysArr, originalValuesArr, targetDates);
        Log.d(TAG, "Interpolation took " + (System.currentTimeMillis() - start) + "ms for " + length + " days");
        return new InterpolationResult(outDays, outValues);
    }

    public static List<Double> doubleArrayToList(double[] array) {
        if (array == null) return new ArrayList<>();
        List<Double> list = new ArrayList<>(array.length);
        for (double d : array) list.add(d);
        return list;
    }

    public static List<Integer> intArrayToList(int[] array) {
        if (array == null) return new ArrayList<>();
        List<Integer> list = new ArrayList<>(array.length);
        for (int i : array) list.add(i);
        return list;
    }
}
