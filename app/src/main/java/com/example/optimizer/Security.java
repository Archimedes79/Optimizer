package com.example.optimizer;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents a financial security (e.g., Stock, ETF) in the portfolio.
 */
public class Security {
    private String name;       // Yahoo Finance Name (Shortname)
    private String alias;      // Original search term
    private String identifier; // WKN, ISIN or Ticker (Yahoo's best available)
    private List<Double> valuesOverTime;
    private List<String> dates; // Corresponding dates for valuesOverTime
    private int quantity;
    private int color;

    public Security() {
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

    public static int generateConsistentColor(String seed) {
        if (seed == null || seed.isEmpty()) return Color.GRAY;
        Random random = new Random(seed.hashCode());
        return Color.rgb(50 + random.nextInt(150), 50 + random.nextInt(150), 50 + random.nextInt(150));
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
    
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getColor() { return (color == 0) ? color = generateConsistentColor(identifier) : color; }
    public void setColor(int color) { this.color = color; }

    public int getNumberOfEntries() {
        return getValuesOverTime().size();
    }

    public void refreshData() { /* Implementation as before */ }
}
