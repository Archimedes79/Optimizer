package com.example.optimizer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
     * Converts a list of dates and values into a continuous daily list with linear interpolation.
     * Assumes originalDates are sorted chronologically.
     * Fills the provided output lists directly.
     */
    public static void convertAndInterpolate(List<String> originalDates, List<Double> originalValues, 
                                             List<Integer> outDays, List<Double> outValues) {
        if (originalDates == null || originalValues == null || originalDates.isEmpty() 
                || originalDates.size() != originalValues.size() || outDays == null || outValues == null) {
            return;
        }

        int[] originalDays = new int[originalDates.size()];
        for (int i = 0; i < originalDates.size(); i++) {
            originalDays[i] = dateToDay(originalDates.get(i));
        }

        int startDay = originalDays[0];
        int endDay = originalDays[originalDays.length - 1];

        int originalIdx = 0;
        for (int d = startDay; d <= endDay; d++) {
            outDays.add(d);
            
            // Advance pointer to the first entry that is >= current day d
            while (originalIdx < originalDays.length - 1 && d > originalDays[originalIdx]) {
                originalIdx++;
            }

            if (d == originalDays[originalIdx]) {
                // Exact match found
                outValues.add(originalValues.get(originalIdx));
            } else if (originalIdx > 0) {
                // Interpolate between originalIdx - 1 and originalIdx
                int d1 = originalDays[originalIdx - 1];
                int d2 = originalDays[originalIdx];
                double v1 = originalValues.get(originalIdx - 1);
                double v2 = originalValues.get(originalIdx);

                // Linear interpolation formula: y = y1 + (y2 - y1) * (x - x1) / (x2 - x1)
                double val = v1 + (v2 - v1) * (double)(d - d1) / (double) (d2 - d1);
                outValues.add(val);
            } else {
                outValues.add(originalValues.get(0));
            }
        }
    }
}
