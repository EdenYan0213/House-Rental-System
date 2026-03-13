package com.house.service;

import com.house.dto.BatchCreateResult;
import com.house.dto.BatchRoomRequest;
import com.house.dto.ConflictInfo;
import com.house.model.Room;
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
                    .status(req.getStatus() == null ? "空闲" : req.getStatus())
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
                existing.setStatus(req.getStatus() == null ? "空闲" : req.getStatus());
                created.add(roomRepository.save(existing));
                continue;
            }
            Room r = Room.builder()
                    .buildingId(req.getBuildingId())
                    .floor(req.getFloor())
                    .roomNumber(roomNumber)
                    .layout(req.getLayout())
                    .area(req.getArea())
                    .status(req.getStatus() == null ? "空闲" : req.getStatus())
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
        if (buildingId == null && floor == null && status == null)
            return roomRepository.findAll();
        if (floor == null && status == null)
            return roomRepository.findByBuildingId(buildingId);
        if (status == null)
            return roomRepository.findByBuildingIdAndFloor(buildingId, floor);
        return roomRepository.findByBuildingIdAndFloorAndStatus(buildingId, floor, status);
    }

    public Room update(Room room) {
        return roomRepository.save(room);
    }

    public void delete(Long id) {
        roomRepository.deleteById(id);
    }
}
