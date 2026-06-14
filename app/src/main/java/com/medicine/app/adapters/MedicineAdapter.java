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
    /** 펼쳐진(메모 전체 표시) 약의 키 목록. */
    private final java.util.Set<String> expandedKeys = new java.util.HashSet<>();
    private RecyclerView attachedRv;

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedRv = recyclerView;
    }

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

        // 메모(복용법/주의사항) 표시 + 카드를 누르면 펼치기/접기
        String memo = medicine.getMemo();
        if (memo != null && !memo.trim().isEmpty()) {
            final String key = keyOf(medicine);
            boolean expanded = expandedKeys.contains(key);
            holder.tvMemo.setText(formatMemo(memo.trim()));
            holder.tvMemo.setVisibility(View.VISIBLE);
            holder.tvMemo.setMaxLines(expanded ? Integer.MAX_VALUE : 3);
            holder.tvMemo.setEllipsize(expanded ? null : android.text.TextUtils.TruncateAt.END);

            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v -> {
                if (expandedKeys.contains(key)) expandedKeys.remove(key);
                else expandedKeys.add(key);
                // 전체 재바인딩 + RecyclerView 높이 재측정(중첩 스크롤뷰에서 다음 섹션과 겹치는 것 방지)
                notifyDataSetChanged();
                if (attachedRv != null) attachedRv.post(attachedRv::requestLayout);
            });
        } else {
            holder.tvMemo.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        }

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

    /** 펼침 상태를 약별로 기억하기 위한 키(가능하면 문서 id, 없으면 이름+메모). */
    private String keyOf(Medicine m) {
        if (m.getId() != null && !m.getId().isEmpty()) return m.getId();
        return (m.getName() == null ? "" : m.getName()) + "|" + (m.getMemo() == null ? "" : m.getMemo());
    }

    /** 메모를 줄별로 구조화: 1줄(분류·복용법)은 진하게, '주의/공통 주의' 라벨은 색+굵게. */
    private CharSequence formatMemo(String memo) {
        String[] lines = memo.split("\n");
        StringBuilder html = new StringBuilder();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (html.length() > 0) html.append("<br>");
            if (line.startsWith("공통 주의:")) {
                html.append("<font color='#F59E0B'><b>⚠ 공통 주의</b></font>  ")
                    .append(esc(line.substring("공통 주의:".length()).trim()));
            } else if (line.startsWith("주의:")) {
                html.append("<font color='#F59E0B'><b>⚠ 주의</b></font>  ")
                    .append(esc(line.substring("주의:".length()).trim()));
            } else {
                html.append("<font color='#121212'><b>").append(esc(line)).append("</b></font>");
            }
        }
        return androidx.core.text.HtmlCompat.fromHtml(
                html.toString(), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY);
    }

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDose, tvMemo;
        ImageButton btnDelete;
        View viewStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDose = itemView.findViewById(R.id.tv_dose);
            tvMemo = itemView.findViewById(R.id.tv_memo);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            viewStatus = itemView.findViewById(R.id.view_status);
        }
    }
}
