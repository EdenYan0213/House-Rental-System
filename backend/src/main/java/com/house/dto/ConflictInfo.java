package com.house.dto;

import lombok.Data;

@Data
public class ConflictInfo {
    private String roomNumber;
    private String currentStatus;
    private String layout;
    private Double area;
    private Long existingId;

    public ConflictInfo(String roomNumber, String status, String layout, Double area, Long existingId) {
        this.roomNumber = roomNumber;
        this.currentStatus = status;
        this.layout = layout;
        this.area = area;
        this.existingId = existingId;
    }
}
