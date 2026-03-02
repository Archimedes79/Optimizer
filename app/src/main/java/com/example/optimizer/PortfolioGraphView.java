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
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom chart widget that shows normalised security curves + a portfolio index.
 *
 * <p>Zoom is controlled by horizontal drag (right = zoom out, left = zoom in).
 * Uses the common-range indices from {@link Security#getStartIndex()} /
 * {@link Security#getEndIndex()} to guarantee all securities are aligned.</p>
 */
public class PortfolioGraphView extends FrameLayout {

    private LineChart chart;
    private int currentMaxEntries = 240;
    private float currentVisibleCount = 240;
    private PortfolioMarkerView markerView;
    private OnVisibleRangeChangeListener visibleRangeChangeListener;
    private List<Security> currentSecurities;

    /** Callback fired when the user changes the zoom level. */
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
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setAutoScaleMinMaxEnabled(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(0xFF5A6478);    // textSecondary
        xAxis.setTextSize(10f);
        xAxis.setValueFormatter(new DynamicDateFormatter());

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0xFFE2E8F0);  // divider
        leftAxis.setTextColor(0xFF5A6478);   // textSecondary
        leftAxis.setTextSize(10f);
        chart.getAxisRight().setEnabled(false);

        markerView = new PortfolioMarkerView(context, R.layout.graph_marker);
        chart.setMarker(markerView);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setWordWrapEnabled(true);
        legend.setTextColor(0xFF1A2138);   // textPrimary
        legend.setTextSize(11f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);

        // Horizontal-drag zoom
        chart.setOnTouchListener(new OnTouchListener() {
            private float startX;
            private float startVisibleCount;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startVisibleCount = currentVisibleCount;
                        chart.onTouchEvent(event);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - startX;
                        float sensitivity = (float) currentMaxEntries / chart.getWidth();
                        currentVisibleCount = startVisibleCount + dx * sensitivity;
                        if (currentVisibleCount < 5) currentVisibleCount = 5;
                        if (currentVisibleCount > currentMaxEntries) currentVisibleCount = currentMaxEntries;
                        applyZoom(true);
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

    /** Applies the current zoom level and optionally notifies listeners. */
    private void applyZoom(boolean notifyListener) {
        if (chart.getData() == null) return;
        chart.setVisibleXRangeMaximum(currentVisibleCount);
        chart.setVisibleXRangeMinimum(currentVisibleCount);
        chart.moveViewToX(currentMaxEntries - currentVisibleCount);
        chart.invalidate();
        if (notifyListener && visibleRangeChangeListener != null) {
            visibleRangeChangeListener.onVisibleRangeChanged(currentVisibleCount);
        }
    }

    /** Convenience: displays securities with their own quantities. */
    public void setSecurities(List<Security> securities) {
        if (securities == null) return;
        double[] quantities = new double[securities.size()];
        for (int i = 0; i < securities.size(); i++) {
            quantities[i] = securities.get(i).getQuantity();
        }
        setSecuritiesWithQuantities(securities, quantities);
    }

    /**
     * Main entry: builds individual + portfolio-index line data sets.
     *
     * <p>Uses the common-range length ({@link Security#getCommonRangeLength()})
     * to align all securities and reads directly from the float[] arrays
     * using startIndex/endIndex.</p>
     */
    public void setSecuritiesWithQuantities(List<Security> securities, double[] quantities) {
        this.currentSecurities = securities;
        if (securities == null || securities.isEmpty() || quantities == null || quantities.length != securities.size()) {
            chart.clear();
            return;
        }

        // Use the common range that Portfolio has already computed
        int commonLen = securities.get(0).getCommonRangeLength();
        for (Security s : securities) {
            commonLen = Math.min(commonLen, s.getCommonRangeLength());
        }
        if (commonLen < 2) {
            chart.clear();
            return;
        }

        int previousMax = this.currentMaxEntries;
        this.currentMaxEntries = commonLen;

        // maintain zoom ratio when data range changes
        if (previousMax != commonLen) {
            float ratio = currentVisibleCount / previousMax;
            currentVisibleCount = commonLen * ratio;
        }
        if (currentVisibleCount > commonLen) currentVisibleCount = commonLen;
        if (currentVisibleCount < 5) currentVisibleCount = 5;

        if (markerView != null) {
            Security first = securities.get(0);
            markerView.setDateSource(first.getDates(), first.getNumberOfEntries() - commonLen);
        }

        // --- last total value (for normalisation of portfolio index) ---
        float lastTotal = 0f;
        for (int i = 0; i < securities.size(); i++) {
            float[] vals = securities.get(i).getValuesOverTime();
            if (vals != null && vals.length > 0) {
                lastTotal += vals[vals.length - 1] * (float) quantities[i];
            }
        }

        List<ILineDataSet> dataSets = new ArrayList<>();

        // --- per-security lines (normalised to 100 via getNormalizedValues) ---
        for (int i = 0; i < securities.size(); i++) {
            Security s = securities.get(i);
            float[] norm = s.getNormalizedValues();
            if (norm.length == 0) continue;

            List<Entry> entries = new ArrayList<>(norm.length);
            for (int j = 0; j < norm.length; j++) {
                entries.add(new Entry(j, norm[j]));
            }

            LineDataSet set = new LineDataSet(entries, s.getDisplayName());
            int base = s.getColor();
            set.setColor(Color.argb(160, Color.red(base), Color.green(base), Color.blue(base)));
            set.setDrawCircles(false);
            set.setLineWidth(1.5f);
            dataSets.add(set);
        }

        // --- portfolio index line (weighted sum, normalised to 100) ---
        List<Entry> totalEntries = new ArrayList<>(commonLen);
        if (lastTotal > 0f) {
            float invLastTotal = 100.0f / lastTotal;
            for (int j = 0; j < commonLen; j++) {
                float sum = 0f;
                for (int i = 0; i < securities.size(); i++) {
                    Security s = securities.get(i);
                    float[] vals = s.getValuesOverTime();
                    if (vals == null) continue;
                    int si = s.getEndIndex() - commonLen + 1;
                    if (si < 0) si = 0;
                    if (si + j < vals.length) {
                        sum += vals[si + j] * (float) quantities[i];
                    }
                }
                totalEntries.add(new Entry(j, sum * invLastTotal));
            }
        }

        LineDataSet totalSet = new LineDataSet(totalEntries, "Portfolio Index");
        totalSet.setColor(0xFF1A2138);  // textPrimary
        totalSet.setLineWidth(2.5f);
        totalSet.setDrawCircles(false);
        dataSets.add(totalSet);

        chart.setData(new LineData(dataSets));

        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(0);
        xAxis.setAxisMaximum(commonLen - 1);

        applyZoom(false); // don't notify during data load
    }

    /**
     * Dynamic X-axis formatter – adjusts granularity based on zoom level.
     * Uses Security's on-demand date helpers instead of building a List every call.
     */
    private class DynamicDateFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            int index = (int) value;
            if (currentSecurities == null || currentSecurities.isEmpty()) return "";

            Security first = currentSecurities.get(0);
            int offset = first.getNumberOfEntries() - currentMaxEntries;
            int dateIndex = offset + index;
            if (dateIndex < 0 || dateIndex >= first.getNumberOfEntries()) return "";

            if (currentVisibleCount < 12) {
                return first.getDayString(dateIndex) + " " + first.getMonthString(dateIndex);
            } else if (currentVisibleCount < 48) {
                return first.getMonthString(dateIndex) + " '" + first.getYearString(dateIndex);
            } else {
                return "'" + first.getYearString(dateIndex);
            }
        }
    }
}
