package com.house.controller;

import com.house.model.Building;
import com.house.model.MeterReading;
import com.house.model.OperationSnapshot;
import com.house.model.Room;
import com.house.repo.BuildingRepository;
import com.house.repo.MeterReadingRepository;
import com.house.repo.RoomRepository;
import com.house.service.MeterReadingBatchService;
import com.house.service.MeterReadingGenerateService;
import com.house.service.OperationSnapshotService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meter-readings")
public class MeterReadingController {

  private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
  private static final Set<String> REQUIRED_HEADERS = Set.of("日期", "房间号", "水表读数", "电表读数");

  private final MeterReadingRepository meterReadingRepository;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final MeterReadingBatchService meterReadingBatchService;
  private final MeterReadingGenerateService meterReadingGenerateService;
  private final OperationSnapshotService operationSnapshotService;

  public MeterReadingController(MeterReadingRepository meterReadingRepository,
      RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      MeterReadingBatchService meterReadingBatchService,
      MeterReadingGenerateService meterReadingGenerateService,
      OperationSnapshotService operationSnapshotService) {
    this.meterReadingRepository = meterReadingRepository;
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.meterReadingBatchService = meterReadingBatchService;
    this.meterReadingGenerateService = meterReadingGenerateService;
    this.operationSnapshotService = operationSnapshotService;
  }

