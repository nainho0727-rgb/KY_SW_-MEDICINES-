package com.medicine.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicine.app.R;
import com.medicine.app.models.RecordItem;

import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {

    private List<RecordItem> recordList;

    public RecordAdapter(List<RecordItem> recordList) {
        this.recordList = recordList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecordItem item = recordList.get(position);
        holder.tvMedicineName.setText(item.getMedicineName());
        holder.tvTime.setText(item.getTimeLabel());
        holder.tvStatus.setText(item.getStatus());

        if ("복용 완료".equals(item.getStatus())) {
            holder.viewStatusDot.setBackgroundResource(R.drawable.bg_circle_primary);
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.success));
        } else {
            holder.viewStatusDot.setBackgroundResource(R.drawable.bg_circle_light);
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
        }
    }

    @Override
    public int getItemCount() {
        return recordList.size();
    }

    public void updateList(List<RecordItem> newList) {
        this.recordList = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View viewStatusDot;
        TextView tvMedicineName, tvTime, tvStatus;

        ViewHolder(View itemView) {
            super(itemView);
            viewStatusDot = itemView.findViewById(R.id.view_status_dot);
            tvMedicineName = itemView.findViewById(R.id.tv_medicine_name);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}
