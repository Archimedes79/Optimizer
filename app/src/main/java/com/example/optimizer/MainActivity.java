package com.example.optimizer;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Portfolio portfolio;
    private PortfolioGraphView graphView;
    private ProgressBar pbSync;
    private TableLayout allocationTable;
    private YahooFinanceService yahooFinanceService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        portfolio = Portfolio.getInstance();
        portfolio.load(this);

        yahooFinanceService = new YahooFinanceService();

        graphView       = findViewById(R.id.portfolioGraph);
        pbSync          = findViewById(R.id.pbSync);
        allocationTable = findViewById(R.id.allocationTable);

        refreshUI();

        findViewById(R.id.btnAddRemove).setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageSecuritiesActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnSync).setOnClickListener(v -> syncPortfolio());

        findViewById(R.id.btnOptimize).setOnClickListener(v -> {
            Intent intent = new Intent(this, OptimizeActivity.class);
            startActivity(intent);
        });
    }

    /** Refreshes the graph and the allocation table. */
    private void refreshUI() {
        List<Security> securities = portfolio.getSecurities();
        if (graphView != null) {
            graphView.setSecurities(securities);
        }
        populateAllocationTable(securities);
    }

    /**
     * Builds the allocation table showing each security's name, units and
     * portfolio weight.  Column 0 (name) stretches to fill; units and pct
     * are right-aligned with fixed-width columns.
     */
    private void populateAllocationTable(List<Security> securities) {
        if (allocationTable == null) return;
        allocationTable.removeAllViews();

        if (securities == null || securities.isEmpty()) return;

        int textColor = getColor(R.color.textPrimary);
        int hintColor = getColor(R.color.textSecondary);
        float textSizeSp = 11f;

        // --- compute total portfolio value ---
        float totalValue = 0f;
        for (Security s : securities) {
            float[] vals = s.getValuesOverTime();
            float price = (vals != null && vals.length > 0) ? vals[vals.length - 1] : 0f;
            totalValue += price * (float) s.getQuantity();
        }

        // --- header row ---
        TableRow header = new TableRow(this);
        header.setPadding(0, 0, 0, dpToPx(2));
        header.addView(makeText("Name", hintColor, textSizeSp, Gravity.START, true));
        header.addView(makeText("Units", hintColor, textSizeSp, Gravity.END, true));
        header.addView(makeText("Pct", hintColor, textSizeSp, Gravity.END, true));
        allocationTable.addView(header);

        // --- compute per-security percentages and sort descending ---
        int n = securities.size();
        float[] pcts = new float[n];
        for (int i = 0; i < n; i++) {
            float[] vals = securities.get(i).getValuesOverTime();
            float price = (vals != null && vals.length > 0) ? vals[vals.length - 1] : 0f;
            float assetVal = price * (float) securities.get(i).getQuantity();
            pcts[i] = (totalValue > 0) ? (assetVal / totalValue) * 100f : 0f;
        }

        // Build index list sorted by pct descending
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) order.add(i);
        Collections.sort(order, (a, b) -> Float.compare(pcts[b], pcts[a]));

        // --- data rows (largest percentage first) ---
        for (int idx : order) {
            Security s = securities.get(idx);

            TableRow row = new TableRow(this);
            row.setPadding(0, dpToPx(1), 0, dpToPx(1));

            // Name: stretches (column 0)
            row.addView(makeText(s.getDisplayName(), textColor, textSizeSp, Gravity.START, false));

            // Units: right-aligned
            String unitsStr = String.format(Locale.getDefault(), "%.2f", s.getQuantity());
            row.addView(makeText(unitsStr, textColor, textSizeSp, Gravity.END, false));

            // Pct: right-aligned
            String pctStr = String.format(Locale.getDefault(), "%.1f%%", pcts[idx]);
            row.addView(makeText(pctStr, textColor, textSizeSp, Gravity.END, false));

            allocationTable.addView(row);
        }
    }

    /** Creates a styled TextView for table cells. */
    private TextView makeText(String text, int color, float sizeSp, int gravity, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setGravity(gravity);
        tv.setSingleLine(true);
        tv.setPadding(dpToPx(4), dpToPx(1), dpToPx(4), dpToPx(1));
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void syncPortfolio() {
        if (portfolio.getSecurities().isEmpty()) {
            Toast.makeText(this, "No assets to sync", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pbSync != null) pbSync.setVisibility(View.VISIBLE);
        findViewById(R.id.btnSync).setEnabled(false);

        yahooFinanceService.syncPortfolio(portfolio, new YahooFinanceService.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    if (pbSync != null) pbSync.setVisibility(View.GONE);
                    findViewById(R.id.btnSync).setEnabled(true);
                    portfolio.save(MainActivity.this);
                    refreshUI();
                    Toast.makeText(MainActivity.this, "Sync complete", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    if (pbSync != null) pbSync.setVisibility(View.GONE);
                    findViewById(R.id.btnSync).setEnabled(true);
                    showErrorDialog("Sync Failed", errorMessage);
                });
            }
        });
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (portfolio != null) {
            refreshUI();
        }
    }
}
