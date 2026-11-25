package com.house.controller;

import com.house.model.*;
import com.house.repo.*;
import com.house.service.OperationLogService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {
  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;
  private final BillRepository billRepository;
  private final RoomRepository roomRepository;
  private final MeterReadingRepository meterReadingRepository;
  private final RoomApplianceRepository roomApplianceRepository;
  private final RepairOrderRepository repairOrderRepository;
  private final ComplaintSuggestionRepository complaintSuggestionRepository;
  private final BuildingRepository buildingRepository;
  private final OperationLogService operationLogService;

  public ExportController(TenantRepository tenantRepository,
      ContractRepository contractRepository,
      BillRepository billRepository,
      RoomRepository roomRepository,
      MeterReadingRepository meterReadingRepository,
      RoomApplianceRepository roomApplianceRepository,
      RepairOrderRepository repairOrderRepository,
      ComplaintSuggestionRepository complaintSuggestionRepository,
      BuildingRepository buildingRepository,
      OperationLogService operationLogService) {
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
    this.billRepository = billRepository;
    this.roomRepository = roomRepository;
    this.meterReadingRepository = meterReadingRepository;
    this.roomApplianceRepository = roomApplianceRepository;
    this.repairOrderRepository = repairOrderRepository;
    this.complaintSuggestionRepository = complaintSuggestionRepository;
    this.buildingRepository = buildingRepository;
    this.operationLogService = operationLogService;
  }

  @GetMapping("/room-appliance-checklist-pdf")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<byte[]> exportRoomApplianceChecklistPdf(@RequestParam Long roomId,
      @RequestParam(defaultValue = "move_in") String type,
      HttpServletRequest request) {
    try {
      Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("房间不存在"));
      String normalizedType = (type == null || type.isBlank()) ? "move_in" : type.toLowerCase();
      String typeText = "move_out".equals(normalizedType) ? "退租" : "入住";

      Building building = room.getBuildingId() == null ? null
          : buildingRepository.findById(room.getBuildingId()).orElse(null);
      Contract activeContract = contractRepository.findByRoomIdAndStatus(roomId, "ACTIVE").stream().findFirst()
          .orElse(null);
      Tenant tenant = activeContract == null || activeContract.getTenantId() == null ? null
          : tenantRepository.findById(activeContract.getTenantId()).orElse(null);

      List<RoomAppliance> appliances = roomApplianceRepository.findByRoomIdOrderByIdDesc(roomId);
      List<RepairOrder> repairs = repairOrderRepository.findByRoomIdOrderByCreatedAtDesc(roomId);

      byte[] pdfBytes = buildChecklistPdf(typeText, room, building, tenant, appliances, repairs);
      operationLogService.log("导出", "导出房间器具核对单PDF: 房间" + room.getRoomNumber() + "(" + typeText + ")", request);

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=room_" + roomId + "_" + normalizedType + "_checklist.pdf")
          .contentType(MediaType.APPLICATION_PDF)
          .body(pdfBytes);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(("PDF导出失败: " + e.getMessage()).getBytes());
    }
  }

  @PostMapping("/{module}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<byte[]> export(@PathVariable String module,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest request) {
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      String normalized = module == null ? "" : module.trim().toLowerCase();
      List<Long> selectedIds = extractIds(body);
      switch (normalized) {
        case "tenants" -> writeTenants(wb, selectedIds);
        case "contracts" -> writeContracts(wb, selectedIds);
        case "bills" -> writeBills(wb, selectedIds);
        case "rooms" -> writeRooms(wb, selectedIds);
        case "complaints" -> writeComplaints(wb, selectedIds);
        case "meter-readings" -> writeMeterReadings(wb, body);
        default -> throw new IllegalArgumentException("不支持的导出模块: " + module);
      }
      wb.write(out);
      operationLogService.log("批量操作", "批量导出: 模块=" + module, request);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + normalized + ".xlsx")
          .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(out.toByteArray());
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(("导出失败: " + e.getMessage()).getBytes());
    }
  }

  private void writeTenants(Workbook wb, List<Long> ids) {
    Sheet sheet = wb.createSheet("租户");
    Row h = sheet.createRow(0);
    h.createCell(0).setCellValue("ID");
    h.createCell(1).setCellValue("姓名");
    h.createCell(2).setCellValue("手机号");
    h.createCell(3).setCellValue("身份证号");
    h.createCell(4).setCellValue("黑名单");
    List<Tenant> data = (ids == null || ids.isEmpty()) ? tenantRepository.findAll() : tenantRepository.findAllById(ids);
    for (int i = 0; i < data.size(); i++) {
      Tenant t = data.get(i);
      Row r = sheet.createRow(i + 1);
      r.createCell(0).setCellValue(t.getId() == null ? 0 : t.getId());
      r.createCell(1).setCellValue(t.getName() == null ? "" : t.getName());
      r.createCell(2).setCellValue(t.getPhone() == null ? "" : t.getPhone());
      r.createCell(3).setCellValue(t.getIdCard() == null ? "" : t.getIdCard());
      r.createCell(4).setCellValue(Boolean.TRUE.equals(t.getIsBlacklisted()) ? "是" : "否");
    }
  }

  private void writeContracts(Workbook wb, List<Long> ids) {
    Sheet sheet = wb.createSheet("合同");
    Row h = sheet.createRow(0);
    // 列顺序对齐「人看得懂」的报表：先标识、再空间与租户、最后金额状态
    String[] headers = {"ID", "合同编号", "楼栋", "房间号", "租户姓名", "租户电话",
        "开始日期", "结束日期", "状态", "月租金", "押金", "付款方式"};
    for (int i = 0; i < headers.length; i++) {
      h.createCell(i).setCellValue(headers[i]);
    }

    List<Contract> data = (ids == null || ids.isEmpty())
        ? contractRepository.findAll() : contractRepository.findAllById(ids);

    // 批量取出关联的房间/楼栋/租户，避免逐行查询
    List<Long> roomIds = data.stream().map(Contract::getRoomId)
        .filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, Room> roomMap = new java.util.HashMap<>();
    if (!roomIds.isEmpty()) {
      roomRepository.findAllById(roomIds).forEach(r -> roomMap.put(r.getId(), r));
    }
    List<Long> buildingIds = roomMap.values().stream().map(Room::getBuildingId)
        .filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, Building> buildingMap = new java.util.HashMap<>();
    if (!buildingIds.isEmpty()) {
      buildingRepository.findAllById(buildingIds).forEach(b -> buildingMap.put(b.getId(), b));
    }
    List<Long> tenantIds = data.stream().map(Contract::getTenantId)
        .filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, Tenant> tenantMap = new java.util.HashMap<>();
    if (!tenantIds.isEmpty()) {
      tenantRepository.findAllById(tenantIds).forEach(t -> tenantMap.put(t.getId(), t));
    }

    for (int i = 0; i < data.size(); i++) {
      Contract c = data.get(i);
      Room room = roomMap.get(c.getRoomId());
      Building building = room == null ? null : buildingMap.get(room.getBuildingId());
      Tenant tenant = tenantMap.get(c.getTenantId());

      Row r = sheet.createRow(i + 1);
      int col = 0;
      r.createCell(col++).setCellValue(c.getId() == null ? 0 : c.getId());
      r.createCell(col++).setCellValue(text(c.getContractNo()));
      r.createCell(col++).setCellValue(building == null ? "" : text(building.getName()));
      r.createCell(col++).setCellValue(room == null ? "" : text(room.getRoomNumber()));
      r.createCell(col++).setCellValue(tenant == null ? "" : text(tenant.getName()));
      r.createCell(col++).setCellValue(tenant == null ? "" : text(tenant.getPhone()));
      r.createCell(col++).setCellValue(c.getStartDate() == null ? "" : c.getStartDate().toString());
      r.createCell(col++).setCellValue(c.getEndDate() == null ? "" : c.getEndDate().toString());
      r.createCell(col++).setCellValue(text(c.getStatus()));
      r.createCell(col++).setCellValue(c.getMonthlyRent() == null ? 0 : c.getMonthlyRent());
      r.createCell(col++).setCellValue(c.getDeposit() == null ? 0 : c.getDeposit());
      r.createCell(col).setCellValue(text(c.getPaymentMethod()));
    }

    // 列宽自适应（中文按 2 字符宽度估算）
    for (int i = 0; i < headers.length; i++) {
      sheet.setColumnWidth(i, Math.max(12, headers[i].length() * 2 + 4) * 256);
    }
  }

  private String text(String value) {
    return value == null ? "" : value;
  }

  private void writeBills(Workbook wb, List<Long> ids) {
    Sheet sheet = wb.createSheet("账单");
    Row h = sheet.createRow(0);
    h.createCell(0).setCellValue("ID");
    h.createCell(1).setCellValue("合同ID");
    h.createCell(2).setCellValue("账单月份");
    h.createCell(3).setCellValue("总金额");
    h.createCell(4).setCellValue("状态");
    List<Bill> data = (ids == null || ids.isEmpty()) ? billRepository.findAll() : billRepository.findAllById(ids);
    for (int i = 0; i < data.size(); i++) {
      Bill b = data.get(i);
      Row r = sheet.createRow(i + 1);
      r.createCell(0).setCellValue(b.getId() == null ? 0 : b.getId());
      r.createCell(1).setCellValue(b.getContractId() == null ? 0 : b.getContractId());
      r.createCell(2).setCellValue(b.getBillMonth() == null ? "" : b.getBillMonth());
      r.createCell(3).setCellValue(b.getTotalAmount() == null ? 0 : b.getTotalAmount());
      r.createCell(4).setCellValue(b.getStatus() == null ? "" : b.getStatus());
    }
  }

  private void writeRooms(Workbook wb, List<Long> ids) {
    Sheet sheet = wb.createSheet("房间");
    Row h = sheet.createRow(0);
    h.createCell(0).setCellValue("ID");
    h.createCell(1).setCellValue("楼栋ID");
    h.createCell(2).setCellValue("房间号");
    h.createCell(3).setCellValue("楼层");
    h.createCell(4).setCellValue("户型");
    h.createCell(5).setCellValue("面积");
    h.createCell(6).setCellValue("状态");
    List<Room> data = (ids == null || ids.isEmpty()) ? roomRepository.findAll() : roomRepository.findAllById(ids);
    for (int i = 0; i < data.size(); i++) {
      Room room = data.get(i);
      Row r = sheet.createRow(i + 1);
      r.createCell(0).setCellValue(room.getId() == null ? 0 : room.getId());
      r.createCell(1).setCellValue(room.getBuildingId() == null ? 0 : room.getBuildingId());
      r.createCell(2).setCellValue(room.getRoomNumber() == null ? "" : room.getRoomNumber());
      r.createCell(3).setCellValue(room.getFloor() == null ? 0 : room.getFloor());
      r.createCell(4).setCellValue(room.getLayout() == null ? "" : room.getLayout());
      r.createCell(5).setCellValue(room.getArea() == null ? 0 : room.getArea());
      r.createCell(6).setCellValue(room.getStatus() == null ? "" : room.getStatus());
    }
  }

  private void writeMeterReadings(Workbook wb, Map<String, Object> body) {
    Sheet sheet = wb.createSheet("水电读数");
    Row h = sheet.createRow(0);
    h.createCell(0).setCellValue("ID");
    h.createCell(1).setCellValue("房间ID");
    h.createCell(2).setCellValue("日期");
    h.createCell(3).setCellValue("水表读数");
    h.createCell(4).setCellValue("电表读数");

    List<MeterReading> data = new ArrayList<>(meterReadingRepository.findAll());
    if (body != null && body.get("startDate") != null && body.get("endDate") != null) {
      String startDate = body.get("startDate").toString();
      String endDate = body.get("endDate").toString();
      data = data.stream().filter(m -> m.getRecordDate() != null
          && !m.getRecordDate().isBefore(java.time.LocalDate.parse(startDate))
          && !m.getRecordDate().isAfter(java.time.LocalDate.parse(endDate))).toList();
    }

    for (int i = 0; i < data.size(); i++) {
      MeterReading m = data.get(i);
      Row r = sheet.createRow(i + 1);
      r.createCell(0).setCellValue(m.getId() == null ? 0 : m.getId());
      r.createCell(1).setCellValue(m.getRoomId() == null ? 0 : m.getRoomId());
      r.createCell(2).setCellValue(m.getRecordDate() == null ? "" : m.getRecordDate().toString());
      r.createCell(3).setCellValue(m.getWaterReading() == null ? 0 : m.getWaterReading());
      r.createCell(4).setCellValue(m.getElectricReading() == null ? 0 : m.getElectricReading());
    }
  }

  private void writeComplaints(Workbook wb, List<Long> ids) {
    Sheet sheet = wb.createSheet("投诉建议");
    Row h = sheet.createRow(0);
    h.createCell(0).setCellValue("ID");
    h.createCell(1).setCellValue("投诉编号");
    h.createCell(2).setCellValue("提交时间");
    h.createCell(3).setCellValue("租户");
    h.createCell(4).setCellValue("房间号");
    h.createCell(5).setCellValue("投诉类型");
    h.createCell(6).setCellValue("标题");
    h.createCell(7).setCellValue("紧急程度");
    h.createCell(8).setCellValue("状态");
    h.createCell(9).setCellValue("是否匿名");
    List<ComplaintSuggestion> data = (ids == null || ids.isEmpty())
        ? complaintSuggestionRepository.findAll()
        : complaintSuggestionRepository.findAllById(ids);
    for (int i = 0; i < data.size(); i++) {
      ComplaintSuggestion c = data.get(i);
      Room room = c.getRoomId() == null ? null : roomRepository.findById(c.getRoomId()).orElse(null);
      Tenant tenant = c.getTenantId() == null ? null : tenantRepository.findById(c.getTenantId()).orElse(null);
      boolean anonymous = c.getIsAnonymous() != null && c.getIsAnonymous() == 1;
      Row r = sheet.createRow(i + 1);
      r.createCell(0).setCellValue(c.getId() == null ? 0 : c.getId());
      r.createCell(1).setCellValue(c.getComplaintNo() == null ? "" : c.getComplaintNo());
      r.createCell(2).setCellValue(c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());
      r.createCell(3).setCellValue(anonymous ? "匿名" : (tenant == null ? "" : tenant.getName()));
      r.createCell(4).setCellValue(room == null ? "" : room.getRoomNumber());
      r.createCell(5).setCellValue(c.getType() == null ? "" : c.getType());
      r.createCell(6).setCellValue(c.getTitle() == null ? "" : c.getTitle());
      r.createCell(7).setCellValue(c.getPriority() == null ? "" : c.getPriority());
      r.createCell(8).setCellValue(c.getStatus() == null ? "" : c.getStatus());
      r.createCell(9).setCellValue(anonymous ? "是" : "否");
    }
  }

  private List<Long> extractIds(Map<String, Object> body) {
    if (body == null || body.get("ids") == null) {
      return List.of();
    }
    List<?> raw = (List<?>) body.get("ids");
    return raw.stream().map(v -> ((Number) v).longValue()).toList();
  }

  private byte[] buildChecklistPdf(String typeText,
      Room room,
      Building building,
      Tenant tenant,
      List<RoomAppliance> appliances,
      List<RepairOrder> repairs) throws Exception {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
      PdfWriter.getInstance(document, out);
      document.open();

      BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
      Font titleFont = new Font(bf, 18, Font.BOLD);
      Font normalFont = new Font(bf, 11, Font.NORMAL);
      Font smallFont = new Font(bf, 10, Font.NORMAL);

      Paragraph title = new Paragraph("房间器具核对单（" + typeText + "）", titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      title.setSpacingAfter(14);
      document.add(title);

      PdfPTable infoTable = new PdfPTable(4);
      infoTable.setWidthPercentage(100);
      infoTable.setWidths(new float[] { 1.2f, 2.3f, 1.2f, 2.3f });
      addCell(infoTable, "楼栋", normalFont, true);
      addCell(infoTable, building == null ? "-" : building.getName(), normalFont, false);
      addCell(infoTable, "房间号", normalFont, true);
      addCell(infoTable, room.getRoomNumber() == null ? "-" : room.getRoomNumber(), normalFont, false);
      addCell(infoTable, "核对日期", normalFont, true);
      addCell(infoTable, LocalDate.now().format(DateTimeFormatter.ISO_DATE), normalFont, false);
      addCell(infoTable, "租户", normalFont, true);
      addCell(infoTable,
          tenant == null ? "-" : (tenant.getName() + (tenant.getPhone() == null ? "" : " / " + tenant.getPhone())),
          normalFont, false);
      infoTable.setSpacingAfter(12);
      document.add(infoTable);

      Paragraph section1 = new Paragraph("一、器具清单", normalFont);
      section1.setSpacingAfter(8);
      document.add(section1);

      PdfPTable applianceTable = new PdfPTable(8);
      applianceTable.setWidthPercentage(100);
      applianceTable.setWidths(new float[] { 0.7f, 1.5f, 1.4f, 0.8f, 0.8f, 1f, 2.2f, 0.9f });
      addCell(applianceTable, "序号", smallFont, true);
      addCell(applianceTable, "器具名称", smallFont, true);
      addCell(applianceTable, "品牌", smallFont, true);
      addCell(applianceTable, "数量", smallFont, true);
      addCell(applianceTable, "单位", smallFont, true);
      addCell(applianceTable, "状态", smallFont, true);
      addCell(applianceTable, "备注", smallFont, true);
      addCell(applianceTable, "照片数", smallFont, true);

      for (int i = 0; i < appliances.size(); i++) {
        RoomAppliance a = appliances.get(i);
        addCell(applianceTable, String.valueOf(i + 1), smallFont, false);
        addCell(applianceTable, nullable(a.getApplianceName()), smallFont, false);
        addCell(applianceTable, nullable(a.getBrand()), smallFont, false);
        addCell(applianceTable, String.valueOf(a.getQuantity() == null ? 0 : a.getQuantity()), smallFont, false);
        addCell(applianceTable, nullable(a.getUnit()), smallFont, false);
        addCell(applianceTable, nullable(a.getStatus()), smallFont, false);
        addCell(applianceTable, nullable(a.getRemark()), smallFont, false);
        addCell(applianceTable, String.valueOf(countPhotos(a.getPhotos())), smallFont, false);
      }
      if (appliances.isEmpty()) {
        PdfPCell empty = new PdfPCell(new Phrase("暂无器具数据", smallFont));
        empty.setColspan(8);
        empty.setHorizontalAlignment(Element.ALIGN_CENTER);
        empty.setPadding(8);
        applianceTable.addCell(empty);
      }
      applianceTable.setSpacingAfter(12);
      document.add(applianceTable);

      Paragraph section2 = new Paragraph("二、最近维修记录（最多10条）", normalFont);
      section2.setSpacingAfter(8);
      document.add(section2);

      PdfPTable repairTable = new PdfPTable(6);
      repairTable.setWidthPercentage(100);
      repairTable.setWidths(new float[] { 1.5f, 1f, 1f, 1f, 1f, 2.2f });
      addCell(repairTable, "工单号", smallFont, true);
      addCell(repairTable, "故障类型", smallFont, true);
      addCell(repairTable, "状态", smallFont, true);
      addCell(repairTable, "维修工", smallFont, true);
      addCell(repairTable, "费用", smallFont, true);
      addCell(repairTable, "维修结果", smallFont, true);

      List<RepairOrder> limited = repairs.stream().limit(10).toList();
      for (RepairOrder r : limited) {
        addCell(repairTable, nullable(r.getOrderNo()), smallFont, false);
        addCell(repairTable, nullable(r.getFaultType()), smallFont, false);
        addCell(repairTable, nullable(r.getStatus()), smallFont, false);
        addCell(repairTable, nullable(r.getWorkerName()), smallFont, false);
        addCell(repairTable, String.format("%.2f", r.getTotalFee() == null ? 0D : r.getTotalFee()), smallFont, false);
        addCell(repairTable, nullable(r.getRepairResult()), smallFont, false);
      }
      if (limited.isEmpty()) {
        PdfPCell empty = new PdfPCell(new Phrase("暂无维修记录", smallFont));
        empty.setColspan(6);
        empty.setHorizontalAlignment(Element.ALIGN_CENTER);
        empty.setPadding(8);
        repairTable.addCell(empty);
      }
      repairTable.setSpacingAfter(16);
      document.add(repairTable);

      document.add(new Paragraph("核对说明：以上器具已由双方共同核对确认。", normalFont));
      document.add(new Paragraph(" ", normalFont));
      document.add(new Paragraph("管理员签字：________________    租户签字：________________", normalFont));
      document.add(new Paragraph("日期：________________", normalFont));

      document.close();
      return out.toByteArray();
    }
  }

  private void addCell(PdfPTable table, String text, Font font, boolean header) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setPadding(6);
    if (header) {
      cell.setHorizontalAlignment(Element.ALIGN_CENTER);
      cell.setBackgroundColor(new BaseColor(242, 242, 242));
    }
    table.addCell(cell);
  }

  private String nullable(String text) {
    return text == null || text.isBlank() ? "-" : text;
  }

  private int countPhotos(String jsonText) {
    if (jsonText == null || jsonText.isBlank() || "[]".equals(jsonText)) {
      return 0;
    }
    long cnt = jsonText.chars().filter(ch -> ch == ',').count();
    return (int) cnt + 1;
  }
}
