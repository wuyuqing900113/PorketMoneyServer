package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.domain.TxRefType;
import wyq.pocket.money.money.domain.WorkValueRecord;
import wyq.pocket.money.money.dto.CreateWorkValueRequest;
import wyq.pocket.money.money.dto.WorkValueResponse;
import wyq.pocket.money.money.mapper.WorkValueRecordMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 工作价值单元测试（M2 设计 §9 / §12.1）：入账 + 落记录原子性（先流水后记录，
 * record.transaction_id 反向关联）、多记录同月允许、salary=0 允许、列表过滤。
 */
class WorkValueServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    private static final Clock CLOCK = Clock.fixed(
            java.time.LocalDate.of(2026, 8, 19)
                    .atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(),
            ClockConfig.BUSINESS_ZONE);

    private final WorkValueRecordMapper recordMapper = mock(WorkValueRecordMapper.class);

    private final AccountTransactionService accountTransactionService =
            mock(AccountTransactionService.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final UserService userService = mock(UserService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final WorkValueService service = new WorkValueService(recordMapper,
            accountTransactionService, familyAccessChecker, userService, auditService, CLOCK);

    @Test
    void createShouldApplyTxFirstThenPersistRecordWithTransactionId() {
        MoneyTransaction tx = new MoneyTransaction();
        tx.setId(88L);
        when(accountTransactionService.apply(any(TxCommand.class))).thenReturn(tx);
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(1L, "爸爸"));

        WorkValueResponse response = service.create(PARENT, new CreateWorkValueRequest(
                "2026-08", new BigDecimal("8000.00"), new BigDecimal("200.00"), "8月工资"));

        ArgumentCaptor<TxCommand> captor = ArgumentCaptor.forClass(TxCommand.class);
        verify(accountTransactionService).apply(captor.capture());
        TxCommand cmd = captor.getValue();
        assertThat(cmd.userId()).isEqualTo(1L);
        assertThat(cmd.direction()).isEqualTo(TxDirection.IN);
        assertThat(cmd.bizType()).isEqualTo(TxBizType.WORK_VALUE);
        assertThat(cmd.refType()).isEqualTo(TxRefType.WORK_VALUE_RECORD);
        // ref_id 留空：反向关联以 record.transaction_id 为准
        assertThat(cmd.refId()).isNull();
        assertThat(cmd.amount()).isEqualByComparingTo("200.00");

        ArgumentCaptor<WorkValueRecord> recordCaptor =
                ArgumentCaptor.forClass(WorkValueRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getTransactionId()).isEqualTo(88L);
        assertThat(recordCaptor.getValue().getWorkMonth()).isEqualTo("2026-08");

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.WORK_VALUE_RECORD);

        assertThat(response.transactionId()).isEqualTo(88L);
        assertThat(response.nickname()).isEqualTo("爸爸");
    }

    @Test
    void createShouldAllowZeroSalary() {
        MoneyTransaction tx = new MoneyTransaction();
        tx.setId(89L);
        when(accountTransactionService.apply(any(TxCommand.class))).thenReturn(tx);
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(1L, "爸爸"));

        WorkValueResponse response = service.create(PARENT, new CreateWorkValueRequest(
                "2026-08", BigDecimal.ZERO, new BigDecimal("50.00"), null));

        assertThat(response.salaryIncome()).isEqualByComparingTo("0");
        assertThat(response.allowanceAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void listShouldFilterByMonthAndCapAt100() {
        WorkValueRecord record = new WorkValueRecord();
        record.setId(5L);
        record.setParentUserId(1L);
        record.setWorkMonth("2026-08");
        record.setSalaryIncome(new BigDecimal("8000.00"));
        record.setAllowanceAmount(new BigDecimal("200.00"));
        record.setTransactionId(88L);
        when(recordMapper.findList(10L, "2026-08", 100)).thenReturn(List.of(record));
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(1L, "爸爸"));

        List<WorkValueResponse> responses = service.list(PARENT, "2026-08");

        verify(recordMapper).findList(10L, "2026-08", 100);
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).workMonth()).isEqualTo("2026-08");
    }
}
