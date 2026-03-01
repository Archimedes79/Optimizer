package com.example.optimizer;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;
import java.util.Locale;

/**
 * Custom MarkerView for the Portfolio Graph.
 * Uses the real date strings from the security data.
 */
public class PortfolioMarkerView extends MarkerView {

    private final TextView tvDate;
    private final TextView tvValue;
    private List<String> dates;
    private int offsetFromStart = 0;

    public PortfolioMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvDate = findViewById(R.id.tvMarkerDate);
        tvValue = findViewById(R.id.tvMarkerValue);
    }

    public void setDateSource(List<String> dates, int offsetFromStart) {
        this.dates = dates;
        this.offsetFromStart = offsetFromStart;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int index = (int) e.getX();
        
        if (dates != null && !dates.isEmpty()) {
            int dateIndex = offsetFromStart + index;
            if (dateIndex >= 0 && dateIndex < dates.size()) {
                tvDate.setText(dates.get(dateIndex));
            } else {
                tvDate.setText("Unknown");
            }
        } else {
            tvDate.setText("No Date");
        }
        
        tvValue.setText(String.format(Locale.getDefault(), "Value: %.2f", e.getY()));
        
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}
