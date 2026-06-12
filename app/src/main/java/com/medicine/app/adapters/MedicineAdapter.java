package com.medicine.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicine.app.R;
import com.medicine.app.models.Medicine;

import java.util.List;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.ViewHolder> {

    public interface OnMedicineActionListener {
        void onDelete(Medicine medicine, int position);
    }

    private List<Medicine> medicineList;
    private OnMedicineActionListener listener;

    public MedicineAdapter(List<Medicine> medicineList, OnMedicineActionListener listener) {
        this.medicineList = medicineList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_medicine, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Medicine medicine = medicineList.get(position);
        holder.tvName.setText(medicine.getName());
        holder.tvDose.setText(medicine.getDose() != null ? medicine.getDose() : "");

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(medicine, holder.getAdapterPosition());
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
        TextView tvName, tvDose;
        ImageButton btnDelete;
        View viewStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDose = itemView.findViewById(R.id.tv_dose);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            viewStatus = itemView.findViewById(R.id.view_status);
        }
    }
}
