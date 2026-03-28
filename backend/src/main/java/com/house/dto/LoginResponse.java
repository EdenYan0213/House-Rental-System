package com.house.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String username;
    private String realName;
    private String role;
    private Long userId;
    private String email;
    private String themePreference;
    private Boolean firstLogin;
    private Boolean showContextTips;
    private Boolean showTaskCard;
}
