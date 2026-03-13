package com.house.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.house.model.Room;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

@Service
public class QrCodeService {

  private static final int QUIET_ZONE = 1;

  public byte[] generateRoomQrPng(String label, String qrText, boolean showLogo) {
    try {
      BufferedImage qrImage = createQrImage(qrText, 420, showLogo);
      BufferedImage labeled = renderLabeledImage(qrImage, label);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ImageIO.write(labeled, "png", outputStream);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("生成二维码失败: " + e.getMessage(), e);
    }
  }

  public BufferedImage createQrImage(String content, int size, boolean showLogo) throws WriterException {
    QRCodeWriter writer = new QRCodeWriter();
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
    hints.put(EncodeHintType.MARGIN, QUIET_ZONE);

    BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
      }
    }

    if (showLogo) {
      addCenterLogo(image);
    }
    return image;
  }

  public BufferedImage renderLabeledImage(BufferedImage qrImage, String label) {
    int width = qrImage.getWidth();
    int textAreaHeight = 56;
    BufferedImage result = new BufferedImage(width, qrImage.getHeight() + textAreaHeight, BufferedImage.TYPE_INT_RGB);

    Graphics2D g2 = result.createGraphics();
    g2.setColor(Color.WHITE);
    g2.fillRect(0, 0, result.getWidth(), result.getHeight());
    g2.drawImage(qrImage, 0, 0, null);

    g2.setColor(new Color(32, 32, 32));
    g2.setFont(new Font("SansSerif", Font.BOLD, 24));
    FontMetrics metrics = g2.getFontMetrics();
    int x = Math.max(8, (width - metrics.stringWidth(label)) / 2);
    int y = qrImage.getHeight() + 36;
    g2.drawString(label, x, y);
    g2.dispose();
    return result;
  }

  private void addCenterLogo(BufferedImage qrImage) {
    try (InputStream logoStream = getClass().getClassLoader().getResourceAsStream("imgs/logo.png")) {
      if (logoStream == null) {
        return;
      }
      BufferedImage logo = ImageIO.read(logoStream);
      if (logo == null) {
        return;
      }

      int qrSize = qrImage.getWidth();
      int logoSize = qrSize / 5;
      int x = (qrSize - logoSize) / 2;
      int y = (qrSize - logoSize) / 2;

      Graphics2D g2 = qrImage.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(Color.WHITE);
      Shape roundRect = new RoundRectangle2D.Float(x - 6, y - 6, logoSize + 12, logoSize + 12, 20, 20);
      g2.fill(roundRect);
      g2.drawImage(logo, x, y, logoSize, logoSize, null);
      g2.dispose();
    } catch (IOException ignored) {
      // Ignore logo errors to keep QR generation available.
    }
  }
}
