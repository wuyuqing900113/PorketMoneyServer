package wyq.pocket.money.finance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.finance.dto.FinanceErrorCode;
import wyq.pocket.money.finance.dto.IncomeExpenseReportResponse;
import wyq.pocket.money.finance.dto.MemberReportRow;
import wyq.pocket.money.finance.dto.StatisticsSummaryResponse;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.AccountTotals;
import wyq.pocket.money.money.dto.BizTypeSum;
import wyq.pocket.money.money.dto.DirectionSum;
import wyq.pocket.money.money.dto.UserDirectionSum;
import wyq.pocket.money.money.service.MoneyQueryService;
import wyq.pocket.money.user.dto.MemberSummary;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.FamilyService;
import wyq.pocket.money.user.service.UserService;

/**
 * 财务报表业务（M2 设计 §8.2 #23–#24）：同步聚合（D7 决策，
 * 10 TPS 家庭场景直接聚合流水，偏差已记入 roadmap）。
 *
 * <p>跨模块数据一律经 money 模块 MoneyQueryService 门面获取；
 * 成员清单经 user 模块 FamilyService 获取（设计 §3.4 允许方向）。
 */
@Component
public class ReportService {

    /** 月份格式：YYYY-MM。 */
    private static final Pattern MONTH_PATTERN =
            Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private final MoneyQueryService moneyQueryService;

    private final FamilyAccessChecker familyAccessChecker;

    private final FamilyService familyService;

    private final UserService userService;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param moneyQueryService   零花钱查询门面
     * @param familyAccessChecker 数据级访问守卫
     * @param familyService       家庭服务（成员清单）
     * @param userService         用户查询服务（成员数）
     * @param clock               时钟（业务时区）
     */
    public ReportService(MoneyQueryService moneyQueryService,
                         FamilyAccessChecker familyAccessChecker,
                         FamilyService familyService,
                         UserService userService, Clock clock) {
        this.moneyQueryService = moneyQueryService;
        this.familyAccessChecker = familyAccessChecker;
        this.familyService = familyService;
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * 月度收支报表（#23）。
     *
     * @param principal 当前登录主体
     * @param month     报表月份（YYYY-MM，不可晚于当月）
     * @return 收支报表
     * @throws BusinessException 500001 格式非法 / 500002 月份在未来
     */
    public IncomeExpenseReportResponse incomeExpense(UserIdPrincipal principal, String month) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        YearMonth yearMonth = parseMonth(month);
        requireNotFuture(yearMonth);
        Instant from = startOfMonth(yearMonth);
        Instant toExclusive = startOfMonth(yearMonth.plusMonths(1));
        List<BizTypeSum> sums = moneyQueryService.sumByBizType(familyId, from, toExclusive);
        BigDecimal totalIncome = sumOf(sums, TxDirection.IN);
        BigDecimal totalExpense = sumOf(sums, TxDirection.OUT);
        return new IncomeExpenseReportResponse(yearMonth.toString(), totalIncome,
                totalExpense, totalIncome.subtract(totalExpense),
                byType(sums, TxDirection.IN), byType(sums, TxDirection.OUT),
                memberRows(familyId, principal, from, toExclusive));
    }

    /**
     * 统计摘要（#24）。
     *
     * @param principal 当前登录主体
     * @return 统计摘要
     */
    public StatisticsSummaryResponse statistics(UserIdPrincipal principal) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        AccountTotals totals = moneyQueryService.accountTotals(familyId);
        YearMonth currentMonth = YearMonth.now(clock);
        Instant monthStart = startOfMonth(currentMonth);
        Map<TxDirection, BigDecimal> monthSums = directionSums(familyId, monthStart);
        return new StatisticsSummaryResponse(moneyQueryService.totalBalance(familyId),
                totals.totalIncome(), totals.totalExpense(),
                monthSums.get(TxDirection.IN), monthSums.get(TxDirection.OUT),
                userService.countFamilyMembers(familyId));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(FinanceErrorCode.MONTH_FORMAT_INVALID);
        }
        return YearMonth.parse(month);
    }

    private void requireNotFuture(YearMonth yearMonth) {
        if (yearMonth.isAfter(YearMonth.now(clock))) {
            throw new BusinessException(FinanceErrorCode.MONTH_IN_FUTURE);
        }
    }

    private Instant startOfMonth(YearMonth yearMonth) {
        return yearMonth.atDay(1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
    }

    private BigDecimal sumOf(List<BizTypeSum> sums, TxDirection direction) {
        BigDecimal total = BigDecimal.ZERO;
        for (BizTypeSum sum : sums) {
            if (sum.direction() == direction) {
                total = total.add(sum.total());
            }
        }
        return total;
    }

    private Map<String, BigDecimal> byType(List<BizTypeSum> sums, TxDirection direction) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (BizTypeSum sum : sums) {
            if (sum.direction() == direction) {
                result.merge(sum.bizType().name(), sum.total(), BigDecimal::add);
            }
        }
        return result;
    }

    /**
     * 成员行：全家庭成员都上榜（设计 §8.2 #23），无收支的成员以 0/0/0 展示。
     *
     * @param familyId    家庭 ID
     * @param principal   当前登录主体（listMembers 需鉴权上下文）
     * @param from        月起（含）
     * @param toExclusive 月止（不含）
     * @return 成员收支行
     */
    private List<MemberReportRow> memberRows(long familyId, UserIdPrincipal principal,
                                             Instant from, Instant toExclusive) {
        Map<Long, BigDecimal> incomeByUser = new HashMap<>();
        Map<Long, BigDecimal> expenseByUser = new HashMap<>();
        for (UserDirectionSum sum
                : moneyQueryService.sumByUserAndDirection(familyId, from, toExclusive)) {
            Map<Long, BigDecimal> target = sum.direction() == TxDirection.IN
                    ? incomeByUser
                    : expenseByUser;
            target.merge(sum.userId(), sum.total(), BigDecimal::add);
        }
        List<MemberReportRow> rows = new ArrayList<>();
        for (MemberSummary member : familyService.listMembers(familyId, principal)) {
            BigDecimal income = incomeByUser.getOrDefault(member.userId(), BigDecimal.ZERO);
            BigDecimal expense = expenseByUser.getOrDefault(member.userId(), BigDecimal.ZERO);
            rows.add(new MemberReportRow(member.userId(), member.nickname(),
                    income, expense, income.subtract(expense)));
        }
        return rows;
    }

    private Map<TxDirection, BigDecimal> directionSums(long familyId, Instant since) {
        Map<TxDirection, BigDecimal> sums = new HashMap<>();
        sums.put(TxDirection.IN, BigDecimal.ZERO);
        sums.put(TxDirection.OUT, BigDecimal.ZERO);
        for (DirectionSum sum : moneyQueryService.sumByDirectionSince(familyId, since)) {
            sums.put(sum.direction(), sum.total());
        }
        return sums;
    }
}
