package com.house.controller;

import com.house.model.FeeItem;
import com.house.repo.FeeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 费用项部分更新测试。
 *
 * <p>守护一个真实 bug：费用项列表上的"启用/停用"开关只提交 `{enabled}`，
 * 而后端原先直接 `save(incoming)`，导致 name/unitPrice/unit/category
 * 被一起覆盖成 null——用户点一下开关就把整条费用配置清空了。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("费用项更新")
class FeeItemUpdateTest {

  @Mock
  private FeeItemRepository feeItemRepository;

  private FeeItemController controller;

  private FeeItem existing() {
    FeeItem item = new FeeItem();
    item.setId(1L);
    item.setName("水费");
    item.setUnitPrice(4.5);
    item.setUnit("元/吨");
    item.setCategory("水");
    item.setEnabled(true);
    return item;
  }

  @BeforeEach
  void setUp() {
    controller = new FeeItemController(feeItemRepository);
    when(feeItemRepository.findById(1L)).thenReturn(Optional.of(existing()));
    when(feeItemRepository.save(any(FeeItem.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  @DisplayName("只传 enabled 时不清空其他字段")
  void partialUpdateKeepsOtherFields() {
    FeeItem patch = new FeeItem();
    patch.setEnabled(false);

    ResponseEntity<FeeItem> response = controller.update(1L, patch);
    FeeItem saved = response.getBody();

    assertNotNull(saved);
    assertEquals("水费", saved.getName(), "名称不应被清空");
    assertEquals(4.5, saved.getUnitPrice(), "单价不应被清空");
    assertEquals("元/吨", saved.getUnit(), "单位不应被清空");
    assertEquals("水", saved.getCategory(), "分类不应被清空");
    assertEquals(false, saved.getEnabled(), "enabled 应被更新");
  }

  @Test
  @DisplayName("编辑弹窗传全字段时全部生效")
  void fullUpdateAppliesAll() {
    FeeItem patch = new FeeItem();
    patch.setName("自来水费");
    patch.setUnitPrice(5.0);
    patch.setUnit("元/立方米");
    patch.setCategory("水");

    FeeItem saved = controller.update(1L, patch).getBody();

    assertNotNull(saved);
    assertEquals("自来水费", saved.getName());
    assertEquals(5.0, saved.getUnitPrice());
    assertEquals("元/立方米", saved.getUnit());
    // 未传的 enabled 保持原值
    assertEquals(true, saved.getEnabled());
  }

  @Test
  @DisplayName("不存在时返回 404")
  void notFound() {
    when(feeItemRepository.findById(99L)).thenReturn(Optional.empty());
    assertEquals(404, controller.update(99L, new FeeItem()).getStatusCode().value());
  }
}
