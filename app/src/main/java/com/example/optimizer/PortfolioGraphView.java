package com.example.optimizer;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PortfolioGraphView extends FrameLayout {

    private LineChart chart;
    private final int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.LTGRAY, Color.DKGRAY};
    private int currentMaxEntries = 240;
    private PortfolioMarkerView markerView;

    public PortfolioGraphView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PortfolioGraphView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        chart = new LineChart(context);
        addView(chart);

        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new DateFormatter());

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        
        // Right axis for normalized values (starting at 100)
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setDrawGridLines(false);
        rightAxis.setAxisMinimum(0f);

        markerView = new PortfolioMarkerView(context, R.layout.graph_marker, currentMaxEntries);
        chart.setMarker(markerView);

        chart.getLegend().setEnabled(true);
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
        if (securities == null || securities.isEmpty() || quantities == null || quantities.length != securities.size()) {
            chart.clear();
            return;
        }

        // Find the MINIMUM number of entries to truncate the chart to the common period
        int minEntries = Integer.MAX_VALUE;
        for (Security s : securities) {
            minEntries = Math.min(minEntries, s.getValuesOverTime().size());
        }
        
        if (minEntries < 2) {
            chart.clear();
            return;
        }

        this.currentMaxEntries = minEntries;
        if (markerView != null) {
            markerView.setMaxEntries(minEntries);
        }

        List<ILineDataSet> dataSets = new ArrayList<>();

        // Individual lines - Normalized to 100 at the START of the truncated (common) period
        for (int i = 0; i < securities.size(); i++) {
            Security s = securities.get(i);
            List<Double> values = s.getValuesOverTime();
            
            // Start index in the original values list for the common period
            int startIdx = values.size() - minEntries;
            double baseValue = values.get(startIdx);
            
            List<Entry> entries = new ArrayList<>();
            for (int j = 0; j < minEntries; j++) {
                // Normalization: (current_price / start_of_common_period_price) * 100
                float normalizedValue = (float) ((values.get(startIdx + j) / baseValue) * 100.0);
                entries.add(new Entry(j, normalizedValue));
            }
            
            LineDataSet set = new LineDataSet(entries, s.getName());
            int color = colors[i % colors.length];
            set.setColor(Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)));
            set.setDrawCircles(false);
            set.setLineWidth(1.0f);
            set.setAxisDependency(YAxis.AxisDependency.RIGHT);
            dataSets.add(set);
        }

        // Total line - Absolute Portfolio Value based on quantities over the common period
        List<Entry> totalEntries = new ArrayList<>();
        for (int j = 0; j < minEntries; j++) {
            float sum = 0;
            for (int i = 0; i < securities.size(); i++) {
                Security s = securities.get(i);
                List<Double> values = s.getValuesOverTime();
                int startIdx = values.size() - minEntries;
                sum += (float) (values.get(startIdx + j) * quantities[i]);
            }
            totalEntries.add(new Entry(j, sum));
        }

        LineDataSet totalSet = new LineDataSet(totalEntries, "Total Portfolio Value");
        totalSet.setColor(Color.BLACK);
        totalSet.setLineWidth(3f);
        totalSet.setDrawCircles(false);
        totalSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSets.add(totalSet);

        LineData data = new LineData(dataSets);
        chart.setData(data);
        chart.invalidate();
    }

    private class DateFormatter extends ValueFormatter {
        private final SimpleDateFormat monthFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());

        @Override
        public String getFormattedValue(float value) {
            Calendar cal = Calendar.getInstance();
            // Today corresponds to the last index (currentMaxEntries - 1)
            int monthsBack = (currentMaxEntries - 1) - (int) value;
            cal.add(Calendar.MONTH, -monthsBack);
            Date date = cal.getTime();
            return monthFormat.format(date);
        }
    }
}
