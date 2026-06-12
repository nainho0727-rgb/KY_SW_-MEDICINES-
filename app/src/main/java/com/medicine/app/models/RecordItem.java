package com.medicine.app.models;

public class RecordItem {
    private String medicineName;
    private String timeLabel;
    private String status;

    public RecordItem(String medicineName, String timeLabel, String status) {
        this.medicineName = medicineName;
        this.timeLabel = timeLabel;
        this.status = status;
    }

    public String getMedicineName() { return medicineName; }
    public String getTimeLabel() { return timeLabel; }
    public String getStatus() { return status; }
}
