package com.house.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.house.model.User;
import com.house.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User findByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userRepository.selectOne(wrapper);
    }

    public User findById(Long id) {
        return userRepository.selectById(id);
    }

    public List<User> findAll() {
        return userRepository.selectList(null);
    }

    public IPage<User> findAll(int page, int size) {
        Page<User> pageObj = new Page<>(page, size);
        return userRepository.selectPage(pageObj, null);
    }

    public IPage<User> searchUsers(String username, String realName, String role, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.like(User::getUsername, username);
        }
        if (realName != null && !realName.isEmpty()) {
            wrapper.like(User::getRealName, realName);
        }
        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, com.house.model.Role.valueOf(role));
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> pageObj = new Page<>(page, size);
        return userRepository.selectPage(pageObj, wrapper);
    }

    public User createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(true);
        if (user.getThemePreference() == null || user.getThemePreference().isBlank()) {
            user.setThemePreference("system");
        }
        userRepository.insert(user);
        return user;
    }

    public User updateUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userRepository.updateById(user);
        return user;
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public void enableUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setEnabled(true);
        userRepository.updateById(user);
    }

    public void disableUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setEnabled(false);
        userRepository.updateById(user);
    }

    public void resetPassword(Long id, String newPassword) {
        User user = new User();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.updateById(user);
    }

    public void updateLastLoginTime(Long id) {
        User user = new User();
        user.setId(id);
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.updateById(user);
    }

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public void initDefaultAdmin() {
        User admin = findByUsername("admin");
        if (admin == null) {
            User newAdmin = User.builder()
                    .username("admin")
                    .password("admin123")
                    .realName("系统管理员")
                    .role(com.house.model.Role.SUPER_ADMIN)
                    .enabled(true)
                    .build();
            createUser(newAdmin);
        }
    }
}
