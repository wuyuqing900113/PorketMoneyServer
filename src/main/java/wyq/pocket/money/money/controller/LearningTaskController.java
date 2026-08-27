package wyq.pocket.money.money.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.OpenApiConfig;
import wyq.pocket.money.common.web.Result;
import wyq.pocket.money.money.dto.CreateLearningTaskRequest;
import wyq.pocket.money.money.dto.LearningTaskPageResponse;
import wyq.pocket.money.money.dto.LearningTaskResponse;
import wyq.pocket.money.money.dto.RejectTaskRequest;
import wyq.pocket.money.money.dto.SubmitTaskRequest;
import wyq.pocket.money.money.service.LearningTaskService;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 学习任务端点（M2 设计 §8.2 #15–#20、§10.1 状态机，资源挂在家庭路径下）。
 *
 * <p>写操作接口级限 PARENT，仅提交例外（孩子本人）；
 * 提交的数据级本人校验在 service 层（越权 403 + 100004）。
 * 入口统一校验路径 familyId 的成员资格（跨家庭 100004）。
 */
@Tag(name = "学习任务", description = "家长定义任务，孩子提交，家长确认发放奖励")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/families/{familyId}/learning-tasks")
public class LearningTaskController {

    private final LearningTaskService learningTaskService;

    private final FamilyAccessChecker familyAccessChecker;

    /**
     * 注入学习任务业务。
     *
     * @param learningTaskService 学习任务业务
     * @param familyAccessChecker 数据级访问守卫（路径 familyId 成员校验）
     */
    public LearningTaskController(LearningTaskService learningTaskService,
                                  FamilyAccessChecker familyAccessChecker) {
        this.learningTaskService = learningTaskService;
        this.familyAccessChecker = familyAccessChecker;
    }

    /**
     * 任务分页查询（#16）。
     *
     * @param familyId       家庭 ID
     * @param principal      当前登录主体
     * @param status         可选状态过滤
     * @param assigneeUserId 可选执行人过滤
     * @param page           页码
     * @param size           页大小
     * @return 分页任务
     */
    @Operation(summary = "学习任务分页查询",
            description = "家庭内全透明读。错误码：HTTP 401 + 100003；HTTP 403 + 100004。")
    @GetMapping
    public Result<LearningTaskPageResponse> list(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "assigneeUserId", required = false) Long assigneeUserId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(learningTaskService.list(principal, status, assigneeUserId,
                page, size));
    }

    /**
     * 创建任务（#15，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param request   创建请求
     * @return 任务响应
     */
    @Operation(summary = "创建学习任务（仅本家庭家长）",
            description = "错误码：HTTP 401 + 100003；HTTP 403 + 100004 CHILD 调用"
                    + "或执行人非本家庭成员；100001 参数校验失败。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping
    public Result<LearningTaskResponse> create(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody CreateLearningTaskRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(learningTaskService.create(principal, request));
    }

    /**
     * 提交任务（#17，仅执行人本人）。
     *
     * @param familyId  家庭 ID
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @param request   提交请求
     * @return 任务响应
     */
    @Operation(summary = "提交学习任务（仅执行人本人）",
            description = "PENDING / REJECTED → SUBMITTED（驳回可重提）。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004 非执行人；"
                    + "300005 任务不存在；300006 状态不允许。")
    @PostMapping("/{taskId}/submit")
    public Result<LearningTaskResponse> submit(@PathVariable("familyId") long familyId,
            @PathVariable("taskId") long taskId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody SubmitTaskRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(learningTaskService.submit(taskId, principal, request));
    }

    /**
     * 通过并发放（#18，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @return 任务响应
     */
    @Operation(summary = "通过任务并发放奖励（仅本家庭家长）",
            description = "SUBMITTED → APPROVED，同事务入账。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "300005 任务不存在；300006 状态不允许。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{taskId}/approve")
    public Result<LearningTaskResponse> approve(@PathVariable("familyId") long familyId,
            @PathVariable("taskId") long taskId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(learningTaskService.approve(taskId, principal));
    }

    /**
     * 驳回（#19，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @param request   驳回请求
     * @return 任务响应
     */
    @Operation(summary = "驳回任务（仅本家庭家长）",
            description = "SUBMITTED → REJECTED，可重提。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；100001 参数校验失败；"
                    + "300005 任务不存在；300006 状态不允许。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{taskId}/reject")
    public Result<LearningTaskResponse> reject(@PathVariable("familyId") long familyId,
            @PathVariable("taskId") long taskId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody RejectTaskRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(learningTaskService.reject(taskId, principal, request));
    }

    /**
     * 取消（#20，仅家长，限发放前）。
     *
     * @param familyId  家庭 ID
     * @param taskId    任务 ID
     * @param principal 当前登录主体
     * @return 任务响应
     */
    @Operation(summary = "取消任务（仅本家庭家长，限发放前）",
            description = "PENDING / SUBMITTED → CANCELED。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "300005 任务不存在；300006 状态不允许。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{taskId}/cancel")
    public Result<LearningTaskResponse> cancel(@PathVariable("familyId") long familyId,
            @PathVariable("taskId") long taskId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(learningTaskService.cancel(taskId, principal));
    }
}
