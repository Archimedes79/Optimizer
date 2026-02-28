package com.example.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Security {
    private String name;
    private String customName;
    private String identifier; // WKN or ISO
    private List<Double> valuesOverTime;
    private int quantity;

    public Security(String name, String identifier, int quantity) {
        this.name = name;
        this.identifier = identifier;
        this.valuesOverTime = new ArrayList<>();
        this.quantity = quantity;
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
    }

    public List<Double> getValuesOverTime() {
        return valuesOverTime;
    }

    public void setValuesOverTime(List<Double> values) {
        this.valuesOverTime = values;
    }

    public void addValue(double value) {
        this.valuesOverTime.add(value);
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getNumberOfEntries() {
        return valuesOverTime.size();
    }

    /**
     * Refreshes the time series values from the "database" (mocked).
     * Fetches up to 20 years of data (240 monthly points) or the maximum available.
     */
    public void refreshData() {
        this.valuesOverTime.clear();
        
        // Mocking database access
        Random random = new Random(identifier.hashCode()); // Use identifier to get consistent "mock" data
        
        // Randomly decide how many years of data this security has (between 2 and 20 years)
        int years = 2 + random.nextInt(19); 
        int dataPoints = years * 12; // Monthly data
        
        double base = 10 + random.nextDouble() * 100;
        for (int i = 0; i < dataPoints; i++) {
            // Growth simulation
            base *= (1.005 + (random.nextDouble() - 0.45) * 0.04);
            addValue(Math.max(1, base));
        }
    }
}
