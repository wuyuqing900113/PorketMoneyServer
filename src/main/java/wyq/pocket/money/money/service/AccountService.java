package wyq.pocket.money.money.service;

import org.springframework.stereotype.Component;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.dto.MoneyErrorCode;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;

/**
 * 账户服务：惰性开户与账户查询（M2 设计 §4.1）。
 */
@Component
public class AccountService {

    private final MoneyAccountMapper accountMapper;

    /**
     * 注入账户 Mapper。
     *
     * @param accountMapper 账户 Mapper
     */
    public AccountService(MoneyAccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 查询账户（可空）。
     *
     * @param userId 持有人用户 ID
     * @return 账户，未开户返回 null
     */
    public MoneyAccount find(long userId) {
        return accountMapper.findByUserId(userId);
    }

    /**
     * 要求账户存在（出账前置：未开户即无余额）。
     *
     * @param userId 持有人用户 ID
     * @return 账户
     * @throws BusinessException 300001 余额不足（未开户）
     */
    public MoneyAccount requireAccount(long userId) {
        MoneyAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(MoneyErrorCode.BALANCE_NOT_ENOUGH);
        }
        return account;
    }

    /**
     * 惰性开户：无账户则创建（并发安全，ON CONFLICT DO NOTHING）。
     *
     * @param familyId 家庭 ID
     * @param userId   持有人用户 ID
     * @return 账户（必非空）
     */
    public MoneyAccount getOrOpen(long familyId, long userId) {
        MoneyAccount account = accountMapper.findByUserId(userId);
        if (account != null) {
            return account;
        }
        MoneyAccount fresh = new MoneyAccount();
        fresh.setFamilyId(familyId);
        fresh.setUserId(userId);
        accountMapper.insertIgnoreConflict(fresh);
        return accountMapper.findByUserId(userId);
    }
}