  /**
   * 水电流水列表。
   *
   * <p>向后兼容：不传 page 返回纯数组（导出等场景依赖该形状），传了 page 返回分页对象。
   */
  @GetMapping
  public Object list(
      @RequestParam(required = false) String date,
      @RequestParam(required = false) Long buildingId,
      @RequestParam(required = false) Long roomId,
      @RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "50") int size) {
    List<MeterReading> readings;
    if (roomId != null) {
      readings = meterReadingRepository.findByRoomIdOrderByRecordDateDesc(roomId);
    } else if (date != null && !date.isBlank()) {
      readings = meterReadingRepository.findByRecordDate(java.time.LocalDate.parse(date));
      if (buildingId != null) {
        Set<Long> roomIds = roomRepository.findByBuildingId(buildingId).stream()
            .map(Room::getId)
            .collect(Collectors.toSet());
        readings = readings.stream().filter(r -> roomIds.contains(r.getRoomId())).toList();
      }
    } else {
      readings = meterReadingRepository.findAll();
    }

    if (page == null) {
      return readings;
    }
    int total = readings.size();
    int safeSize = size <= 0 ? 50 : size;
    int from = Math.max(page, 0) * safeSize;
    int to = Math.min(total, from + safeSize);
    List<MeterReading> content = from >= total ? List.of() : readings.subList(from, to);

    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", total);
    result.put("page", page);
    result.put("size", safeSize);
    return result;
  }

  /**
   * 各房间「最近一次」读数。
   *
   * <p>抄表页需要显示「上次读数」用于算用量。原实现取「抄表日期的前一天」，
   * 但租户常隔月抄表，前一天往往没有记录，界面就显示「无记录」。
   * 这里改为取每个房间在指定日期之前**最后一次**的读数，语义更符合实际。
   *
   * @param buildingId 可选，限定楼栋
   * @param beforeDate 可选，只取该日期（不含）之前的记录；不传则取全部历史
   * @return roomId → {waterReading, electricReading, recordDate}
   */
  @GetMapping("/last-by-room")
  public java.util.Map<String, Object> lastByRoom(
      @RequestParam(required = false) Long buildingId,
      @RequestParam(required = false) String beforeDate) {
    java.time.LocalDate before = (beforeDate == null || beforeDate.isBlank())
        ? null : java.time.LocalDate.parse(beforeDate);

    List<Room> rooms = buildingId != null
        ? roomRepository.findByBuildingId(buildingId)
        : roomRepository.findAll();

    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
    for (Room room : rooms) {
      MeterReading last = before != null
          ? meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(room.getId(), before)
          : meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(
              room.getId(), java.time.LocalDate.of(9999, 12, 31));

      if (last == null) {
        continue;
      }
      java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
      item.put("waterReading", last.getWaterReading());
      item.put("electricReading", last.getElectricReading());
      item.put("recordDate", last.getRecordDate());
      result.put(String.valueOf(room.getId()), item);
    }
    return result;
  }

  @GetMapping("/export-csv")
  public ResponseEntity<byte[]> exportCsv(
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) Long buildingId,
      @RequestParam(required = false) Long roomId) {
    List<MeterReading> readings = queryReadingsForExport(date, startDate, endDate, buildingId, roomId);

    Map<Long, Room> roomMap = roomRepository.findAll().stream()
        .collect(Collectors.toMap(Room::getId, r -> r));
    Map<Long, Building> buildingMap = buildingRepository.findAll().stream()
        .collect(Collectors.toMap(Building::getId, b -> b));

    StringBuilder csv = new StringBuilder();
    csv.append("记录ID,日期,楼栋,房间号,水表读数,电表读数\n");
    for (MeterReading item : readings) {
      Room room = roomMap.get(item.getRoomId());
      Building building = room == null ? null : buildingMap.get(room.getBuildingId());
      csv.append(csvCell(item.getId())).append(',')
          .append(csvCell(item.getRecordDate())).append(',')
          .append(csvCell(building == null ? "" : building.getName())).append(',')
          .append(csvCell(room == null ? "" : room.getRoomNumber())).append(',')
          .append(csvCell(item.getWaterReading())).append(',')
          .append(csvCell(item.getElectricReading()))
          .append('\n');
    }

    byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=meter-readings-export.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(bytes);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public List<MeterReading> save(@RequestBody List<Map<String, Object>> data) {
    try {
      List<MeterReadingBatchService.MeterUpsertItem> items = new ArrayList<>();
      for (Map<String, Object> item : data) {
        Long roomId = ((Number) item.get("roomId")).longValue();
        java.time.LocalDate recordDate = java.time.LocalDate.parse(String.valueOf(item.get("recordDate")));
        Double waterReading = item.get("waterReading") != null ? ((Number) item.get("waterReading")).doubleValue() : 0;
        Double electricReading = item.get("electricReading") != null
            ? ((Number) item.get("electricReading")).doubleValue()
            : 0;
        items.add(new MeterReadingBatchService.MeterUpsertItem(roomId, recordDate, waterReading, electricReading));
      }
      return meterReadingBatchService.upsertBatch(items);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("保存水电记录失败: " + e.getMessage());
    }
  }

  @GetMapping("/import-template")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<byte[]> downloadImportTemplate() {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("A栋");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("日期");
      header.createCell(1).setCellValue("房间号");
      header.createCell(2).setCellValue("水表读数");
      header.createCell(3).setCellValue("电表读数");

      Object[][] demo = new Object[][] {
          { "2023-10-15", "101", 1250.5, 3680 },
          { "2023-10-15", "102", 1312.3, 3725 },
          { "2023-10-16", "101", 1255.2, 3685 },
          { "2023-10-16", "102", 1318.9, 3732 }
      };
      for (int i = 0; i < demo.length; i++) {
        Row row = sheet.createRow(i + 1);
        row.createCell(0).setCellValue((String) demo[i][0]);
        row.createCell(1).setCellValue((String) demo[i][1]);
        row.createCell(2).setCellValue((Double) demo[i][2]);
        row.createCell(3).setCellValue((Integer) demo[i][3]);
      }
      for (int i = 0; i < 4; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(out);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=meter-reading-import-template.xlsx")
          .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(out.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException("生成模板失败: " + e.getMessage());
    }
  }

  @PostMapping("/import/parse")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public Map<String, Object> parseImportFile(@RequestParam("file") MultipartFile file) {
    validateExcelFile(file);

    List<Building> buildings = buildingRepository.findAll();
    Map<String, Building> buildingByName = new HashMap<>();
    for (Building building : buildings) {
      buildingByName.put(building.getName() == null ? "" : building.getName().trim(), building);
    }

    Map<Long, Map<String, Room>> roomsByBuilding = new HashMap<>();
    for (Building building : buildings) {
      Map<String, Room> roomMap = new HashMap<>();
      for (Room room : roomRepository.findByBuildingId(building.getId())) {
        roomMap.put(room.getRoomNumber() == null ? "" : room.getRoomNumber().trim(), room);
      }
      roomsByBuilding.put(building.getId(), roomMap);
    }

    List<ImportErrorItem> errors = new ArrayList<>();
    List<ImportSuccessItem> success = new ArrayList<>();
    Set<String> dateSet = new HashSet<>();
    Set<String> buildingSet = new HashSet<>();
    int totalDataRows = 0;

    try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
      for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
        Sheet sheet = workbook.getSheetAt(s);
        String rawSheetName = sheet.getSheetName();
        String sheetName = rawSheetName == null ? "" : rawSheetName.trim();
        Building building = buildingByName.get(sheetName);
        if (building == null) {
          errors.add(ImportErrorItem.of(sheetName, 1, "", "", "Sheet [" + sheetName + "] 未找到对应楼栋，请检查"));
          continue;
        }

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
          errors.add(ImportErrorItem.of(sheetName, 1, "", "", "缺少标题行"));
          continue;
        }

        Map<String, Integer> columnIndex = resolveHeaderColumns(headerRow);
        for (String required : REQUIRED_HEADERS) {
          if (!columnIndex.containsKey(required)) {
            errors.add(ImportErrorItem.of(sheetName, 1, "", "", "缺少必需的列：" + required));
          }
        }
        if (REQUIRED_HEADERS.stream().anyMatch(h -> !columnIndex.containsKey(h))) {
          continue;
        }

        Map<String, Integer> duplicateIndex = new HashMap<>();
        Map<String, Room> roomMap = roomsByBuilding.getOrDefault(building.getId(), Map.of());
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
          Row row = sheet.getRow(r);
          int rowNumber = r + 1;
          if (isBlankRow(row)) {
            errors.add(ImportErrorItem.of(sheetName, rowNumber, "", "", "数据行不能包含空行"));
            continue;
          }
          totalDataRows++;

          String dateText = getCellText(row, columnIndex.get("日期"));
          String roomNumber = getCellText(row, columnIndex.get("房间号")).trim();
          String waterText = getCellText(row, columnIndex.get("水表读数"));
          String electricText = getCellText(row, columnIndex.get("电表读数"));

          java.time.LocalDate recordDate = parseDateCell(row.getCell(columnIndex.get("日期")));
          if (recordDate == null) {
            errors.add(ImportErrorItem.of(sheetName, rowNumber, dateText, roomNumber,
                "第" + rowNumber + "行日期\"" + dateText + "\"格式无效"));
            continue;
          }

          Room room = roomMap.get(roomNumber);
          if (room == null) {
            errors.add(ImportErrorItem.of(sheetName, rowNumber, recordDate.toString(), roomNumber,
                "第" + rowNumber + "行房间号\"" + roomNumber + "\"在楼栋\"" + sheetName + "\"中不存在"));
            continue;
          }

          Double waterReading = parseNumberCell(row.getCell(columnIndex.get("水表读数")));
          if (waterReading == null || waterReading < 0) {
            errors.add(ImportErrorItem.of(sheetName, rowNumber, recordDate.toString(), roomNumber,
                "第" + rowNumber + "行水表读数\"" + waterText + "\"不是有效数字"));
            continue;
          }

          Double electricReading = parseNumberCell(row.getCell(columnIndex.get("电表读数")));
          if (electricReading == null || electricReading < 0) {
            errors.add(ImportErrorItem.of(sheetName, rowNumber, recordDate.toString(), roomNumber,
                "第" + rowNumber + "行电表读数\"" + electricText + "\"不是有效数字"));
            continue;
          }

          String duplicateKey = recordDate + "|" + roomNumber;
          if (duplicateIndex.containsKey(duplicateKey)) {
            Integer duplicateRow = duplicateIndex.get(duplicateKey);
            errors.add(ImportErrorItem.of(sheetName, rowNumber, recordDate.toString(), roomNumber,
                "第" + rowNumber + "行与第" + duplicateRow + "行重复（日期：" + recordDate + "，房间号：" + roomNumber + "）"));
            continue;
          }
          duplicateIndex.put(duplicateKey, rowNumber);

          success.add(ImportSuccessItem.builder()
              .sheetName(sheetName)
              .rowNumber(rowNumber)
              .buildingId(building.getId())
              .buildingName(sheetName)
              .roomId(room.getId())
              .roomNumber(roomNumber)
              .recordDate(recordDate.toString())
              .waterReading(waterReading)
              .electricReading(electricReading)
              .build());
          buildingSet.add(sheetName);
          dateSet.add(recordDate.toString());
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("解析Excel失败: " + e.getMessage());
    }

    long futureDateCount = success.stream()
        .filter(i -> java.time.LocalDate.parse(i.getRecordDate()).isAfter(java.time.LocalDate.now()))
        .count();

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("totalRows", totalDataRows);
    summary.put("successCount", success.size());
    summary.put("errorCount", errors.size());
    summary.put("buildingCount", buildingSet.size());
    summary.put("dateCount", dateSet.size());
    summary.put("futureDateCount", futureDateCount);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("summary", summary);
    result.put("successData", success);
    result.put("errorData", errors);
    result.put("canImport", !success.isEmpty() && errors.isEmpty());
    return result;
  }

  @PostMapping("/import/confirm")
  @Transactional
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public Map<String, Object> confirmImport(@RequestBody ImportConfirmRequest request) {
    if (request == null || request.getRecords() == null || request.getRecords().isEmpty()) {
      throw new IllegalArgumentException("没有可导入的数据");
    }

    Set<Long> roomIds = request.getRecords().stream().map(ImportSuccessItem::getRoomId).collect(Collectors.toSet());
    Map<Long, Room> roomMap = roomRepository.findAllById(roomIds).stream()
        .collect(Collectors.toMap(Room::getId, r -> r));
    if (roomMap.size() != roomIds.size()) {
      throw new IllegalArgumentException("导入数据中包含不存在的房间，请重新解析后导入");
    }

    List<MeterReadingBatchService.MeterUpsertItem> toSave = new ArrayList<>();
    // 逐行校验，把问题行收集起来而不是整批失败。
    // 一批 50 行里因为 1 行读数倒退就全部退回，用户只能重做整张表。
    List<Map<String, Object>> failedRows = new ArrayList<>();

    for (ImportSuccessItem item : request.getRecords()) {
      String failReason = null;

      if (item.getWaterReading() == null || item.getWaterReading() < 0
          || item.getElectricReading() == null || item.getElectricReading() < 0) {
        failReason = "读数缺失或为负数";
      }

      java.time.LocalDate date = null;
      if (failReason == null) {
        try {
          date = java.time.LocalDate.parse(item.getRecordDate());
        } catch (Exception e) {
          failReason = "日期格式无效: " + item.getRecordDate();
        }
      }

      if (failReason != null) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roomId", item.getRoomId());
        row.put("recordDate", item.getRecordDate());
        row.put("reason", failReason);
        failedRows.add(row);
        continue;
      }

      toSave.add(new MeterReadingBatchService.MeterUpsertItem(
          item.getRoomId(),
          date,
          item.getWaterReading(),
          item.getElectricReading()));
    }

    // 读数倒退会跨行互相影响（后面的行依赖前面的行落库），
    // 因此仍然整批提交：只要有一条倒退就整体拒绝，但要把具体是哪个房间报出来。
    if (!toSave.isEmpty()) {
      meterReadingBatchService.upsertBatch(toSave);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", toSave.size());
    result.put("failCount", failedRows.size());
    result.put("failedRows", failedRows);
    result.put("message", failedRows.isEmpty()
        ? "导入成功"
        : String.format("导入 %d 条，%d 条被跳过", toSave.size(), failedRows.size()));
    return result;
  }

  /**
   * 按「上期读数 + 增量」批量生成抄表。
   *
   * <p>与 Excel 导入互补：导入要求精确读数，这里只给增量，
   * 适合先批量出一版、个别房间再修正的场景。
   *
   * <p>基线取「目标日期之前」的最后一条记录：补录历史月份时不会
   * 误用更晚的读数，从而避免生成倒退的读数。
   */
  @PostMapping("/generate-by-delta")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public Map<String, Object> generateByDelta(@RequestBody GenerateByDeltaRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("请求不能为空");
    }
    LocalDate date;
    try {
      date = LocalDate.parse(request.getRecordDate() == null ? "" : request.getRecordDate());
    } catch (Exception e) {
      throw new IllegalArgumentException("抄表日期格式应为 yyyy-MM-dd");
    }

    MeterReadingGenerateService.GenerateResult result = meterReadingGenerateService.generateByDelta(
        request.getRoomIds(),
        date,
        request.getWaterDelta(),
        request.getElectricDelta(),
        Boolean.TRUE.equals(request.getOverwrite()));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("recordDate", date.toString());
    body.put("created", result.getCreated());
    body.put("updated", result.getUpdated());
    body.put("skipped", result.getSkipped());
    body.put("affectedRoomIds", result.getAffectedRoomIds());
    return body;
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    MeterReading before = meterReadingRepository.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    String targetName = "房间#" + before.getRoomId() + " " + before.getRecordDate();
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "水电读数", "delete", "meter_reading", id, targetName, before);
    meterReadingRepository.deleteById(id);
    return ResponseEntity.ok(UndoResponse.of(snapshot));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  private void validateExcelFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("请上传Excel文件（.xlsx或.xls）");
    }
    String filename = file.getOriginalFilename();
    String lower = filename == null ? "" : filename.toLowerCase();
    if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
      throw new IllegalArgumentException("请上传Excel文件（.xlsx或.xls）");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException("文件大小不能超过10MB");
    }
  }

  private List<MeterReading> queryReadingsForExport(String date, String startDate, String endDate, Long buildingId,
      Long roomId) {
    List<MeterReading> readings;
    if (roomId != null) {
      readings = meterReadingRepository.findByRoomIdOrderByRecordDateDesc(roomId);
    } else if (date != null && !date.isBlank()) {
      readings = meterReadingRepository.findByRecordDate(LocalDate.parse(date));
    } else {
      readings = meterReadingRepository.findAll();
    }

    if (buildingId != null) {
      Set<Long> roomIds = roomRepository.findByBuildingId(buildingId).stream()
          .map(Room::getId)
          .collect(Collectors.toSet());
      readings = readings.stream().filter(r -> roomIds.contains(r.getRoomId())).toList();
    }

    LocalDate start = parseDateSafe(startDate);
    LocalDate end = parseDateSafe(endDate);
    if (start != null) {
      readings = readings.stream().filter(r -> !r.getRecordDate().isBefore(start)).toList();
    }
    if (end != null) {
      readings = readings.stream().filter(r -> !r.getRecordDate().isAfter(end)).toList();
    }

    return readings.stream()
        .sorted(Comparator.comparing(MeterReading::getRecordDate).reversed()
            .thenComparing(MeterReading::getRoomId, Comparator.nullsLast(Long::compareTo)))
        .toList();
  }

  private LocalDate parseDateSafe(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (Exception ignore) {
      return null;
    }
  }

  private String csvCell(Object value) {
    if (value == null) {
      return "";
    }
    String text = String.valueOf(value);
    if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
      return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    return text;
  }

  private Map<String, Integer> resolveHeaderColumns(Row headerRow) {
    Map<String, Integer> columnIndex = new HashMap<>();
    for (Cell cell : headerRow) {
      String header = cell == null ? "" : cell.toString();
      if (header != null) {
        String normalized = header.trim();
        if (!normalized.isEmpty()) {
          columnIndex.put(normalized, cell.getColumnIndex());
        }
      }
    }
    return columnIndex;
  }

  private boolean isBlankRow(Row row) {
    if (row == null) {
      return true;
    }
    for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
      Cell cell = row.getCell(c);
      if (cell != null && cell.getCellType() != CellType.BLANK && !getCellText(row, c).isBlank()) {
        return false;
      }
    }
    return true;
  }

  private String getCellText(Row row, Integer column) {
    if (row == null || column == null || column < 0) {
      return "";
    }
    Cell cell = row.getCell(column);
    if (cell == null) {
      return "";
    }
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue().trim();
      case NUMERIC -> {
        if (DateUtil.isCellDateFormatted(cell)) {
          yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        double value = cell.getNumericCellValue();
        if (Math.floor(value) == value) {
          yield String.valueOf((long) value);
        }
        yield String.valueOf(value);
      }
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> {
        try {
          yield String.valueOf(cell.getNumericCellValue());
        } catch (Exception ignore) {
          yield cell.getStringCellValue().trim();
        }
      }
      default -> "";
    };
  }

  private java.time.LocalDate parseDateCell(Cell cell) {
    if (cell == null) {
      return null;
    }
    try {
      if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
        return cell.getLocalDateTimeCellValue().toLocalDate();
      }
      String raw = getCellRawString(cell);
      if (raw.isBlank()) {
        return null;
      }
      String normalized = raw.trim().replace('/', '-').replace('.', '-');
      return java.time.LocalDate.parse(normalized);
    } catch (Exception e) {
      return null;
    }
  }

  private Double parseNumberCell(Cell cell) {
    if (cell == null) {
      return null;
    }
    try {
      if (cell.getCellType() == CellType.NUMERIC) {
        return cell.getNumericCellValue();
      }
      String raw = getCellRawString(cell);
      if (raw.isBlank()) {
        return null;
      }
      return Double.parseDouble(raw.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private String getCellRawString(Cell cell) {
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> {
        double value = cell.getNumericCellValue();
        if (Math.floor(value) == value) {
          yield String.valueOf((long) value);
        }
        yield String.valueOf(value);
      }
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> {
        try {
          yield String.valueOf(cell.getNumericCellValue());
        } catch (Exception ignore) {
          yield cell.getStringCellValue();
        }
      }
      default -> "";
    };
  }

  @lombok.Data
  public static class ImportConfirmRequest {
    private List<ImportSuccessItem> records;
  }

  @lombok.Data
  @lombok.Builder
  public static class ImportSuccessItem {
    private String sheetName;
    private Integer rowNumber;
    private Long buildingId;
    private String buildingName;
    private Long roomId;
    private String roomNumber;
    private String recordDate;
    private Double waterReading;
    private Double electricReading;
  }

  @lombok.Data
  @lombok.Builder
  public static class ImportErrorItem {
    private String sheetName;
    private Integer rowNumber;
    private String recordDate;
    private String roomNumber;
    private String errorReason;

    static ImportErrorItem of(String sheetName, Integer rowNumber, String recordDate, String roomNumber,
        String errorReason) {
      return ImportErrorItem.builder()
          .sheetName(sheetName)
          .rowNumber(rowNumber)
          .recordDate(recordDate)
          .roomNumber(roomNumber)
          .errorReason(errorReason)
          .build();
    }
  }

  /** 批量按增量生成抄表的请求体。 */
  public static class GenerateByDeltaRequest {
    private List<Long> roomIds;
    /** yyyy-MM-dd */
    private String recordDate;
    private Double waterDelta;
    private Double electricDelta;
    private Boolean overwrite;

    public List<Long> getRoomIds() {
      return roomIds;
    }

    public void setRoomIds(List<Long> roomIds) {
      this.roomIds = roomIds;
    }

    public String getRecordDate() {
      return recordDate;
    }

    public void setRecordDate(String recordDate) {
      this.recordDate = recordDate;
    }

    public Double getWaterDelta() {
      return waterDelta;
    }

    public void setWaterDelta(Double waterDelta) {
      this.waterDelta = waterDelta;
    }

    public Double getElectricDelta() {
      return electricDelta;
    }

    public void setElectricDelta(Double electricDelta) {
      this.electricDelta = electricDelta;
    }

    public Boolean getOverwrite() {
      return overwrite;
    }

    public void setOverwrite(Boolean overwrite) {
      this.overwrite = overwrite;
    }
  }
}
