package wyq.pocket.money.money.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.dto.AccountTotals;
import wyq.pocket.money.money.dto.BizTypeSum;
import wyq.pocket.money.money.dto.DirectionSum;
import wyq.pocket.money.money.dto.TransactionPageResponse;
import wyq.pocket.money.money.dto.TransactionRow;
import wyq.pocket.money.money.dto.UserDirectionSum;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 零花钱查询门面：流水分页 + 供 finance 模块使用的聚合入口
 * （M2 设计 §8.2 #2、§7.1；跨模块只经本 service，不直连 mapper）。
 */
@Component
public class MoneyQueryService {

    /** 默认页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大页大小（设计 §5.3：页长上限 50）。 */
    private static final int MAX_PAGE_SIZE = 50;

    private final MoneyTransactionMapper transactionMapper;

    private final MoneyAccountMapper accountMapper;

    private final FamilyAccessChecker familyAccessChecker;

    /**
     * 注入协作对象。
     *
     * @param transactionMapper   流水 Mapper
     * @param accountMapper       账户 Mapper
     * @param familyAccessChecker 数据级访问守卫
     */
    public MoneyQueryService(MoneyTransactionMapper transactionMapper,
                             MoneyAccountMapper accountMapper,
                             FamilyAccessChecker familyAccessChecker) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
        this.familyAccessChecker = familyAccessChecker;
    }

    /**
     * 流水分页查询（#2）：家庭内全透明读，可按成员 / 方向 / 类型 / 日期过滤。
     *
     * @param principal 当前登录主体
     * @param userId    可选：账户持有人过滤
     * @param direction 可选：方向过滤（IN / OUT）
     * @param bizType   可选：业务类型过滤
     * @param from      可选：起始日期（含）
     * @param to        可选：截止日期（含）
     * @param page      页码（从 1 起）
     * @param size      页大小（1–100，默认 20）
     * @return 分页结果
     */
    public TransactionPageResponse page(UserIdPrincipal principal, Long userId, String direction,
                                        String bizType, LocalDate from, LocalDate to,
                                        int page, int size) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        if (userId != null) {
            familyAccessChecker.requireMember(familyId, userId);
        }
        wyq.pocket.money.money.domain.TxDirection dir = parseEnum(
                wyq.pocket.money.money.domain.TxDirection.class, direction);
        wyq.pocket.money.money.domain.TxBizType biz = parseEnum(
                wyq.pocket.money.money.domain.TxBizType.class, bizType);
        Instant fromInstant = startOfBusinessDay(from);
        Instant toExclusive = startOfBusinessDay(to == null ? null : to.plusDays(1));
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        long total = transactionMapper.countPage(familyId, userId, dir, biz,
                fromInstant, toExclusive);
        List<TransactionRow> rows = transactionMapper.findPage(familyId, userId, dir, biz,
                fromInstant, toExclusive, safeSize, (safePage - 1) * safeSize);
        return new TransactionPageResponse(rows, total, safePage, safeSize);
    }

    /**
     * 起始时间后按方向聚合（看板 / 统计摘要）。
     *
     * @param familyId 家庭 ID
     * @param since    起始时间（含）
     * @return 方向合计列表
     */
    public List<DirectionSum> sumByDirectionSince(long familyId, Instant since) {
        return transactionMapper.sumByDirectionSince(familyId, since);
    }

    /**
     * 区间内按业务类型 + 方向聚合（收支报表）。
     *
     * @param familyId    家庭 ID
     * @param from        起始时间（含）
     * @param toExclusive 截止时间（不含）
     * @return 业务类型合计列表
     */
    public List<BizTypeSum> sumByBizType(long familyId, Instant from, Instant toExclusive) {
        return transactionMapper.sumByBizType(familyId, from, toExclusive);
    }

    /**
     * 区间内按用户 + 方向聚合（报表成员行）。
     *
     * @param familyId    家庭 ID
     * @param from        起始时间（含）
     * @param toExclusive 截止时间（不含）
     * @return 用户方向合计列表
     */
    public List<UserDirectionSum> sumByUserAndDirection(long familyId, Instant from,
                                                        Instant toExclusive) {
        return transactionMapper.sumByUserAndDirection(familyId, from, toExclusive);
    }

    /**
     * 家庭累计收支（统计摘要）。
     *
     * @param familyId 家庭 ID
     * @return 累计收入 / 支出
     */
    public AccountTotals accountTotals(long familyId) {
        return accountMapper.sumTotalsByFamily(familyId);
    }

    /**
     * 家庭总余额（统计摘要）。
     *
     * @param familyId 家庭 ID
     * @return 总余额
     */
    public BigDecimal totalBalance(long familyId) {
        return accountMapper.sumBalanceByFamily(familyId);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "非法枚举值: " + value);
        }
    }

    private Instant startOfBusinessDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
    }

    private int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
