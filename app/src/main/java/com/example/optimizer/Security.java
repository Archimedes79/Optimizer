package com.example.optimizer;

import android.graphics.Color;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Represents a financial security (e.g., Stock, ETF) in the portfolio.
 */
public class Security {
    private String name;       // Yahoo Finance Name (Shortname)
    private String alias;      // Original search term
    private String identifier; // WKN, ISIN or Ticker (Yahoo's best available)
    private List<Double> valuesOverTime;
    private List<String> dates; // Corresponding dates for valuesOverTime, format "yyyy-MM-dd"
    private double quantity;
    private int color;

    public Security() {
        this.valuesOverTime = new ArrayList<>();
        this.dates = new ArrayList<>();
    }

    public Security(String name, String identifier, double quantity) {
        this.name = name;
        this.identifier = identifier;
        this.valuesOverTime = new ArrayList<>();
        this.dates = new ArrayList<>();
        this.quantity = quantity;
        this.color = generateConsistentColor(identifier);
    }

    public static int generateConsistentColor(String seed) {
        if (seed == null || seed.isEmpty()) return Color.GRAY;
        Random random = new Random(seed.hashCode());
        return Color.rgb(50 + random.nextInt(150), 50 + random.nextInt(150), 50 + random.nextInt(150));
    }

    // Date Formatting Functions
    private String getFormattedDate(int index, String format) {
        if (index < 0 || index >= dates.size()) return "";
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dates.get(index));
            if (date != null) {
                return new SimpleDateFormat(format, Locale.getDefault()).format(date);
            }
        } catch (ParseException e) {
            // Fallback to simple substring if parsing fails
            String dateStr = dates.get(index);
            if (dateStr.length() == 10) { // "yyyy-MM-dd"
                if (format.equals("yy")) return dateStr.substring(2, 4);
                if (format.equals("MMM")) { // Simple fallback for month
                    String month = dateStr.substring(5, 7);
                    switch(month) {
                        case "01": return "Jan";
                        case "02": return "Feb";
                        case "03": return "Mar";
                        case "04": return "Apr";
                        case "05": return "May";
                        case "06": return "Jun";
                        case "07": return "Jul";
                        case "08": return "Aug";
                        case "09": return "Sep";
                        case "10": return "Oct";
                        case "11": return "Nov";
                        case "12": return "Dec";
                        default: return "";
                    }
                }
                if (format.equals("dd")) return dateStr.substring(8, 10);
            }
        }
        return "";
    }

    public String getYearString(int index) {
        return getFormattedDate(index, "yy");
    }

    public String getMonthString(int index) {
        return getFormattedDate(index, "MMM");
    }

    public String getDayString(int index) {
        return getFormattedDate(index, "dd");
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
        if (this.color == 0 || this.color == Color.GRAY) this.color = generateConsistentColor(identifier);
    }
    
    public List<Double> getValuesOverTime() {
        if (valuesOverTime == null) valuesOverTime = new ArrayList<>();
        return valuesOverTime;
    }
    public void setValuesOverTime(List<Double> values) { this.valuesOverTime = values; }
    
    public List<String> getDates() {
        if (dates == null) dates = new ArrayList<>();
        return dates;
    }
    public void setDates(List<String> dates) { this.dates = dates; }
    
    public void addValue(double value, String date) {
        getValuesOverTime().add(value);
        getDates().add(date);
    }
    
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public int getColor() { return (color == 0) ? color = generateConsistentColor(identifier) : color; }
    public void setColor(int color) { this.color = color; }

    public int getNumberOfEntries() {
        return getValuesOverTime().size();
    }

    public void refreshData() { /* Implementation as before */ }
}
