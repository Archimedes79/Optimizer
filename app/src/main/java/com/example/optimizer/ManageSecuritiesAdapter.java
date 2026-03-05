package com.example.optimizer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * RecyclerView adapter for the Manage Assets list.
 *
 * <p>Each item is rendered as a single {@link TextView} with a
 * {@link SpannableStringBuilder}: the first line uses the same caption style
 * as the screen titles (20 sp, sans-serif-medium, bold, textPrimary) and the
 * remaining lines show ID, quantity and flags in the secondary detail style
 * (11 sp, textSecondary) – matching the allocation tables in the other screens.</p>
 */
public class ManageSecuritiesAdapter extends RecyclerView.Adapter<ManageSecuritiesAdapter.ViewHolder> {

    private final List<Security> securities;
    private final OnSecurityActionListener listener;

    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // ms

    public interface OnSecurityActionListener {
        void onSecurityRemoved(Security security);
        void onSecurityClicked(Security security);
        void onSecurityColorChanged(Security security);
    }

    public ManageSecuritiesAdapter(List<Security> securities, OnSecurityActionListener listener) {
        this.securities = securities;
        this.listener = listener;
    }

    // ── ViewHolder ──────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvContent;
        final ImageButton btnRemove;
        final View viewColor;

        public ViewHolder(@NonNull View view) {
            super(view);
            tvContent = view.findViewById(R.id.tvSecurityContent);
            btnRemove = view.findViewById(R.id.btnRemoveSecurity);
            viewColor = view.findViewById(R.id.viewSecurityColor);
        }
    }

    // ── Adapter callbacks ───────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_security_manage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Security security = securities.get(position);

        // Determine whether this security limits the common date range
        int minCount = Integer.MAX_VALUE;
        for (Security s : securities) {
            minCount = Math.min(minCount, s.getNumberOfEntries());
        }
        boolean isLimiting = securities.size() > 1
                && security.getNumberOfEntries() == minCount;

        // Build the formatted text and set it
        holder.tvContent.setText(buildContent(holder, security, isLimiting));
        holder.viewColor.setBackgroundColor(security.getColor());

        // Remove button
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onSecurityRemoved(security);
        });

        // Single-click → edit; double-click → random colour
        holder.itemView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                int newColor = Color.rgb(
                        new Random().nextInt(256),
                        new Random().nextInt(256),
                        new Random().nextInt(256));
                security.setColor(newColor);
                notifyItemChanged(position);
                if (listener != null) listener.onSecurityColorChanged(security);
            } else {
                if (listener != null) listener.onSecurityClicked(security);
            }
            lastClickTime = now;
        });
    }

    @Override
    public int getItemCount() {
        return securities.size();
    }

    // ── Text builder ────────────────────────────────────────────────────────

    /**
     * Builds a single SpannableStringBuilder for each list item.
     *
     * <p>Line 1 (caption): security display name – same style as screen
     * captions (20 sp, bold, sans-serif-medium, textPrimary).<br>
     * Lines 2+: ID, quantity, fixed flag, limiting marker – 11 sp,
     * textSecondary, matching the allocation tables.</p>
     */
    private CharSequence buildContent(@NonNull ViewHolder holder,
                                      @NonNull Security security,
                                      boolean isLimiting) {

        float density = holder.itemView.getResources().getDisplayMetrics().scaledDensity;
        int captionPx = Math.round(14f * density);   // caption size matching screen titles
        int detailPx  = Math.round(11f * density);   // detail size matching table text

        int colorPrimary   = holder.itemView.getContext().getColor(R.color.textPrimary);
        int colorSecondary = holder.itemView.getContext().getColor(R.color.textSecondary);

        // ── caption line ────────────────────────────────────────────────
        String caption = security.getDisplayName();

        // ── detail lines ────────────────────────────────────────────────
        StringBuilder detail = new StringBuilder();
        String alias = security.getAlias();
        detail.append("Alias: ").append((alias != null && !alias.isEmpty()) ? alias : "–");
        detail.append('\n');
        detail.append("ID: ").append(security.getSymbol() != null ? security.getSymbol() : "–");
        detail.append('\n');
        detail.append(String.format(Locale.getDefault(), "Qty: %.4f", security.getQuantity()));
        if (security.isFixed()) {
            detail.append("  ·  Fixed");
        }
        if (isLimiting) {
            detail.append("  ·  Limiting");
        }

        // ── assemble spannable ──────────────────────────────────────────
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(caption);
        int captionEnd = ssb.length();
        ssb.append('\n');
        int detailStart = ssb.length();
        ssb.append(detail);

        // Caption: bold, larger, primary colour
        ssb.setSpan(new StyleSpan(Typeface.BOLD),
                0, captionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new AbsoluteSizeSpan(captionPx),
                0, captionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(colorPrimary),
                0, captionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Details: normal weight, smaller, secondary colour
        ssb.setSpan(new AbsoluteSizeSpan(detailPx),
                detailStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(colorSecondary),
                detailStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }
}
