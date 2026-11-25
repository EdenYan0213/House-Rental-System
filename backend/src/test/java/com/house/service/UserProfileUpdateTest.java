package com.house.service;

import com.house.model.User;
import com.house.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 用户资料与密码更新的边界测试。
 *
 * <p>这里守护的是一个真实发生过的线上事故：改个人资料时把已经加密的密码
 * 又加密了一遍，导致用户「点完头像就再也登录不了」。密码是唯一不能出错的字段，
 * 所以单独用测试锁死行为。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("用户资料更新")
class UserProfileUpdateTest {

  @Mock
  private UserRepository userRepository;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  private UserService userService;

  private final String existingHash = new BCryptPasswordEncoder().encode("admin123");

  @BeforeEach
  void setUp() {
    userService = new UserService();
    // 字段注入：直接反射塞入，避免为测试改生产代码
    org.springframework.test.util.ReflectionTestUtils.setField(userService, "userRepository", userRepository);
    org.springframework.test.util.ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);
  }

  private User dbUser() {
    return User.builder()
        .id(3L)
        .username("admin")
        .realName("系统管理员")
        .password(existingHash)
        .firstLogin(false)
        .build();
  }

  @Test
  @DisplayName("updateProfileInfo 不会修改已加密的密码")
  void updateProfileInfoKeepsPassword() {
    when(userRepository.selectById(3L)).thenReturn(dbUser());

    User incoming = dbUser();
    incoming.setPassword(null);        // 前端不会传密码
    incoming.setRealName("新名字");
    incoming.setFirstLogin(false);

    userService.updateProfileInfo(incoming);

    // 关键断言：写回库里的密码必须是原来的哈希
    assertEquals(existingHash, incoming.getPassword(),
        "改资料不能改动密码，否则用户会被锁在门外");
    verify(userRepository).updateById(incoming);
  }

  @Test
  @DisplayName("updateProfileInfo 即使被传了新密码也不会覆盖")
  void updateProfileInfoIgnoresIncomingPassword() {
    when(userRepository.selectById(3L)).thenReturn(dbUser());

    User incoming = dbUser();
    incoming.setPassword("明文的新密码");   // 恶意或误传
    incoming.setFirstLogin(false);

    userService.updateProfileInfo(incoming);

    assertEquals(existingHash, incoming.getPassword(),
        "资料接口不应具备改密码的能力，改密码必须走 changePassword");
  }

  @Test
  @DisplayName("updateUser 传入明文时正常加密（改密码路径）")
  void updateUserEncodesPlainText() {
    User incoming = User.builder().id(3L).username("admin").password("newpass123").build();

    userService.updateUser(incoming);

    assertNotEquals("newpass123", incoming.getPassword(), "明文必须被加密");
    assertTrue(incoming.getPassword().startsWith("$2"), "应产出 BCrypt 哈希");
    assertTrue(passwordEncoder.matches("newpass123", incoming.getPassword()),
        "新密码应能通过校验");
  }

  @Test
  @DisplayName("updateUser 传入 BCrypt 哈希时不再二次加密")
  void updateUserDoesNotReencodeHash() {
    User incoming = User.builder().id(3L).username("admin").password(existingHash).build();

    userService.updateUser(incoming);

    assertEquals(existingHash, incoming.getPassword(),
        "已经是哈希的密码不能再加密一次");
    assertTrue(passwordEncoder.matches("admin123", incoming.getPassword()),
        "原密码仍应可用");
  }

  @Test
  @DisplayName("updateProfileInfo 用户不存在时抛出明确错误")
  void updateProfileInfoMissingUser() {
    when(userRepository.selectById(any())).thenReturn(null);

    User incoming = User.builder().id(999L).username("ghost").build();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> userService.updateProfileInfo(incoming));
    assertTrue(ex.getMessage().contains("用户不存在"));
  }
}
