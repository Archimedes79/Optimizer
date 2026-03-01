package com.example.optimizer;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
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

public class ManageSecuritiesAdapter extends RecyclerView.Adapter<ManageSecuritiesAdapter.ViewHolder> {

    private List<Security> securities;
    private OnSecurityActionListener listener;
    
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds

    public interface OnSecurityActionListener {
        void onSecurityRemoved(Security security);
        void onSecurityClicked(Security security);
        void onSecurityColorChanged(Security security);
    }

    public ManageSecuritiesAdapter(List<Security> securities, OnSecurityActionListener listener) {
        this.securities = securities;
        this.listener = listener;
    }

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
        
        holder.tvName.setText(security.getName());
        
        String details = "ID: " + security.getIdentifier();
        holder.tvIdentifier.setText(details);

        if (security.getAlias() != null && !security.getAlias().isEmpty()) {
            holder.tvAlias.setText("Alias: " + security.getAlias());
            holder.tvAlias.setVisibility(View.VISIBLE);
        } else {
            holder.tvAlias.setVisibility(View.GONE);
        }

        holder.tvQuantity.setText(String.format(Locale.getDefault(), "Quantity: %.4f", security.getQuantity()));
        holder.viewColor.setBackgroundColor(security.getColor());
        
        holder.tvFixed.setVisibility(security.isFixed() ? View.VISIBLE : View.GONE);

        int minCount = Integer.MAX_VALUE;
        for (Security s : securities) {
            minCount = Math.min(minCount, s.getNumberOfEntries());
        }

        boolean isLimiting = (securities.size() > 1) && (security.getNumberOfEntries() == minCount);
        
        if (isLimiting) {
            holder.tvLimitingMarker.setVisibility(View.VISIBLE);
            holder.itemView.setBackgroundColor(Color.parseColor("#11FF0000"));
        } else {
            holder.tvLimitingMarker.setVisibility(View.GONE);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSecurityRemoved(security);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                // Double click detected
                Random random = new Random();
                int newColor = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
                security.setColor(newColor);
                notifyItemChanged(position);
                if (listener != null) {
                    listener.onSecurityColorChanged(security);
                }
            } else {
                // Single click
                if (listener != null) {
                    listener.onSecurityClicked(security);
                }
            }
            lastClickTime = clickTime;
        });
    }

    @Override
    public int getItemCount() {
        return securities.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName;
        public TextView tvAlias;
        public TextView tvIdentifier;
        public TextView tvQuantity;
        public TextView tvLimitingMarker;
        public TextView tvFixed;
        public ImageButton btnRemove;
        public View viewColor;

        public ViewHolder(View view) {
            super(view);
            tvName = view.findViewById(R.id.tvManageName);
            tvAlias = view.findViewById(R.id.tvManageAlias);
            tvIdentifier = view.findViewById(R.id.tvManageIdentifier);
            tvQuantity = view.findViewById(R.id.tvManageQuantity);
            tvLimitingMarker = view.findViewById(R.id.tvLimitingMarker);
            tvFixed = view.findViewById(R.id.tvManageFixed);
            btnRemove = view.findViewById(R.id.btnRemoveSecurity);
            viewColor = view.findViewById(R.id.viewSecurityColor);
        }
    }
}
