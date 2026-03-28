package com.house.service;

import com.house.model.Contract;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.ContractRepository;
import com.house.repo.RoomRepository;
import com.house.repo.TenantRepository;
import com.house.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TenantTokenService {
  private static final List<String> ACTIVE_CONTRACT_STATUS = List.of("ACTIVE", "进行中");

  private final JwtUtil jwtUtil;
  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;
  private final RoomRepository roomRepository;

  public TenantTokenService(JwtUtil jwtUtil,
      TenantRepository tenantRepository,
      ContractRepository contractRepository,
      RoomRepository roomRepository) {
    this.jwtUtil = jwtUtil;
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
    this.roomRepository = roomRepository;
  }

  public Map<String, Object> parseAndValidateTenant(String bearerToken) {
    String token = extractToken(bearerToken);
    if (token == null || token.isBlank() || !jwtUtil.validateToken(token)) {
      throw new IllegalArgumentException("租客登录已失效，请重新登录");
    }

    Claims claims = jwtUtil.parseToken(token);
    String role = claims.get("role", String.class);
    if (!"TENANT".equalsIgnoreCase(role)) {
      throw new IllegalArgumentException("无租客访问权限");
    }

    Long tenantId = claims.get("tenantId", Long.class);
    Long roomId = claims.get("roomId", Long.class);
    Boolean prospect = claims.get("prospect", Boolean.class);
    boolean isProspect = prospect != null && prospect;
    if (tenantId == null) {
      throw new IllegalArgumentException("租客上下文缺失，请重新登录");
    }

    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("租客不存在"));
    List<Contract> contracts = contractRepository.findByTenantIdAndStatusIn(tenantId, ACTIVE_CONTRACT_STATUS);
    Set<Long> roomIds = new HashSet<>();
    for (Contract contract : contracts) {
      if (contract.getRoomId() != null) {
        roomIds.add(contract.getRoomId());
      }
    }
    if (!isProspect && (roomId == null || !roomIds.contains(roomId))) {
      throw new IllegalArgumentException("当前房间已失效，请重新选择房间");
    }

    Map<String, Object> result = new HashMap<>();
    result.put("token", token);
    result.put("tenant", tenant);
    result.put("tenantId", tenantId);
    result.put("roomId", roomId);
    result.put("roomIds", roomIds);
    result.put("isProspect", isProspect);
    return result;
  }

  public String buildTenantToken(Tenant tenant, Long roomId) {
    return buildTenantToken(tenant, roomId, false);
  }

  public String buildTenantToken(Tenant tenant, Long roomId, boolean isProspect) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("tenantId", tenant.getId());
    if (roomId != null) {
      claims.put("roomId", roomId);
    }
    claims.put("phone", tenant.getPhone());
    claims.put("prospect", isProspect);
    return jwtUtil.generateToken("tenant:" + tenant.getPhone(), tenant.getId(), "TENANT", claims);
  }

  public List<Map<String, Object>> buildRoomList(List<Contract> contracts) {
    Set<Long> roomIds = new LinkedHashSet<>();
    for (Contract contract : contracts) {
      if (contract.getRoomId() != null) {
        roomIds.add(contract.getRoomId());
      }
    }

    List<Map<String, Object>> rooms = new ArrayList<>();
    for (Long roomId : roomIds) {
      Room room = roomRepository.findById(roomId).orElse(null);
      if (room == null) {
        continue;
      }
      Map<String, Object> item = new HashMap<>();
      item.put("roomId", room.getId());
      item.put("roomNumber", room.getRoomNumber());
      item.put("buildingId", room.getBuildingId());
      rooms.add(item);
    }
    return rooms;
  }

  private String extractToken(String bearerToken) {
    if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
      return null;
    }
    return bearerToken.substring(7);
  }
}
