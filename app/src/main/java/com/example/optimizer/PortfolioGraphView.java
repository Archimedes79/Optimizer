package com.example.optimizer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PortfolioGraphView extends FrameLayout {

    private LineChart chart;
    private int currentMaxEntries = 240;
    private float currentVisibleCount = 240;
    private PortfolioMarkerView markerView;
    private OnVisibleRangeChangeListener visibleRangeChangeListener;
    private List<Security> currentSecurities;

    public interface OnVisibleRangeChangeListener {
        void onVisibleRangeChanged(float visibleCount);
    }

    public PortfolioGraphView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PortfolioGraphView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setOnVisibleRangeChangeListener(OnVisibleRangeChangeListener listener) {
        this.visibleRangeChangeListener = listener;
    }

    public float getCurrentVisibleCount() {
        return currentVisibleCount;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(Context context) {
        chart = new LineChart(context);
        addView(chart);

        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(false); // Using custom zoom logic
        chart.setScaleEnabled(false); // Using custom zoom logic
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setAutoScaleMinMaxEnabled(true); // Y-axis scales to visible data

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new DateFormatter());

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);

        markerView = new PortfolioMarkerView(context, R.layout.graph_marker);
        chart.setMarker(markerView);

        chart.getLegend().setEnabled(true);

        // Custom Zoom/Drag Logic
        chart.setOnTouchListener(new OnTouchListener() {
            private float startX;
            private float startVisibleCount;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startVisibleCount = currentVisibleCount;
                        chart.onTouchEvent(event); // Still allow chart to handle markers
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - startX;
                        // Sensitivity: Dragging across the full width represents the full data range
                        float sensitivity = (float) currentMaxEntries / chart.getWidth();
                        
                        // Shift Right (dx > 0) -> Zoom Out (Increase visible count)
                        // Shift Left (dx < 0) -> Zoom In (Decrease visible count)
                        currentVisibleCount = startVisibleCount + dx * sensitivity;
                        
                        // Bounds: Min 5 points to Max available points
                        if (currentVisibleCount < 5) currentVisibleCount = 5;
                        if (currentVisibleCount > currentMaxEntries) currentVisibleCount = currentMaxEntries;
                        
                        applyZoom(true); // Notify listeners when changed by user touch
                        chart.onTouchEvent(event);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        chart.onTouchEvent(event);
                        v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void applyZoom(boolean notifyListener) {
        if (chart.getData() == null) return;
        
        // Force the window to always end at the most recent date (currentMaxEntries - 1)
        chart.setVisibleXRangeMaximum(currentVisibleCount);
        chart.setVisibleXRangeMinimum(currentVisibleCount);
        chart.moveViewToX(currentMaxEntries - currentVisibleCount);
        chart.invalidate();

        if (notifyListener && visibleRangeChangeListener != null) {
            visibleRangeChangeListener.onVisibleRangeChanged(currentVisibleCount);
        }
    }

    public void setSecurities(List<Security> securities) {
        if (securities == null) return;
        double[] quantities = new double[securities.size()];
        for (int i = 0; i < securities.size(); i++) {
            quantities[i] = securities.get(i).getQuantity();
        }
        setSecuritiesWithQuantities(securities, quantities);
    }

    public void setSecuritiesWithQuantities(List<Security> securities, double[] quantities) {
        this.currentSecurities = securities;
        if (securities == null || securities.isEmpty() || quantities == null || quantities.length != securities.size()) {
            chart.clear();
            return;
        }

        int minEntries = Integer.MAX_VALUE;
        for (Security s : securities) {
            minEntries = Math.min(minEntries, s.getValuesOverTime().size());
        }
        
        if (minEntries < 2) {
            chart.clear();
            return;
        }

        int previousMaxEntries = this.currentMaxEntries;
        this.currentMaxEntries = minEntries;
        
        // Maintain current zoom percentage if data range changed
        if (previousMaxEntries != minEntries) {
            float zoomRatio = currentVisibleCount / previousMaxEntries;
            currentVisibleCount = minEntries * zoomRatio;
        }
        
        if (currentVisibleCount > minEntries) currentVisibleCount = minEntries;
        if (currentVisibleCount < 5) currentVisibleCount = 5;

        if (markerView != null) {
            Security first = securities.get(0);
            markerView.setDateSource(first.getDates(), first.getValuesOverTime().size() - minEntries);
        }

        float lastTotalValue = 0;
        for (int i = 0; i < securities.size(); i++) {
            List<Double> values = securities.get(i).getValuesOverTime();
            lastTotalValue += (float) (values.get(values.size() - 1) * quantities[i]);
        }

        List<ILineDataSet> dataSets = new ArrayList<>();

        for (int i = 0; i < securities.size(); i++) {
            Security s = securities.get(i);
            List<Double> values = s.getValuesOverTime();
            int startIdx = values.size() - minEntries;
            double lastValue = values.get(values.size() - 1);
            
            List<Entry> entries = new ArrayList<>();
            for (int j = 0; j < minEntries; j++) {
                float normalizedValue = (float) ((values.get(startIdx + j) / lastValue) * 100.0);
                entries.add(new Entry(j, normalizedValue));
            }
            
            LineDataSet set = new LineDataSet(entries, s.getName());
            int baseColor = s.getColor();
            int alphaColor = Color.argb(120, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            set.setColor(alphaColor);
            set.setDrawCircles(false);
            set.setLineWidth(1.0f);
            dataSets.add(set);
        }

        List<Entry> totalEntries = new ArrayList<>();
        if (lastTotalValue > 0) {
            for (int j = 0; j < minEntries; j++) {
                float sum = 0;
                for (int i = 0; i < securities.size(); i++) {
                    List<Double> values = securities.get(i).getValuesOverTime();
                    sum += (float) (values.get(values.size() - minEntries + j) * quantities[i]);
                }
                totalEntries.add(new Entry(j, (sum / lastTotalValue) * 100.0f));
            }
        }

        LineDataSet totalSet = new LineDataSet(totalEntries, "Portfolio Index");
        totalSet.setColor(Color.BLACK);
        totalSet.setLineWidth(3f);
        totalSet.setDrawCircles(false);
        dataSets.add(totalSet);

        chart.setData(new LineData(dataSets));
        
        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(0);
        xAxis.setAxisMaximum(minEntries - 1);
        
        applyZoom(false); // Don't notify listeners during data updates to avoid infinite recursion
    }

    private class DateFormatter extends ValueFormatter {
        private final SimpleDateFormat monthFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());

        @Override
        public String getFormattedValue(float value) {
            int index = (int) value;
            
            // Try to use real dates from the first security if available
            if (currentSecurities != null && !currentSecurities.isEmpty()) {
                Security first = currentSecurities.get(0);
                List<String> dates = first.getDates();
                if (dates != null && !dates.isEmpty()) {
                    int offset = first.getValuesOverTime().size() - currentMaxEntries;
                    int dateIndex = offset + index;
                    if (dateIndex >= 0 && dateIndex < dates.size()) {
                        return dates.get(dateIndex);
                    }
                }
            }
            
            // Fallback to month calculation
            Calendar cal = Calendar.getInstance();
            int monthsBack = (currentMaxEntries - 1) - index;
            cal.add(Calendar.MONTH, -monthsBack);
            return monthFormat.format(cal.getTime());
        }
    }
}
