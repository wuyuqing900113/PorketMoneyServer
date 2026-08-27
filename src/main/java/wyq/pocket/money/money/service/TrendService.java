package wyq.pocket.money.money.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.TrendPoint;
import wyq.pocket.money.money.dto.TrendResponse;
import wyq.pocket.money.money.dto.TxWindowRow;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 趋势服务（M2 设计 §8.2 #3）：流水实时聚合，
 * 日粒度 30 天 / 周粒度（周一至周日）12 周，含期末余额推演。
 */
@Component
public class TrendService {

    /** 日粒度桶数。 */
    private static final int DAY_BUCKETS = 30;

    /** 周粒度桶数。 */
    private static final int WEEK_BUCKETS = 12;

    /** 每周天数。 */
    private static final int DAYS_PER_WEEK = 7;

    /** 范围参数：家庭。 */
    private static final String SCOPE_FAMILY = "FAMILY";

    /** 范围参数：个人。 */
    private static final String SCOPE_USER = "USER";

    /** 粒度参数：周。 */
    private static final String GRANULARITY_WEEK = "WEEK";

    private final MoneyTransactionMapper transactionMapper;

    private final MoneyAccountMapper accountMapper;

    private final FamilyAccessChecker familyAccessChecker;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param transactionMapper   流水 Mapper
     * @param accountMapper       账户 Mapper
     * @param familyAccessChecker 数据级访问守卫
     * @param clock               时钟（业务时区）
     */
    public TrendService(MoneyTransactionMapper transactionMapper,
                        MoneyAccountMapper accountMapper,
                        FamilyAccessChecker familyAccessChecker, Clock clock) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
        this.familyAccessChecker = familyAccessChecker;
        this.clock = clock;
    }

    /**
     * 趋势查询（#3）。
     *
     * @param principal   当前登录主体
     * @param scope       FAMILY（默认）/ USER
     * @param userId      scope=USER 时可指定成员，缺省为自己
     * @param granularity DAY / WEEK（默认 WEEK）
     * @return 趋势响应
     */
    public TrendResponse trend(UserIdPrincipal principal, String scope, Long userId,
                               String granularity) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        boolean userScope = SCOPE_USER.equalsIgnoreCase(scope);
        Long targetUserId = resolveTargetUser(userScope, userId, principal);
        boolean daily = !GRANULARITY_WEEK.equalsIgnoreCase(granularity);
        int bucketCount = daily ? DAY_BUCKETS : WEEK_BUCKETS;
        LocalDate windowStart = windowStart(daily, bucketCount);
        Instant since = windowStart.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
        List<TxWindowRow> rows = transactionMapper.findWindow(familyId, targetUserId, since);
        BigDecimal startBalance = startBalance(userScope, familyId, targetUserId, rows);
        List<TrendPoint> series = buildSeries(rows, windowStart, bucketCount, daily, startBalance);
        return new TrendResponse(daily ? "DAY" : GRANULARITY_WEEK,
                userScope ? SCOPE_USER : SCOPE_FAMILY, targetUserId, series);
    }

    private Long resolveTargetUser(boolean userScope, Long userId, UserIdPrincipal principal) {
        if (!userScope) {
            return null;
        }
        long targetId = userId != null ? userId : principal.userId();
        // 目标即请求主体时，trend 入口的成员校验已覆盖，避免重复查库
        if (targetId != principal.userId()) {
            familyAccessChecker.requireMember(principal.familyId(), targetId);
        }
        return targetId;
    }

    private LocalDate windowStart(boolean daily, int bucketCount) {
        LocalDate today = LocalDate.now(clock);
        if (daily) {
            return today.minusDays(bucketCount - 1L);
        }
        return today.with(DayOfWeek.MONDAY).minusWeeks(bucketCount - 1L);
    }

    /**
     * 推导窗口起点余额 B0（M2 设计 §6.2）：当前余额 − 窗口净流入。
     *
     * <p>惰性开户（D10）下成员无账户即无余额，起点余额直接记 0，
     * 不做窗口净额回减。
     *
     * @param userScope    是否成员范围
     * @param familyId     家庭 ID
     * @param targetUserId 目标用户 ID（仅成员范围使用）
     * @param rows         窗口内流水
     * @return 窗口起点余额 B0
     */
    private BigDecimal startBalance(boolean userScope, long familyId, Long targetUserId,
                                    List<TxWindowRow> rows) {
        if (!userScope) {
            return accountMapper.sumBalanceByFamily(familyId).subtract(windowNet(rows));
        }
        MoneyAccount account = accountMapper.findByUserId(targetUserId);
        return account == null
                ? BigDecimal.ZERO
                : account.getBalance().subtract(windowNet(rows));
    }

    private BigDecimal windowNet(List<TxWindowRow> rows) {
        BigDecimal net = BigDecimal.ZERO;
        for (TxWindowRow row : rows) {
            net = row.direction() == TxDirection.IN
                    ? net.add(row.amount())
                    : net.subtract(row.amount());
        }
        return net;
    }

    private List<TrendPoint> buildSeries(List<TxWindowRow> rows, LocalDate windowStart,
                                         int bucketCount, boolean daily, BigDecimal startBalance) {
        Map<Integer, BigDecimal> incomeByBucket = new HashMap<>();
        Map<Integer, BigDecimal> expenseByBucket = new HashMap<>();
        collectByBucket(rows, windowStart, daily, incomeByBucket, expenseByBucket);
        List<TrendPoint> series = new ArrayList<>();
        BigDecimal running = startBalance;
        for (int index = 0; index < bucketCount; index++) {
            BigDecimal income = incomeByBucket.getOrDefault(index, BigDecimal.ZERO);
            BigDecimal expense = expenseByBucket.getOrDefault(index, BigDecimal.ZERO);
            running = running.add(income).subtract(expense);
            series.add(pointOf(windowStart, index, daily, running, income, expense));
        }
        return series;
    }

    private void collectByBucket(List<TxWindowRow> rows, LocalDate windowStart, boolean daily,
                                 Map<Integer, BigDecimal> incomeByBucket,
                                 Map<Integer, BigDecimal> expenseByBucket) {
        for (TxWindowRow row : rows) {
            int index = bucketIndex(row.createdAt(), windowStart, daily);
            Map<Integer, BigDecimal> target = row.direction() == TxDirection.IN
                    ? incomeByBucket
                    : expenseByBucket;
            target.merge(index, row.amount(), BigDecimal::add);
        }
    }

    private int bucketIndex(Instant createdAt, LocalDate windowStart, boolean daily) {
        LocalDate date = createdAt.atZone(ClockConfig.BUSINESS_ZONE).toLocalDate();
        long days = ChronoUnit.DAYS.between(windowStart, date);
        return daily ? (int) days : (int) (days / DAYS_PER_WEEK);
    }

    private TrendPoint pointOf(LocalDate windowStart, int index, boolean daily,
                               BigDecimal running, BigDecimal income, BigDecimal expense) {
        LocalDate start = daily
                ? windowStart.plusDays(index)
                : windowStart.plusWeeks(index);
        LocalDate end = daily ? start : start.plusDays(DAYS_PER_WEEK - 1L);
        return new TrendPoint(start.toString(), start, end, running, income, expense);
    }
}
