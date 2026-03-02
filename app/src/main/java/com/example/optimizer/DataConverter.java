package com.example.optimizer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to convert date-based data into day-based data with linear interpolation.
 */
public class DataConverter {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String START_DATE = "1970-01-01"; // Standard epoch start

    /**
     * Converts a date string to the number of days since 1970-01-01.
     */
    public static int dateToDay(String dateString) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        try {
            Date date = sdf.parse(dateString);
            Date startDate = sdf.parse(START_DATE);
            if (date == null || startDate == null) return -1;
            
            long diffInMillies = date.getTime() - startDate.getTime();
            return (int) TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
        } catch (ParseException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Converts the number of days since 1970-01-01 back to a date string.
     */
    public static String dayToDate(int day) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(sdf.parse(START_DATE));
            cal.add(Calendar.DATE, day);
            return sdf.format(cal.getTime());
        } catch (ParseException e) {
            return "";
        }
    }

    /**
     * Result container for converted and interpolated data.
     */
    public static class ConversionResult {
        public List<Integer> days;
        public List<Double> values;
        public List<String> dates;

        public ConversionResult(List<Integer> days, List<Double> values, List<String> dates) {
            this.days = days;
            this.values = values;
            this.dates = dates;
        }
    }

    /**
     * Converts a list of dates and values into a continuous daily list with linear interpolation.
     * Assumes originalDates are sorted chronologically.
     */
    public static ConversionResult convertAndInterpolate(List<String> originalDates, List<Double> originalValues) {
        if (originalDates == null || originalValues == null || originalDates.isEmpty() || originalDates.size() != originalValues.size()) {
            return new ConversionResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        List<Integer> originalDays = new ArrayList<>();
        for (String date : originalDates) {
            originalDays.add(dateToDay(date));
        }

        int startDay = originalDays.get(0);
        int endDay = originalDays.get(originalDays.size() - 1);

        List<Integer> targetDays = new ArrayList<>();
        List<Double> interpolatedValues = new ArrayList<>();
        List<String> targetDates = new ArrayList<>();

        int originalIdx = 0;
        for (int d = startDay; d <= endDay; d++) {
            targetDays.add(d);
            targetDates.add(dayToDate(d));
            
            // Advance pointer to the first entry that is >= current day d
            while (originalIdx < originalDays.size() - 1 && d > originalDays.get(originalIdx)) {
                originalIdx++;
            }

            if (d == originalDays.get(originalIdx)) {
                // Exact match found
                interpolatedValues.add(originalValues.get(originalIdx));
            } else if (originalIdx > 0) {
                // Interpolate between originalIdx - 1 and originalIdx
                int d1 = originalDays.get(originalIdx - 1);
                int d2 = originalDays.get(originalIdx);
                double v1 = originalValues.get(originalIdx - 1);
                double v2 = originalValues.get(originalIdx);

                // Linear interpolation formula: y = y1 + (y2 - y1) * (x - x1) / (x2 - x1)
                double val = v1 + (v2 - v1) * (double)(d - d1) / (d2 - d1);
                interpolatedValues.add(val);
            } else {
                // Should not happen if startDay is originalDays.get(0)
                interpolatedValues.add(originalValues.get(0));
            }
        }

        return new ConversionResult(targetDays, interpolatedValues, targetDates);
    }
}
