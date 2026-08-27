package wyq.pocket.money.money.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.dto.LeaderboardEntry;
import wyq.pocket.money.money.dto.LeaderboardResponse;
import wyq.pocket.money.money.dto.UserSum;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 本周收入榜服务（M2 设计 §8.2 #4）：本周一 00:00（业务时区）起
 * 各成员收入合计，dense ranking（同收入同名次，不跳号）。
 *
 * <p>业务假设：仅展示本周有收入的成员，无收入成员不入榜。
 */
@Component
public class LeaderboardService {

    private final MoneyTransactionMapper transactionMapper;

    private final FamilyAccessChecker familyAccessChecker;

    private final UserService userService;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param transactionMapper   流水 Mapper
     * @param familyAccessChecker 数据级访问守卫
     * @param userService         用户查询服务（昵称回显）
     * @param clock               时钟（业务时区）
     */
    public LeaderboardService(MoneyTransactionMapper transactionMapper,
                              FamilyAccessChecker familyAccessChecker,
                              UserService userService, Clock clock) {
        this.transactionMapper = transactionMapper;
        this.familyAccessChecker = familyAccessChecker;
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * 本周收入榜（#4）。
     *
     * @param principal 当前登录主体
     * @return 榜单响应
     */
    public LeaderboardResponse leaderboard(UserIdPrincipal principal) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        LocalDate weekStart = LocalDate.now(clock).with(DayOfWeek.MONDAY);
        Instant since = weekStart.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
        List<UserSum> sums = transactionMapper.sumIncomeByUserSince(familyId, since);
        Map<Long, String> nicknames = userService.findNicknameMap(userIds(sums));
        return new LeaderboardResponse(weekStart, ranked(sums, nicknames));
    }

    private Set<Long> userIds(List<UserSum> sums) {
        return sums.stream().map(UserSum::userId).collect(Collectors.toSet());
    }

    private List<LeaderboardEntry> ranked(List<UserSum> sums, Map<Long, String> nicknames) {
        List<UserSum> sorted = new ArrayList<>(sums);
        sorted.sort(Comparator.comparing(UserSum::total).reversed());
        List<LeaderboardEntry> entries = new ArrayList<>();
        BigDecimal previousTotal = null;
        int rank = 0;
        for (UserSum sum : sorted) {
            if (previousTotal == null || sum.total().compareTo(previousTotal) != 0) {
                rank++;
                previousTotal = sum.total();
            }
            entries.add(new LeaderboardEntry(rank, sum.userId(),
                    nicknames.get(sum.userId()), sum.total()));
        }
        return entries;
    }
}
