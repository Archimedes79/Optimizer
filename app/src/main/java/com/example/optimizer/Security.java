package com.example.optimizer;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Represents a financial security (e.g., Stock, ETF) in the portfolio.
 * This class stores the security's identity, ownership quantity, assigned UI color,
 * and its historical price performance data.
 */
public class Security {
    private String name;
    private String customName;
    private String identifier; // WKN or ISIN
    private List<Double> valuesOverTime;
    private List<String> dates; // Corresponding dates for valuesOverTime
    private int quantity;
    private int color;

    public Security() {
        // Required for Gson
        this.valuesOverTime = new ArrayList<>();
        this.dates = new ArrayList<>();
    }

    public Security(String name, String identifier, int quantity) {
        this.name = name;
        this.identifier = identifier;
        this.valuesOverTime = new ArrayList<>();
        this.dates = new ArrayList<>();
        this.quantity = quantity;
        this.color = generateConsistentColor(identifier);
    }

    public Security(String name, String identifier, int quantity, int color) {
        this.name = name;
        this.identifier = identifier;
        this.valuesOverTime = new ArrayList<>();
        this.dates = new ArrayList<>();
        this.quantity = quantity;
        this.color = color;
    }

    public static int generateConsistentColor(String seed) {
        if (seed == null || seed.isEmpty()) {
            return Color.GRAY;
        }
        Random random = new Random(seed.hashCode());
        int r = 50 + random.nextInt(150);
        int g = 50 + random.nextInt(150);
        int b = 50 + random.nextInt(150);
        return Color.rgb(r, g, b);
    }

    public String getName() {
        return (customName != null && !customName.isEmpty()) ? customName : name;
    }

    public String getOriginalName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
        if (this.color == 0 || this.color == Color.GRAY) {
            this.color = generateConsistentColor(identifier);
        }
    }

    public List<Double> getValuesOverTime() {
        if (valuesOverTime == null) valuesOverTime = new ArrayList<>();
        return valuesOverTime;
    }

    public void setValuesOverTime(List<Double> values) {
        this.valuesOverTime = values;
    }

    public List<String> getDates() {
        if (dates == null) dates = new ArrayList<>();
        return dates;
    }

    public void setDates(List<String> dates) {
        this.dates = dates;
    }

    public void addValue(double value, String date) {
        getValuesOverTime().add(value);
        getDates().add(date);
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getColor() {
        if (color == 0) {
            color = generateConsistentColor(identifier);
        }
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getNumberOfEntries() {
        return getValuesOverTime().size();
    }

    public void refreshData() {
        getValuesOverTime().clear();
        getDates().clear();
        Random random = new Random(identifier.hashCode());
        int years = 2 + random.nextInt(18); 
        int dataPoints = years * 12; 
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -dataPoints);
        
        double base = 10 + random.nextDouble() * 100;
        for (int i = 0; i < dataPoints; i++) {
            base *= (1.005 + (random.nextDouble() - 0.45) * 0.04);
            String dateStr = String.format(Locale.getDefault(), "%04d-%02d-01", 
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
            addValue(Math.max(1, base), dateStr);
            cal.add(Calendar.MONTH, 1);
        }
    }
}
