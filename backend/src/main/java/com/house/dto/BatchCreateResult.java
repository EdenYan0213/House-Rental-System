package com.house.dto;

import com.house.model.Room;
import lombok.Data;

import java.util.List;

@Data
public class BatchCreateResult {
    private List<Room> created;
    private List<ConflictInfo> conflicts;
    private int successCount;
    private int conflictCount;
}
