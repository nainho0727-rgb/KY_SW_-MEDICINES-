package com.medicine.app.models;

import java.util.ArrayList;
import java.util.List;

public class Medicine {
    private String id;
    private String name;
    private String dose;
    private List<String> timeSlots; // morning, lunch, evening, bedtime
    private String memo;
    private String userId;
    private boolean taken;
    private long createdAt;

    public Medicine() {}

    // Copy constructor
    public Medicine(Medicine other) {
        this.id = other.id;
        this.name = other.name;
        this.dose = other.dose;
        this.timeSlots = new ArrayList<>(other.timeSlots);
        this.memo = other.memo;
        this.userId = other.userId;
        this.taken = other.taken;
        this.createdAt = other.createdAt;
    }

    public Medicine(String name, String dose, List<String> timeSlots, String memo, String userId) {
        this.name = name;
        this.dose = dose;
        this.timeSlots = timeSlots;
        this.memo = memo;
        this.userId = userId;
        this.taken = false;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }

    public List<String> getTimeSlots() { return timeSlots != null ? timeSlots : new ArrayList<>(); }
    public void setTimeSlots(List<String> timeSlots) { this.timeSlots = timeSlots; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isTaken() { return taken; }
    public void setTaken(boolean taken) { this.taken = taken; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getTimeSlotLabel() {
        if (timeSlots == null || timeSlots.isEmpty()) return "시간 미지정";
        StringBuilder label = new StringBuilder();
        for (String slot : timeSlots) {
            switch (slot) {
                case "morning": label.append("🌅 아침 "); break;
                case "lunch": label.append("☀️ 점심 "); break;
                case "evening": label.append("🌙 저녁 "); break;
                case "bedtime": label.append("🌛 취침 전 "); break;
            }
        }
        return label.toString().trim();
    }
}
