package com.house.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常 → HTTP 状态码映射。
 *
 * <p>此前只有「业务异常 400 / 权限 403 / 其他 500」三条分支，
 * 导致 405、413、404 这类客户端错误全部被当成 500 返回，
 * 前端拦截器里的 413 分支永远走不到。
 *
 * <p>响应体统一为 {@code {"message": "...", "status": 4xx}}，
 * 与各控制器手写的 Map 响应保持一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
  public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "forbidden" : ex.getMessage();
    return build(HttpStatus.FORBIDDEN, message);
  }

  /** 请求方法不对（如对 POST 接口发 GET）：405 而不是 500。 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
    return build(HttpStatus.METHOD_NOT_ALLOWED, "接口不支持 " + ex.getMethod() + " 请求");
  }

  /** Content-Type 不匹配：415。 */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
    return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的 Content-Type：" + ex.getContentType());
  }

  /** 上传文件超出限制：413。前端拦截器依赖该状态码给出提示。 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
    return build(HttpStatus.PAYLOAD_TOO_LARGE, "文件过大，请压缩后重试");
  }

  /** 请求体缺失或 JSON 解析失败：400。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return build(HttpStatus.BAD_REQUEST, "请求体格式有误，请检查后重试");
  }

  /** 缺少必填查询参数：400。 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
    return build(HttpStatus.BAD_REQUEST, "缺少必填参数：" + ex.getParameterName());
  }

  /** 参数类型不匹配（如 id 传了非数字）：400。 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return build(HttpStatus.BAD_REQUEST, "参数格式有误：" + ex.getName());
  }

  /** 方法参数校验失败（配合 @Valid 使用）：400。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(err -> err.getField() + " " + err.getDefaultMessage())
        .orElse("请求参数校验失败");
    return build(HttpStatus.BAD_REQUEST, message);
  }

  /** 路径不存在：404 而不是 500。 */
  @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
  public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
    return build(HttpStatus.NOT_FOUND, "接口不存在");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnhandled(Exception ex) {
    log.error("Unhandled server exception", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "server error");
  }

  private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
    Map<String, Object> body = new HashMap<>();
    body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
    body.put("status", status.value());
    return ResponseEntity.status(status).body(body);
  }
}
