package com.house.controller;

import com.house.model.Building;
import com.house.model.Room;
import com.house.repo.BuildingRepository;
import com.house.service.QrCodeService;
import com.house.service.RoomService;
import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/qr")
public class QrCodeController {

  @Value("${app.qr.base-url:http://localhost:5173}")
  private String qrBaseUrl;

  private final RoomService roomService;
  private final BuildingRepository buildingRepository;
  private final QrCodeService qrCodeService;

  public QrCodeController(RoomService roomService, BuildingRepository buildingRepository, QrCodeService qrCodeService) {
    this.roomService = roomService;
    this.buildingRepository = buildingRepository;
    this.qrCodeService = qrCodeService;
  }

  @GetMapping("/rooms/{roomId}/link")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> roomQrLink(@PathVariable Long roomId, HttpServletRequest request) {
    Room room = roomService.findById(roomId).orElse(null);
    if (room == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(Map.of(
        "roomId", roomId,
        "link", buildQrTargetUrl(roomId, request)));
  }

  @GetMapping(value = "/rooms/{roomId}/png", produces = MediaType.IMAGE_PNG_VALUE)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<byte[]> roomQrPng(@PathVariable Long roomId,
      @RequestParam(defaultValue = "false") boolean showLogo,
      HttpServletRequest request) {
    Room room = roomService.findById(roomId).orElse(null);
    if (room == null) {
      return ResponseEntity.notFound().build();
    }
    String roomLabel = buildRoomLabel(room);
    byte[] png = qrCodeService.generateRoomQrPng(roomLabel, buildQrTargetUrl(roomId, request), showLogo);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=room-" + roomId + "-qr.png")
        .contentType(MediaType.IMAGE_PNG)
        .body(png);
  }

  @PostMapping("/rooms/batch-pdf")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<byte[]> exportBatchPdf(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    List<Number> roomIdRaw = (List<Number>) payload.get("roomIds");
    if (roomIdRaw == null || roomIdRaw.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    List<Long> roomIds = roomIdRaw.stream().map(Number::longValue).toList();
    String paperSize = payload.getOrDefault("paperSize", "A4").toString();
    int qrPerPage = payload.get("qrPerPage") == null ? 15 : ((Number) payload.get("qrPerPage")).intValue();
    boolean showLogo = payload.get("showLogo") != null && Boolean.TRUE.equals(payload.get("showLogo"));

    List<Room> rooms = roomService.listByIds(roomIds);
    if (rooms.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      Rectangle pageSize = "A5".equalsIgnoreCase(paperSize) ? PageSize.A5 : PageSize.A4;
      Document document = new Document(pageSize, 18, 18, 20, 20);
      PdfWriter.getInstance(document, outputStream);
      document.open();

      int columns = resolveColumns(qrPerPage);
      PdfPTable table = new PdfPTable(columns);
      table.setWidthPercentage(100);
      int cellsInCurrentTable = 0;

      int cellCount = 0;
      for (Room room : rooms) {
        String roomLabel = buildRoomLabel(room);
        byte[] qrPng = qrCodeService.generateRoomQrPng(roomLabel, buildQrTargetUrl(room.getId(), request), showLogo);
        Image qrImage = Image.getInstance(qrPng);
        qrImage.scaleToFit(150, 180);

        PdfPCell cell = new PdfPCell();
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(8f);
        cell.setBorder(Rectangle.BOX);
        cell.addElement(qrImage);
        table.addCell(cell);
        cellCount++;
        cellsInCurrentTable++;

        if (cellCount % qrPerPage == 0) {
          fillEmptyCells(table, columns, cellsInCurrentTable);
          document.add(table);
          document.newPage();
          table = new PdfPTable(columns);
          table.setWidthPercentage(100);
          cellsInCurrentTable = 0;
        }
      }

      fillEmptyCells(table, columns, cellsInCurrentTable);
      document.add(table);
      document.close();
    } catch (Exception e) {
      throw new RuntimeException("导出二维码PDF失败: " + e.getMessage(), e);
    }

    String filename = buildFilenameFromRooms(rooms) + "_" +
        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .contentType(MediaType.APPLICATION_PDF)
        .body(outputStream.toByteArray());
  }

  private String buildQrTargetUrl(Long roomId, HttpServletRequest request) {
    return qrBaseUrl + "/qr/room/" + roomId;
  }

  private int resolveColumns(int qrPerPage) {
    if (qrPerPage <= 4) {
      return 2;
    }
    if (qrPerPage <= 8) {
      return 4;
    }
    return 3;
  }

  private String buildRoomLabel(Room room) {
    String buildingName = buildingRepository.findById(room.getBuildingId())
        .map(Building::getName)
        .orElse("-");
    return buildingName + " " + room.getRoomNumber();
  }

  private void fillEmptyCells(PdfPTable table, int columns, int currentCells) {
    int remain = currentCells % columns;
    if (remain == 0) {
      return;
    }
    int need = columns - remain;
    for (int i = 0; i < need; i++) {
      PdfPCell empty = new PdfPCell();
      empty.setBorder(Rectangle.NO_BORDER);
      table.addCell(empty);
    }
  }

  private String buildFilenameFromRooms(List<Room> rooms) {
    Set<Long> buildingIds = new HashSet<>();
    for (Room room : rooms) {
      buildingIds.add(room.getBuildingId());
    }
    if (buildingIds.size() == 1) {
      Long buildingId = buildingIds.iterator().next();
      String name = buildingRepository.findById(buildingId).map(Building::getName).orElse("房间");
      return name + "二维码";
    }
    return "房间二维码";
  }
}
