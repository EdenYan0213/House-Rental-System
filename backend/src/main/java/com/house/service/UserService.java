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
        if (user.getFirstLogin() == null) {
            user.setFirstLogin(true);
        }
        if (user.getShowContextTips() == null) {
            user.setShowContextTips(true);
        }
        if (user.getShowTaskCard() == null) {
            user.setShowTaskCard(true);
        }
        userRepository.insert(user);
        return user;
    }

    /**
     * 更新用户。
     *
     * <p>密码只在「传入的是明文」时才加密。{@code updateProfile} 这类
     * 场景传进来的 user 是从库里查出来的，password 已是 BCrypt 哈希，
     * 再加密一次会把密码彻底改坏（用户下次登录必然失败）。
     */
    public User updateUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isEmpty() && !isBcryptHash(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userRepository.updateById(user);
        return user;
    }

    /** BCrypt 哈希固定以 $2a$ / $2b$ / $2y$ 开头且长度 60。 */
    private boolean isBcryptHash(String value) {
        return value != null
            && value.length() == 60
            && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    /**
     * 更新个人资料（姓名、手机、邮箱、主题、引导标记）。
     *
     * <p>与 {@link #updateUser} 的区别：**显式不读写密码字段**。
     * 改资料和改密码是两件事，混在一起很容易把已加密的密码再加密一次
     * （这个 bug 真实发生过，导致用户改完头像就再也登录不了）。
     */
    public User updateProfileInfo(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 保留库里的密码原值，绝不把入参的 password 写回
        User existing = userRepository.selectById(user.getId());
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setPassword(existing.getPassword());
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
