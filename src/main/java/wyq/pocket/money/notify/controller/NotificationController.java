package wyq.pocket.money.notify.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.OpenApiConfig;
import wyq.pocket.money.common.web.Result;
import wyq.pocket.money.notify.dto.NotificationPageResponse;
import wyq.pocket.money.notify.dto.UnreadCountResponse;
import wyq.pocket.money.notify.service.NotificationService;

/**
 * 通知端点（M5 设计 §5.4 #1–#4）。
 *
 * <p>通知为接收人维度，无 familyId 路径；归属校验 =
 * {@code notification.user_id == principal.userId()}（不匹配 → 700001）。
 */
@Tag(name = "通知", description = "站内信分页、未读数与已读标记")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 注入通知服务。
     *
     * @param notificationService 通知服务
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 通知分页（未读优先）。
     *
     * @param principal 当前登录主体
     * @param page      页码（默认 1）
     * @param size      页大小（默认 20，上限 50）
     * @return 分页通知
     */
    @Operation(summary = "通知分页",
            description = "本人通知分页（未读优先，page 默认 1、size 默认 20 上限 50）。"
                    + "错误码：HTTP 401 + 100003 未认证。")
    @GetMapping
    public Result<NotificationPageResponse> list(
            @AuthenticationPrincipal UserIdPrincipal principal,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(notificationService.page(principal.userId(), page, size));
    }

    /**
     * 未读数。
     *
     * @param principal 当前登录主体
     * @return 未读数
     */
    @Operation(summary = "未读数",
            description = "本人未读通知数。错误码：HTTP 401 + 100003 未认证。")
    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return Result.success(new UnreadCountResponse(
                notificationService.unreadCount(principal.userId())));
    }

    /**
     * 标记单条已读（校验归属本人）。
     *
     * @param id        通知 ID
     * @param principal 当前登录主体
     * @return 空结果
     */
    @Operation(summary = "标记已读",
            description = "校验归属本人，越权或不存在返回 700001。"
                    + "错误码：HTTP 401 + 100003；700001 通知不存在或非本人。")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable("id") long id,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        notificationService.markRead(id, principal.userId());
        return Result.success();
    }

    /**
     * 全部标记已读。
     *
     * @param principal 当前登录主体
     * @return 空结果
     */
    @Operation(summary = "全部已读",
            description = "本人未读通知全部标记已读。错误码：HTTP 401 + 100003 未认证。")
    @PostMapping("/read-all")
    public Result<Void> markAllRead(@AuthenticationPrincipal UserIdPrincipal principal) {
        notificationService.markAllRead(principal.userId());
        return Result.success();
    }
}
