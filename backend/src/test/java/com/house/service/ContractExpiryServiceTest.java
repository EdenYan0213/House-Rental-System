package com.house.service;

import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.Contract;
import com.house.model.ContractStatus;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.repo.BillRepository;
import com.house.repo.ContractRepository;
import com.house.repo.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 合同到期自动处理测试。
 *
 * <p>守护一个此前缺失的能力：系统原本没有定时任务把到期合同置为「已到期」，
 * 房间会一直显示「已租」，出租率统计与账单生成都受影响。
 *
 * <p>同时锁住两条安全边界：
 * <ul>
 *   <li>合同有未结清账单时<b>不释放房间</b>（欠款需要房间作为催缴线索）</li>
 *   <li>房间已有另一份生效合同时<b>不释放房间</b>（换租/续租场景）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("合同到期自动处理")
class ContractExpiryServiceTest {

  @Mock
  private ContractRepository contractRepository;
  @Mock
  private RoomRepository roomRepository;
  @Mock
  private BillRepository billRepository;

  private ContractExpiryService service;

  @BeforeEach
  void setUp() {
    service = new ContractExpiryService(contractRepository, roomRepository, billRepository);
  }

  private Contract contract(long id, long roomId, LocalDate endDate, String status) {
    Contract c = new Contract();
    c.setId(id);
    c.setRoomId(roomId);
    c.setContractNo("CT" + id);
    c.setEndDate(endDate);
    c.setStatus(status);
    return c;
  }

  private Room room(long id, String status) {
    Room r = new Room();
    r.setId(id);
    r.setStatus(status);
    return r;
  }

  private void stub(List<Contract> contracts, List<Room> rooms, List<Bill> bills,
      List<Contract> roomActiveContracts) {
    when(contractRepository.findByStatusIn(anyList())).thenReturn(contracts);
    when(roomRepository.findAllById(anyList())).thenReturn(rooms);
    when(billRepository.findByContractIdIn(anyList())).thenReturn(bills);
    when(contractRepository.findByRoomIdInAndStatus(anyList(), eq(ContractStatus.ACTIVE)))
        .thenReturn(roomActiveContracts);
    when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
    when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  @DisplayName("到期合同置为 EXPIRED，并释放房间为 IDLE")
  void expiresAndReleasesRoom() {
    LocalDate today = LocalDate.of(2026, 6, 1);
    Contract c = contract(1L, 10L, today.minusDays(1), ContractStatus.ACTIVE);
    Room r = room(10L, RoomStatus.RENTED);
    stub(List.of(c), List.of(r), List.of(), List.of(c));

    Map<String, Object> result = service.expireContracts(today);

    assertEquals(ContractStatus.EXPIRED, c.getStatus());
    assertEquals(RoomStatus.IDLE, r.getStatus());
    assertEquals(1, result.get("expiredCount"));
    assertEquals(1, result.get("releasedRoomCount"));
  }

  @Test
  @DisplayName("有未结清账单时不释放房间")
  void keepsRoomWhenUnpaid() {
    LocalDate today = LocalDate.of(2026, 6, 1);
    Contract c = contract(1L, 10L, today.minusDays(1), ContractStatus.ACTIVE);
    Room r = room(10L, RoomStatus.RENTED);
    Bill unpaid = new Bill();
    unpaid.setContractId(1L);
    unpaid.setStatus(BillStatus.PENDING);

    stub(List.of(c), List.of(r), List.of(unpaid), List.of(c));

    Map<String, Object> result = service.expireContracts(today);

    // 合同仍应过期
    assertEquals(ContractStatus.EXPIRED, c.getStatus());
    // 但房间保持已租，作为催缴线索
    assertEquals(RoomStatus.RENTED, r.getStatus());
    assertEquals(0, result.get("releasedRoomCount"));
    assertEquals(1, result.get("keptRoomCount"));
    verify(roomRepository, never()).save(r);
  }

  @Test
  @DisplayName("房间已有另一份生效合同时不释放（换租/续租）")
  void keepsRoomWhenAnotherActiveContract() {
    LocalDate today = LocalDate.of(2026, 6, 1);
    Contract old = contract(1L, 10L, today.minusDays(1), ContractStatus.ACTIVE);
    // 同房间的另一份合同，到期日更晚
    Contract renewed = contract(2L, 10L, today.plusMonths(11), ContractStatus.ACTIVE);
    Room r = room(10L, RoomStatus.RENTED);

    stub(List.of(old), List.of(r), List.of(), List.of(old, renewed));

    Map<String, Object> result = service.expireContracts(today);

    assertEquals(RoomStatus.RENTED, r.getStatus(), "有接续合同，房间不应被释放");
    assertEquals(0, result.get("releasedRoomCount"));
    verify(roomRepository, never()).save(r);
  }

  @Test
  @DisplayName("未到期的合同不动")
  void ignoresFutureContracts() {
    LocalDate today = LocalDate.of(2026, 6, 1);
    Contract c = contract(1L, 10L, today.plusDays(1), ContractStatus.ACTIVE);
    Room r = room(10L, RoomStatus.RENTED);
    stub(List.of(c), List.of(r), List.of(), List.of(c));

    Map<String, Object> result = service.expireContracts(today);

    assertEquals(ContractStatus.ACTIVE, c.getStatus());
    assertEquals(0, result.get("expiredCount"));
    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName("无到期日的长期合同不处理")
  void ignoresContractWithoutEndDate() {
    LocalDate today = LocalDate.of(2026, 6, 1);
    Contract c = contract(1L, 10L, null, ContractStatus.ACTIVE);
    stub(List.of(c), List.of(room(10L, RoomStatus.RENTED)), List.of(), List.of());

    Map<String, Object> result = service.expireContracts(today);

    assertEquals(0, result.get("expiredCount"));
  }

  @Test
  @DisplayName("中文旧状态「进行中」也能被识别并过期")
  void handlesLegacyChineseStatus() {
    LocalDate today = LocalDate.of(2026, 6, 1);
    // 仓储层用 ACTIVE_RAW_VALUES 查询，这里返回中文状态的行模拟历史数据
    Contract c = contract(1L, 10L, today.minusDays(5), "进行中");
    Room r = room(10L, RoomStatus.RENTED);
    stub(List.of(c), List.of(r), List.of(), List.of(c));

    service.expireContracts(today);

    assertEquals(ContractStatus.EXPIRED, c.getStatus());
    assertEquals(RoomStatus.IDLE, r.getStatus());
  }
}
