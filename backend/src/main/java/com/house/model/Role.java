package com.house.model;

public enum Role {
    SUPER_ADMIN("超级管理员"),
    ADMIN("管理员"),
    USER("普通用户");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
