package wyq.pocket.money.money.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.DashboardResponse;
import wyq.pocket.money.money.dto.DirectionSum;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 看板服务（M2 设计 §8.2 #1）：总余额、本周 / 本月收支、全员余额。
 */
@Component
public class DashboardService {

    private final MoneyAccountMapper accountMapper;

    private final MoneyTransactionMapper transactionMapper;

    private final FamilyAccessChecker familyAccessChecker;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param accountMapper       账户 Mapper
     * @param transactionMapper   流水 Mapper
     * @param familyAccessChecker 数据级访问守卫
     * @param clock               时钟（业务时区）
     */
    public DashboardService(MoneyAccountMapper accountMapper,
                            MoneyTransactionMapper transactionMapper,
                            FamilyAccessChecker familyAccessChecker, Clock clock) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.familyAccessChecker = familyAccessChecker;
        this.clock = clock;
    }

    /**
     * 看板数据（#1）。
     *
     * @param principal 当前登录主体
     * @return 看板响应
     */
    public DashboardResponse getDashboard(UserIdPrincipal principal) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        LocalDate today = LocalDate.now(clock);
        Instant weekStart = startOfDay(today.with(DayOfWeek.MONDAY));
        Instant monthStart = startOfDay(today.withDayOfMonth(1));
        Map<TxDirection, BigDecimal> week = sumSince(familyId, weekStart);
        Map<TxDirection, BigDecimal> month = sumSince(familyId, monthStart);
        return new DashboardResponse(accountMapper.sumBalanceByFamily(familyId),
                week.get(TxDirection.IN), week.get(TxDirection.OUT),
                month.get(TxDirection.IN), month.get(TxDirection.OUT),
                accountMapper.findMemberBalances(familyId));
    }

    private Map<TxDirection, BigDecimal> sumSince(long familyId, Instant since) {
        Map<TxDirection, BigDecimal> sums = new HashMap<>();
        sums.put(TxDirection.IN, BigDecimal.ZERO);
        sums.put(TxDirection.OUT, BigDecimal.ZERO);
        List<DirectionSum> rows = transactionMapper.sumByDirectionSince(familyId, since);
        for (DirectionSum row : rows) {
            sums.put(row.direction(), row.total());
        }
        return sums;
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
    }
}
