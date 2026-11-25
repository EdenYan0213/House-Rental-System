package com.house.service;

import com.house.dto.BatchCreateResult;
import com.house.dto.BatchRoomRequest;
import com.house.dto.ConflictInfo;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.repo.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final ImageStorageService imageStorageService;

    public RoomService(RoomRepository roomRepository, ImageStorageService imageStorageService) {
        this.roomRepository = roomRepository;
        this.imageStorageService = imageStorageService;
    }

    /**
     * 把房间引用的图片标记为已引用。
     *
     * <p>图片上传时 refCount=0，孤儿清理任务会删除所有 refCount=0 的图片。
     * 如果不在这里标记，用户上传的房间照片会在 1 天后被清掉，
     * 业务表里的 image_ids 变成悬空引用（历史 bug）。
     */
    private void markImagesReferenced(Room room) {
        if (room == null) {
            return;
        }
        try {
            List<Long> ids = room.getImageIdList();
            if (ids != null && !ids.isEmpty()) {
                imageStorageService.markReferenced(ids);
            }
        } catch (Exception e) {
            log.warn("标记房间图片引用失败 roomId={}: {}", room.getId(), e.getMessage());
        }
    }

    @Transactional
    public Room create(Room room) {
        room.setStatus(RoomStatus.normalize(room.getStatus()));
        Room saved = roomRepository.save(room);
        markImagesReferenced(saved);
        return saved;
    }

    @Transactional
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

    @Transactional
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

    /**
     * 更新房间。
     *
     * <p>请求体通常是前端表单的部分字段，直接 save() 会把未传的字段
     * （如 deleted）覆盖成 null，触发非空约束。这里先读原记录再逐字段合并。
     */
    @Transactional
    public Room update(Room room) {
        Room existing = room.getId() == null ? null
            : roomRepository.findById(room.getId()).orElse(null);
        if (existing == null) {
            room.setStatus(RoomStatus.normalize(room.getStatus()));
            if (room.getDeleted() == null) {
                room.setDeleted(0);
            }
            return roomRepository.save(room);
        }

        if (room.getBuildingId() != null) existing.setBuildingId(room.getBuildingId());
        if (room.getFloor() != null) existing.setFloor(room.getFloor());
        if (room.getRoomNumber() != null) existing.setRoomNumber(room.getRoomNumber());
        if (room.getLayout() != null) existing.setLayout(room.getLayout());
        if (room.getArea() != null) existing.setArea(room.getArea());
        if (room.getStatus() != null) existing.setStatus(RoomStatus.normalize(room.getStatus()));
        if (room.getImageIds() != null) existing.setImageIds(room.getImageIds());
        if (room.getWaterMeterId() != null) existing.setWaterMeterId(room.getWaterMeterId());
        if (room.getElectricMeterId() != null) existing.setElectricMeterId(room.getElectricMeterId());

        if (existing.getDeleted() == null) {
            existing.setDeleted(0);
        }
        Room saved = roomRepository.save(existing);
        markImagesReferenced(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        roomRepository.deleteById(id);
    }
}
