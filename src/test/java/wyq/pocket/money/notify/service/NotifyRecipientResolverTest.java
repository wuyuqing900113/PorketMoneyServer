package wyq.pocket.money.notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.user.service.FamilyService;

/**
 * 通知接收人解析测试（M5 设计 §5.3）：账务变动 → 账户主人；
 * 余额不足 / 规则到期 → 主人 + 家长去重，家长列表来自 FamilyService（mock）。
 */
class NotifyRecipientResolverTest {

    private final FamilyService familyService = mock(FamilyService.class);

    private final NotifyRecipientResolver resolver = new NotifyRecipientResolver(familyService);

    @Test
    void txRecipientsShouldBeOwnerOnly() {
        assertThat(resolver.txRecipients(42L)).containsExactly(42L);
    }

    @Test
    void ownerAndParentsShouldDeduplicateAndPrioritizeOwner() {
        when(familyService.listParentUserIds(10L)).thenReturn(List.of(99L, 42L, 88L));

        Set<Long> recipients = resolver.ownerAndParents(10L, 42L);

        assertThat(recipients).containsExactly(42L, 99L, 88L);
    }

    @Test
    void ownerAndParentsShouldReturnOwnerOnlyWhenNoParents() {
        when(familyService.listParentUserIds(10L)).thenReturn(List.of());

        assertThat(resolver.ownerAndParents(10L, 42L)).containsExactly(42L);
    }
}
