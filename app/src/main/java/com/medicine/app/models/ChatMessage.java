package com.medicine.app.models;

public class ChatMessage {
    public static final int TYPE_AI = 0;
    public static final int TYPE_USER = 1;
    public static final int TYPE_LOADING = 2;

    private String message;
    private int type;
    private String time;
    private boolean isLoading = false;

    public ChatMessage(String message, int type, String time) {
        this.message = message;
        this.type = type;
        this.time = time;
    }

    public String getMessage() { return message; }
    public int getType() { return type; }
    public String getTime() { return time; }
    public boolean isLoading() { return isLoading; }
    public void setLoading(boolean loading) { isLoading = loading; }
    public void setMessage(String message) { this.message = message; }
}
