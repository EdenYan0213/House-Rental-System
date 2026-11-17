package com.house.service;

import com.house.dto.BatchCreateResult;
import com.house.dto.BatchRoomRequest;
import com.house.dto.ConflictInfo;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.repo.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RoomService {
    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public Room create(Room room) {
        room.setStatus(RoomStatus.normalize(room.getStatus()));
        return roomRepository.save(room);
    }

    public BatchCreateResult createBatch(BatchRoomRequest req) {
        BatchCreateResult result = new BatchCreateResult();
        List<Room> created = new ArrayList<>();
        List<ConflictInfo> conflicts = new ArrayList<>();

        if (req.getStartNumber() == null || req.getEndNumber() == null) {
            result.setCreated(created);
            result.setConflicts(conflicts);
            result.setSuccessCount(0);
            result.setConflictCount(0);
            return result;
        }
        int start = req.getStartNumber();
        int end = req.getEndNumber();
        if (end < start) {
            result.setCreated(created);
            result.setConflicts(conflicts);
            result.setSuccessCount(0);
            result.setConflictCount(0);
            return result;
        }

        for (int i = start; i <= end; i++) {
            String roomNumber = String.valueOf(i);
            Optional<Room> exist = roomRepository.findByBuildingIdAndRoomNumber(req.getBuildingId(), roomNumber);
            if (exist.isPresent()) {
                Room existing = exist.get();
                conflicts.add(new ConflictInfo(
                        roomNumber,
                        existing.getStatus(),
                        existing.getLayout(),
                        existing.getArea(),
                        existing.getId()));
                continue;
            }
            Room r = Room.builder()
                    .buildingId(req.getBuildingId())
                    .floor(req.getFloor())
                    .roomNumber(roomNumber)
                    .layout(req.getLayout())
                    .area(req.getArea())
                    .status(RoomStatus.normalize(req.getStatus()))
                    .build();
            created.add(roomRepository.save(r));
        }

        result.setCreated(created);
        result.setConflicts(conflicts);
        result.setSuccessCount(created.size());
        result.setConflictCount(conflicts.size());
        return result;
    }

    public BatchCreateResult createBatchWithOverride(BatchRoomRequest req) {
        BatchCreateResult result = new BatchCreateResult();
        List<Room> created = new ArrayList<>();
        List<ConflictInfo> conflicts = new ArrayList<>();

        if (req.getStartNumber() == null || req.getEndNumber() == null) {
            result.setCreated(created);
            result.setConflicts(conflicts);
            result.setSuccessCount(0);
            result.setConflictCount(0);
            return result;
        }
        int start = req.getStartNumber();
        int end = req.getEndNumber();
        if (end < start) {
            result.setCreated(created);
            result.setConflicts(conflicts);
            result.setSuccessCount(0);
            result.setConflictCount(0);
            return result;
        }

        for (int i = start; i <= end; i++) {
            String roomNumber = String.valueOf(i);
            Optional<Room> exist = roomRepository.findByBuildingIdAndRoomNumber(req.getBuildingId(), roomNumber);
            if (exist.isPresent()) {
                Room existing = exist.get();
                existing.setLayout(req.getLayout());
                existing.setArea(req.getArea());
                existing.setFloor(req.getFloor());
                existing.setStatus(RoomStatus.normalize(req.getStatus()));
                created.add(roomRepository.save(existing));
                continue;
            }
            Room r = Room.builder()
                    .buildingId(req.getBuildingId())
                    .floor(req.getFloor())
                    .roomNumber(roomNumber)
                    .layout(req.getLayout())
                    .area(req.getArea())
                    .status(RoomStatus.normalize(req.getStatus()))
                    .build();
            created.add(roomRepository.save(r));
        }

        result.setCreated(created);
        result.setConflicts(conflicts);
        result.setSuccessCount(created.size());
        result.setConflictCount(conflicts.size());
        return result;
    }

    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id);
    }

    public List<Room> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return roomRepository.findByIdIn(ids);
    }

    public List<Room> listByBuildingAndFloor(Long buildingId, Integer floor) {
        return listByBuildingAndFloor(buildingId, floor, null);
    }

    public List<Room> listByBuildingAndFloor(Long buildingId, Integer floor, String status) {
        List<Room> rooms;
        if (buildingId == null) {
            // 未指定楼栋时忽略楼层，避免查 building_id = null 的无效条件
            rooms = roomRepository.findAll();
        } else if (floor == null) {
            rooms = roomRepository.findByBuildingId(buildingId);
        } else {
            rooms = roomRepository.findByBuildingIdAndFloor(buildingId, floor);
        }
        // 状态筛选在内存中归一化后再比较，兼容历史数据的中英文混用
        if (status != null && !status.isBlank()) {
            rooms = rooms.stream().filter(r -> RoomStatus.matches(r.getStatus(), status)).toList();
        }
        // 输出统一为规范中文，保证前端标签与筛选下拉能正确命中
        rooms.forEach(r -> r.setStatus(RoomStatus.normalize(r.getStatus())));
        return rooms;
    }

    public Room update(Room room) {
        room.setStatus(RoomStatus.normalize(room.getStatus()));
        return roomRepository.save(room);
    }

    public void delete(Long id) {
        roomRepository.deleteById(id);
    }
}
