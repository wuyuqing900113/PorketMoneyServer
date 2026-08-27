package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.dto.LeaderboardEntry;
import wyq.pocket.money.money.dto.LeaderboardResponse;
import wyq.pocket.money.money.dto.UserSum;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 本周收入榜单元测试（M2 设计 §8.2 #4 / §12.1）：
 * 降序排序、并列同名次（dense ranking 不跳号）、无收入不入榜、周一起始（固定 Clock）。
 */
class LeaderboardServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    /** 固定时钟：2026-08-19（周三）→ 本周一 2026-08-17。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(), ClockConfig.BUSINESS_ZONE);

    private final MoneyTransactionMapper transactionMapper = mock(MoneyTransactionMapper.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final UserService userService = mock(UserService.class);

    private final LeaderboardService service = new LeaderboardService(
            transactionMapper, familyAccessChecker, userService, CLOCK);

    @Test
    void leaderboardShouldSortDescAndApplyDenseRank() {
        when(transactionMapper.sumIncomeByUserSince(eq(10L), any(Instant.class)))
                .thenReturn(List.of(
                        new UserSum(2L, new BigDecimal("5.00")),
                        new UserSum(3L, new BigDecimal("10.00")),
                        new UserSum(4L, new BigDecimal("10.00"))));
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(
                2L, "小明", 3L, "小红", 4L, "小刚"));

        LeaderboardResponse response = service.leaderboard(PARENT);

        assertThat(response.weekStartDate()).isEqualTo(TODAY.with(DayOfWeek.MONDAY));
        List<LeaderboardEntry> entries = response.entries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).rank()).isEqualTo(1);
        assertThat(entries.get(0).totalIncome()).isEqualByComparingTo("10.00");
        assertThat(entries.get(1).rank()).isEqualTo(1);
        assertThat(entries.get(1).totalIncome()).isEqualByComparingTo("10.00");
        // dense ranking：并列第 1 后直接第 2，不跳号
        assertThat(entries.get(2).rank()).isEqualTo(2);
        assertThat(entries.get(2).userId()).isEqualTo(2L);
        assertThat(entries.get(2).nickname()).isEqualTo("小明");
    }

    @Test
    void leaderboardShouldBeEmptyWhenNoIncome() {
        when(transactionMapper.sumIncomeByUserSince(eq(10L), any(Instant.class)))
                .thenReturn(List.of());
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of());

        LeaderboardResponse response = service.leaderboard(PARENT);

        assertThat(response.entries()).isEmpty();
    }
}
