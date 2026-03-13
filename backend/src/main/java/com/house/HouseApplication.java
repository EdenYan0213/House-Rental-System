package com.house;

import com.house.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = "com.house.model")
@EnableScheduling
public class HouseApplication {
  public static void main(String[] args) {
    SpringApplication.run(HouseApplication.class, args);
  }

  @Bean
  public CommandLineRunner init(UserService userService) {
    return args -> {
      try {
        userService.initDefaultAdmin();
      } catch (Exception e) {
        // ignore
      }
    };
  }
}
