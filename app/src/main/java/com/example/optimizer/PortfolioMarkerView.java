package com.example.optimizer;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Custom MarkerView for the Portfolio Graph.
 * Handles the "interaction" of reading out time (date) and value when a point is selected.
 */
public class PortfolioMarkerView extends MarkerView {

    private final TextView tvDate;
    private final TextView tvValue;
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());
    private int maxEntries = 240;

    public PortfolioMarkerView(Context context, int layoutResource, int maxEntries) {
        super(context, layoutResource);
        tvDate = findViewById(R.id.tvMarkerDate);
        tvValue = findViewById(R.id.tvMarkerValue);
        this.maxEntries = maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        Calendar cal = Calendar.getInstance();
        // Today corresponds to the last index in the series (currentMaxEntries - 1)
        int monthsBack = (maxEntries - 1) - (int) e.getX();
        cal.add(Calendar.MONTH, -monthsBack);
        Date date = cal.getTime();
        
        tvDate.setText(monthFormat.format(date));
        tvValue.setText(String.format(Locale.getDefault(), "Value: %.2f", e.getY()));
        
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Center the marker horizontally and place it above the point
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}
