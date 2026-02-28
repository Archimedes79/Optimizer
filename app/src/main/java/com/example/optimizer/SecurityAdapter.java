package com.example.optimizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SecurityAdapter extends RecyclerView.Adapter<SecurityAdapter.ViewHolder> {

    private List<Security> securities;

    public SecurityAdapter(List<Security> securities) {
        this.securities = securities;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_security, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Security security = securities.get(position);
        holder.tvSecurityName.setText(security.getName());
        holder.tvIdentifier.setText(security.getIdentifier());
        holder.tvEntries.setText("Entries: " + security.getNumberOfEntries());
        holder.tvValues.setText("Values: " + security.getValuesOverTime().toString());
    }

    @Override
    public int getItemCount() {
        return securities.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvSecurityName;
        public TextView tvIdentifier;
        public TextView tvEntries;
        public TextView tvValues;

        public ViewHolder(View view) {
            super(view);
            tvSecurityName = view.findViewById(R.id.tvSecurityName);
            tvIdentifier = view.findViewById(R.id.tvIdentifier);
            tvEntries = view.findViewById(R.id.tvEntries);
            tvValues = view.findViewById(R.id.tvValues);
        }
    }
}
