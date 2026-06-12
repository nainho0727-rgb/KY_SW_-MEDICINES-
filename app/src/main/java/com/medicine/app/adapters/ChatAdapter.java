package com.medicine.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.medicine.app.R;
import com.medicine.app.models.ChatMessage;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<ChatMessage> chatList;

    public ChatAdapter(List<ChatMessage> chatList) {
        this.chatList = chatList;
    }

    @Override
    public int getItemViewType(int position) {
        return chatList.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_AI) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
            return new AIViewHolder(view);
        } else if (viewType == ChatMessage.TYPE_LOADING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
            return new LoadingViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = chatList.get(position);
        if (holder instanceof AIViewHolder) {
            ((AIViewHolder) holder).tvMessage.setText(message.getMessage());
            ((AIViewHolder) holder).tvTime.setText(message.getTime());
        } else if (holder instanceof LoadingViewHolder) {
            ((LoadingViewHolder) holder).tvMessage.setText("생각 중...");
            ((LoadingViewHolder) holder).tvTime.setText("");
        } else {
            ((UserViewHolder) holder).tvMessage.setText(message.getMessage());
            ((UserViewHolder) holder).tvTime.setText(message.getTime());
        }
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        AIViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        UserViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        LoadingViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}
