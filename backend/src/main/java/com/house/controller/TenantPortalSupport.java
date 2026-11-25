package com.house.controller;

import com.house.model.Building;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.BuildingRepository;
import com.house.repo.RoomRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 租客端控制器共享工具。
 *
 * <p>原本这些方法散落在 {@code TenantPortalController}（1344 行）里，
 * 被认证、消息、电子签署三个流程共用。拆分为多个控制器后，
 * 把它们收敛到这里，避免复制粘贴。
 *
 * <p>只放**无状态、无事务**的纯转换/格式化逻辑；需要读写数据库的业务
 * 仍然留在各自 controller 或 service 里。
 */
@Component
public class TenantPortalSupport {

  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;

  public TenantPortalSupport(RoomRepository roomRepository, BuildingRepository buildingRepository) {
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
  }

  /** 请求体取值：null 安全，并去掉首尾空白。 */
  public String text(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  /** Long 解析：接受 Number 和数字字符串，失败返回 null（不抛异常）。 */
  public Long parseLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(value.toString().trim());
    } catch (Exception e) {
      return null;
    }
  }

  /** 请求体布尔值：兼容 true/"1"/"yes"。 */
  public boolean boolValue(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    String s = text(value);
    return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
  }

  /** 数据库 Integer 布尔位：仅 1 为真。 */
  public boolean bool(Integer v) {
    return v != null && v == 1;
  }

  public String normalizePhone(String phone) {
    return phone == null ? "" : phone.trim();
  }

  /** 房间摘要（房间号、楼栋名、合并显示文本）。房间不存在时返回占位。 */
  public Map<String, Object> buildRoomInfo(Long roomId) {
    Room room = roomId == null ? null : roomRepository.findById(roomId).orElse(null);
    if (room == null) {
      return Map.of("roomId", roomId, "roomText", "-");
    }
    Building building = room.getBuildingId() == null ? null
        : buildingRepository.findById(room.getBuildingId()).orElse(null);
    String roomText = (building == null ? "" : building.getName())
        + (room.getRoomNumber() == null ? "" : room.getRoomNumber());
    return Map.of(
        "roomId", room.getId(),
        "roomNumber", room.getRoomNumber() == null ? "-" : room.getRoomNumber(),
        "buildingName", building == null ? "-" : building.getName(),
        "roomText", roomText.isBlank() ? "-" : roomText);
  }

  /** 手机号脱敏：138****8001。长度不足 7 位原样返回。 */
  public String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }

  /** 身份证脱敏：前 4 位 + **** + 后 4 位。 */
  public String maskIdCard(String idCard) {
    if (idCard == null || idCard.length() < 8) {
      return idCard == null ? "" : idCard;
    }
    return idCard.substring(0, 4) + "****" + idCard.substring(idCard.length() - 4);
  }

  /** 金额保留两位小数。 */
  public double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  /** 补齐租客通知偏好默认值（历史数据可能为 null）。 */
  public void normalizeTenantPreference(Tenant tenant) {
    if (tenant.getLanguagePreference() == null || tenant.getLanguagePreference().isBlank()) {
      tenant.setLanguagePreference("zh-CN");
    }
    if (tenant.getNotifyOverdue() == null) {
      tenant.setNotifyOverdue(1);
    }
    if (tenant.getNotifyRepair() == null) {
      tenant.setNotifyRepair(1);
    }
    if (tenant.getNotifyContract() == null) {
      tenant.setNotifyContract(1);
    }
    if (tenant.getNotifyNotice() == null) {
      tenant.setNotifyNotice(1);
    }
  }
}
