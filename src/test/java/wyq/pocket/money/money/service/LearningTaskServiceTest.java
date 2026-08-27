package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.domain.LearningTask;
import wyq.pocket.money.money.domain.LearningTaskStatus;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.domain.TxRefType;
import wyq.pocket.money.money.dto.LearningTaskResponse;
import wyq.pocket.money.money.dto.RejectTaskRequest;
import wyq.pocket.money.money.dto.SubmitTaskRequest;
import wyq.pocket.money.money.mapper.LearningTaskMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 学习任务单元测试（M2 设计 §10 / §12.1）：状态机合法 / 非法（300006 参数化）、
 * 重提、取消时机、执行人校验、approve 同事务入账、300005 不存在、overdue 推导。
 */
class LearningTaskServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    private static final UserIdPrincipal CHILD =
            new UserIdPrincipal(2L, 10L, "CHILD", false);

    private static final long TASK_ID = 50L;

    /** 固定时钟：2026-08-19。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(), ClockConfig.BUSINESS_ZONE);

    private final LearningTaskMapper taskMapper = mock(LearningTaskMapper.class);

    private final AccountTransactionService accountTransactionService =
            mock(AccountTransactionService.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final UserService userService = mock(UserService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final LearningTaskService service = new LearningTaskService(taskMapper,
            accountTransactionService, familyAccessChecker, userService, auditService, CLOCK);

    private LearningTask task(LearningTaskStatus status, LocalDate deadline) {
        LearningTask task = new LearningTask();
        task.setId(TASK_ID);
        task.setFamilyId(10L);
        task.setAssigneeUserId(2L);
        task.setCreatedBy(1L);
        task.setTitle("背单词");
        task.setRewardAmount(new BigDecimal("5.00"));
        task.setDeadline(deadline);
        task.setStatus(status);
        return task;
    }

    private void stubFound(LearningTask task) {
        when(taskMapper.findById(TASK_ID)).thenReturn(task);
        when(userService.findNicknameMap(anySet()))
                .thenReturn(Map.of(1L, "家长", 2L, "小明"));
    }

    private void expectCode(Throwable thrown, int code) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode().getCode()).isEqualTo(code);
    }

    @Test
    void submitShouldMovePendingToSubmitted() {
        stubFound(task(LearningTaskStatus.PENDING, null));

        LearningTaskResponse response =
                service.submit(TASK_ID, CHILD, new SubmitTaskRequest("做完了"));

        verify(taskMapper).updateSubmit(eq(TASK_ID), eq("做完了"), any(Instant.class));
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void submitShouldAllowResubmitFromRejected() {
        stubFound(task(LearningTaskStatus.REJECTED, null));

        service.submit(TASK_ID, CHILD, new SubmitTaskRequest("重做完了"));

        verify(taskMapper).updateSubmit(eq(TASK_ID), eq("重做完了"), any(Instant.class));
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE,
            names = {"PENDING", "REJECTED"})
    void submitShouldThrow300006OnIllegalStatus(LearningTaskStatus status) {
        stubFound(task(status, null));

        assertThatThrownBy(() -> service.submit(TASK_ID, CHILD, new SubmitTaskRequest("x")))
                .satisfies(thrown -> expectCode(thrown, 300006));
        verify(taskMapper, never()).updateSubmit(any(Long.class), any(), any());
    }

    @Test
    void submitShouldDenyNonAssignee() {
        stubFound(task(LearningTaskStatus.PENDING, null));

        assertThatThrownBy(() -> service.submit(TASK_ID, PARENT, new SubmitTaskRequest("x")))
                .isInstanceOf(AccessDeniedException.class);
        verify(taskMapper, never()).updateSubmit(any(Long.class), any(), any());
    }

    @Test
    void approveShouldApplyRewardAndPersistTransactionId() {
        stubFound(task(LearningTaskStatus.SUBMITTED, null));
        MoneyTransaction tx = new MoneyTransaction();
        tx.setId(77L);
        when(accountTransactionService.apply(any(TxCommand.class))).thenReturn(tx);

        service.approve(TASK_ID, PARENT);

        ArgumentCaptor<TxCommand> captor = ArgumentCaptor.forClass(TxCommand.class);
        verify(accountTransactionService).apply(captor.capture());
        TxCommand cmd = captor.getValue();
        assertThat(cmd.direction()).isEqualTo(TxDirection.IN);
        assertThat(cmd.bizType()).isEqualTo(TxBizType.LEARNING_REWARD);
        assertThat(cmd.refType()).isEqualTo(TxRefType.LEARNING_TASK);
        assertThat(cmd.refId()).isEqualTo(TASK_ID);
        assertThat(cmd.amount()).isEqualByComparingTo("5.00");
        assertThat(cmd.operatorUserId()).isEqualTo(1L);
        verify(taskMapper).updateApprove(eq(TASK_ID), eq(1L), any(Instant.class), eq(77L));
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "SUBMITTED")
    void approveShouldThrow300006OnIllegalStatus(LearningTaskStatus status) {
        stubFound(task(status, null));

        assertThatThrownBy(() -> service.approve(TASK_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 300006));
        verify(accountTransactionService, never()).apply(any(TxCommand.class));
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "SUBMITTED")
    void rejectShouldThrow300006OnIllegalStatus(LearningTaskStatus status) {
        stubFound(task(status, null));

        assertThatThrownBy(() -> service.reject(TASK_ID, PARENT,
                new RejectTaskRequest("不合格")))
                .satisfies(thrown -> expectCode(thrown, 300006));
    }

    @Test
    void rejectShouldPersistReason() {
        stubFound(task(LearningTaskStatus.SUBMITTED, null));

        service.reject(TASK_ID, PARENT,
                new RejectTaskRequest("不合格"));

        verify(taskMapper).updateReject(eq(TASK_ID), eq("不合格"), eq(1L), any(Instant.class));
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"PENDING", "SUBMITTED"})
    void cancelShouldThrow300006AfterGrant(LearningTaskStatus status) {
        stubFound(task(status, null));

        assertThatThrownBy(() -> service.cancel(TASK_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 300006));
        verify(taskMapper, never()).updateCancel(any(Long.class));
    }

    @Test
    void cancelShouldAllowBeforeGrant() {
        stubFound(task(LearningTaskStatus.PENDING, null));

        service.cancel(TASK_ID, PARENT);

        verify(taskMapper).updateCancel(eq(TASK_ID));
    }

    @Test
    void missingTaskShouldThrow300005() {
        when(taskMapper.findById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.submit(TASK_ID, CHILD, new SubmitTaskRequest("x")))
                .satisfies(thrown -> expectCode(thrown, 300005));
    }

    @Test
    void taskOfOtherFamilyShouldThrow300005() {
        LearningTask foreign = task(LearningTaskStatus.PENDING, null);
        foreign.setFamilyId(99L);
        when(taskMapper.findById(TASK_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service.cancel(TASK_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 300005));
    }

    @Test
    void overdueShouldBeTrueOnlyBeforeGrantAndPastDeadline() {
        LearningTask overdueTask = task(LearningTaskStatus.PENDING, TODAY.minusDays(1));
        stubFound(overdueTask);

        LearningTaskResponse response = service.cancel(TASK_ID, PARENT);

        assertThat(response.overdue()).isTrue();
    }

    @Test
    void overdueShouldBeFalseForFutureDeadline() {
        stubFound(task(LearningTaskStatus.PENDING, TODAY.plusDays(1)));

        LearningTaskResponse response = service.cancel(TASK_ID, PARENT);

        assertThat(response.overdue()).isFalse();
    }
}
