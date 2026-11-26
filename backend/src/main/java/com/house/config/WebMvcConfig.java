package com.house.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>此前这里把 `/uploads/**` 映射到本地磁盘目录 `app.file.upload-dir`。
 * 文件改为存数据库（`image_blob`，经 `/api/images/{id}` 与 `/api/files/{name}` 读取）后，
 * 该静态映射不再需要，已移除——避免部署时仍被诱导去准备持久磁盘。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
