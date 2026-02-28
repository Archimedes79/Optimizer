package com.example.optimizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ManageSecuritiesAdapter extends RecyclerView.Adapter<ManageSecuritiesAdapter.ViewHolder> {

    private List<Security> securities;
    private OnSecurityActionListener listener;

    public interface OnSecurityActionListener {
        void onSecurityRemoved(Security security);
        void onSecurityClicked(Security security);
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
        holder.tvIdentifier.setText(security.getIdentifier());
        holder.tvQuantity.setText("Quantity: " + security.getQuantity());

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSecurityRemoved(security);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSecurityClicked(security);
            }
        });
    }

    @Override
    public int getItemCount() {
        return securities.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName;
        public TextView tvIdentifier;
        public TextView tvQuantity;
        public ImageButton btnRemove;

        public ViewHolder(View view) {
            super(view);
            tvName = view.findViewById(R.id.tvManageName);
            tvIdentifier = view.findViewById(R.id.tvManageIdentifier);
            tvQuantity = view.findViewById(R.id.tvManageQuantity);
            btnRemove = view.findViewById(R.id.btnRemoveSecurity);
        }
    }
}
