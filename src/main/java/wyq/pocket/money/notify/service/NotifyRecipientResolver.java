package wyq.pocket.money.notify.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import wyq.pocket.money.user.service.FamilyService;

/**
 * 通知接收人解析（M5 设计 §5.3）。
 *
 * <p>账务变动（TX_IN/TX_OUT）→ 账户主人；余额不足 / 规则到期 →
 * 账户主人 + 家庭全部家长（经 {@link FamilyService#listParentUserIds} 只读取数，
 * 不触碰 user.mapper）。家长列表顺序稳定（成员加入顺序），去重。
 */
@Component
public class NotifyRecipientResolver {

    private final FamilyService familyService;

    /**
     * 注入家庭服务。
     *
     * @param familyService 家庭服务（家长列表只读解析）
     */
    public NotifyRecipientResolver(FamilyService familyService) {
        this.familyService = familyService;
    }

    /**
     * 账务变动接收人 = 账户主人本人。
     *
     * @param userId 账户主人用户 ID
     * @return 单元素集合
     */
    public Set<Long> txRecipients(long userId) {
        return Set.of(userId);
    }

    /**
     * 余额不足 / 规则到期接收人 = 账户主人 + 家庭全部家长（去重，主人优先）。
     *
     * @param familyId 家庭 ID
     * @param userId   账户主人用户 ID
     * @return 接收人集合
     */
    public Set<Long> ownerAndParents(long familyId, long userId) {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(userId);
        recipients.addAll(familyService.listParentUserIds(familyId));
        return recipients;
    }
}
