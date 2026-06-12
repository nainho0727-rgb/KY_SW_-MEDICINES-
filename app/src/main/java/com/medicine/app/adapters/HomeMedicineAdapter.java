package com.medicine.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicine.app.R;
import com.medicine.app.models.Medicine;

import java.util.List;

public class HomeMedicineAdapter extends RecyclerView.Adapter<HomeMedicineAdapter.ViewHolder> {

    public interface OnTakeListener {
        void onTake(Medicine medicine, int position);
    }

    private List<Medicine> medicineList;
    private OnTakeListener listener;

    public HomeMedicineAdapter(List<Medicine> medicineList, OnTakeListener listener) {
        this.medicineList = medicineList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_medicine_home, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Medicine medicine = medicineList.get(position);
        holder.tvMedicineName.setText(medicine.getName());
        holder.tvMedicineTime.setText(medicine.getTimeSlotLabel());
        holder.cbTaken.setChecked(medicine.isTaken());

        if (medicine.isTaken()) {
            holder.btnTake.setText("완료");
            holder.btnTake.setEnabled(false);
            holder.btnTake.setAlpha(0.5f);
        } else {
            holder.btnTake.setText("복용");
            holder.btnTake.setEnabled(true);
            holder.btnTake.setAlpha(1.0f);
        }

        holder.btnTake.setOnClickListener(v -> {
            if (listener != null && !medicine.isTaken()) {
                listener.onTake(medicine, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return medicineList.size();
    }

    public void updateList(List<Medicine> newList) {
        this.medicineList = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbTaken;
        TextView tvMedicineName, tvMedicineTime;
        Button btnTake;

        ViewHolder(View itemView) {
            super(itemView);
            cbTaken = itemView.findViewById(R.id.cb_taken);
            tvMedicineName = itemView.findViewById(R.id.tv_medicine_name);
            tvMedicineTime = itemView.findViewById(R.id.tv_medicine_time);
            btnTake = itemView.findViewById(R.id.btn_take);
        }
    }
}
