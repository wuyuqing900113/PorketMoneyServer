package wyq.pocket.money.money.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.money.domain.LearningTask;
import wyq.pocket.money.money.domain.LearningTaskStatus;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.domain.TxRefType;
import wyq.pocket.money.money.dto.CreateLearningTaskRequest;
import wyq.pocket.money.money.dto.LearningTaskPageResponse;
import wyq.pocket.money.money.dto.LearningTaskResponse;
import wyq.pocket.money.money.dto.MoneyErrorCode;
import wyq.pocket.money.money.dto.RejectTaskRequest;
import wyq.pocket.money.money.dto.SubmitTaskRequest;
import wyq.pocket.money.money.mapper.LearningTaskMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 学习任务业务（M2 设计 §10）：家长定义 → 孩子提交 → 家长确认发放。
 *
 * <p>状态机：PENDING → SUBMITTED → APPROVED / REJECTED（驳回可重提），
 * 发放前（PENDING / SUBMITTED）可取消。家庭内全透明读；
 * 状态校验在 service 层完成（并发竞态窗口极小，属业务可接受，见待验证点）。
 */
@Component
public class LearningTaskService {

    /** 默认页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大页大小。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final LearningTaskMapper taskMapper;

    private final AccountTransactionService accountTransactionService;

    private final FamilyAccessChecker familyAccessChecker;

    private final UserService userService;

    private final AuditService auditService;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param taskMapper                任务 Mapper
     * @param accountTransactionService 记账原语
     * @param familyAccessChecker       数据级访问守卫
     * @param userService               用户查询服务（昵称回显）
     * @param auditService              审计服务
     * @param clock                     时钟
     */
    public LearningTaskService(LearningTaskMapper taskMapper,
                               AccountTransactionService accountTransactionService,
                               FamilyAccessChecker familyAccessChecker,
                               UserService userService,
                               AuditService auditService, Clock clock) {
        this.taskMapper = taskMapper;
        this.accountTransactionService = accountTransactionService;
        this.familyAccessChecker = familyAccessChecker;
        this.userService = userService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * 创建任务（#15，仅家长，接口级守卫）。
     *
     * @param principal 当前登录主体
     * @param request   创建请求
     * @return 任务响应
     */
    @Transactional
    public LearningTaskResponse create(UserIdPrincipal principal,
                                       CreateLearningTaskRequest request) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        familyAccessChecker.requireMember(familyId, request.assigneeUserId());
        LearningTask task = new LearningTask();
        task.setFamilyId(familyId);
        task.setAssigneeUserId(request.assigneeUserId());
        task.setCreatedBy(principal.userId());
        task.setTitle(request.title());
        task.setRewardAmount(request.rewardAmount());
        task.setDeadline(request.deadline());
        taskMapper.insert(task);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.LEARNING_TASK_CREATE,
                "LEARNING_TASK", String.valueOf(task.getId()), null));
        return toResponse(taskMapper.findById(task.getId()), nicknames(task), LocalDate.now(clock));
    }

    /**
     * 提交任务（#16，仅执行人本人）：PENDING / REJECTED → SUBMITTED。
     *
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @param request   提交请求
     * @return 任务响应
     * @throws BusinessException 300005 不存在 / 300006 状态不允许
     */
    @Transactional
    public LearningTaskResponse submit(long taskId, UserIdPrincipal principal,
                                       SubmitTaskRequest request) {
        LearningTask task = requireTask(taskId, principal.familyId());
        if (task.getAssigneeUserId() != principal.userId()) {
            throw new AccessDeniedException("TASK_ASSIGNEE_ONLY user=" + principal.userId());
        }
        requireStatus(task, LearningTaskStatus.PENDING, LearningTaskStatus.REJECTED);
        taskMapper.updateSubmit(taskId, request.submitNote(), Instant.now(clock));
        auditService.record(new AuditEntry(principal.userId(), AuditAction.LEARNING_TASK_SUBMIT,
                "LEARNING_TASK", String.valueOf(taskId), null));
        return toResponse(taskMapper.findById(taskId), nicknames(task), LocalDate.now(clock));
    }

    /**
     * 通过并发放奖励（#17，仅家长）：SUBMITTED → APPROVED，同事务入账。
     *
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @return 任务响应
     * @throws BusinessException 300005 不存在 / 300006 状态不允许
     */
    @Transactional
    public LearningTaskResponse approve(long taskId, UserIdPrincipal principal) {
        LearningTask task = requireTask(taskId, principal.familyId());
        requireStatus(task, LearningTaskStatus.SUBMITTED);
        MoneyTransaction tx = accountTransactionService.apply(new TxCommand(
                task.getFamilyId(), task.getAssigneeUserId(), TxDirection.IN,
                TxBizType.LEARNING_REWARD, task.getRewardAmount(),
                TxRefType.LEARNING_TASK, task.getId(), principal.userId(), task.getTitle(), null));
        taskMapper.updateApprove(taskId, principal.userId(), Instant.now(clock), tx.getId());
        auditService.record(new AuditEntry(principal.userId(), AuditAction.LEARNING_TASK_APPROVE,
                "LEARNING_TASK", String.valueOf(taskId), null));
        return toResponse(taskMapper.findById(taskId), nicknames(task), LocalDate.now(clock));
    }

    /**
     * 驳回（#18，仅家长）：SUBMITTED → REJECTED，可重提。
     *
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @param request   驳回请求
     * @return 任务响应
     * @throws BusinessException 300005 不存在 / 300006 状态不允许
     */
    @Transactional
    public LearningTaskResponse reject(long taskId, UserIdPrincipal principal,
                                       RejectTaskRequest request) {
        LearningTask task = requireTask(taskId, principal.familyId());
        requireStatus(task, LearningTaskStatus.SUBMITTED);
        taskMapper.updateReject(taskId, request.rejectReason(), principal.userId(),
                Instant.now(clock));
        auditService.record(new AuditEntry(principal.userId(), AuditAction.LEARNING_TASK_REJECT,
                "LEARNING_TASK", String.valueOf(taskId), null));
        return toResponse(taskMapper.findById(taskId), nicknames(task), LocalDate.now(clock));
    }

    /**
     * 取消（#19，仅家长）：限发放前（PENDING / SUBMITTED）。
     *
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @return 任务响应
     * @throws BusinessException 300005 不存在 / 300006 状态不允许
     */
    @Transactional
    public LearningTaskResponse cancel(long taskId, UserIdPrincipal principal) {
        LearningTask task = requireTask(taskId, principal.familyId());
        requireStatus(task, LearningTaskStatus.PENDING, LearningTaskStatus.SUBMITTED);
        taskMapper.updateCancel(taskId);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.LEARNING_TASK_CANCEL,
                "LEARNING_TASK", String.valueOf(taskId), null));
        return toResponse(taskMapper.findById(taskId), nicknames(task), LocalDate.now(clock));
    }

    /**
     * 任务分页查询（#20）：家庭内全透明读。
     *
     * @param principal      当前登录主体
     * @param status         可选状态过滤
     * @param assigneeUserId 可选执行人过滤
     * @param page           页码（从 1 起）
     * @param size           页大小
     * @return 分页结果
     */
    public LearningTaskPageResponse list(UserIdPrincipal principal, String status,
                                         Long assigneeUserId, int page, int size) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        LearningTaskStatus parsed = parseStatus(status);
        String statusName = parsed == null ? null : parsed.name();
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        int total = taskMapper.countPage(familyId, statusName, assigneeUserId);
        List<LearningTask> tasks = taskMapper.findPage(familyId, statusName, assigneeUserId,
                safeSize, (safePage - 1) * safeSize);
        return new LearningTaskPageResponse(toResponses(tasks), total, safePage, safeSize);
    }

    private LearningTask requireTask(long taskId, long familyId) {
        LearningTask task = taskMapper.findById(taskId);
        if (task == null || task.getFamilyId() != familyId) {
            throw new BusinessException(MoneyErrorCode.LEARNING_TASK_NOT_FOUND);
        }
        return task;
    }

    private void requireStatus(LearningTask task, LearningTaskStatus... allowed) {
        for (LearningTaskStatus status : allowed) {
            if (task.getStatus() == status) {
                return;
            }
        }
        throw new BusinessException(MoneyErrorCode.TASK_STATUS_NOT_ALLOWED);
    }

    private LearningTaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return LearningTaskStatus.valueOf(status.trim().toUpperCase());
    }

    private List<LearningTaskResponse> toResponses(List<LearningTask> tasks) {
        Map<Long, String> nicknames = userService.findNicknameMap(collectUserIds(tasks));
        LocalDate today = LocalDate.now(clock);
        List<LearningTaskResponse> responses = new ArrayList<>();
        for (LearningTask task : tasks) {
            responses.add(toResponse(task, nicknames, today));
        }
        return responses;
    }

    private Set<Long> collectUserIds(List<LearningTask> tasks) {
        Set<Long> ids = new HashSet<>();
        for (LearningTask task : tasks) {
            ids.add(task.getAssigneeUserId());
            ids.add(task.getCreatedBy());
        }
        return ids;
    }

    private Map<Long, String> nicknames(LearningTask task) {
        return userService.findNicknameMap(
                Set.of(task.getAssigneeUserId(), task.getCreatedBy()));
    }

    private LearningTaskResponse toResponse(LearningTask task, Map<Long, String> nicknames,
                                            LocalDate today) {
        boolean overdue = task.getDeadline() != null && task.getDeadline().isBefore(today)
                && (task.getStatus() == LearningTaskStatus.PENDING
                || task.getStatus() == LearningTaskStatus.SUBMITTED);
        return new LearningTaskResponse(task.getId(), task.getFamilyId(),
                task.getAssigneeUserId(), nicknames.get(task.getAssigneeUserId()),
                task.getCreatedBy(), nicknames.get(task.getCreatedBy()), task.getTitle(),
                task.getRewardAmount(), task.getDeadline(), task.getStatus().name(),
                task.getSubmitNote(), task.getSubmittedAt(), task.getRejectReason(),
                task.getReviewedBy(), task.getReviewedAt(), task.getTransactionId(),
                task.getCreatedAt(), overdue);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
