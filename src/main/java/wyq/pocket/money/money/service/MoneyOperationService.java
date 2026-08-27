package wyq.pocket.money.money.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.idempotency.IdempotencyContext;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.DepositRequest;
import wyq.pocket.money.money.dto.DepositWithdrawResponse;
import wyq.pocket.money.money.dto.WithdrawRequest;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 手动存取业务（M2 设计 §8.2 #5–#6）。
 *
 * <p>权限：家长可对本家庭任意成员账户存取；孩子仅限本人账户
 * （越权 403 + 100004，与附录 B 权限矩阵一致）。
 */
@Component
public class MoneyOperationService {

    /** 孩子角色标识（与 user 模块 User.ROLE_CHILD 同值）。 */
    private static final String ROLE_CHILD = "CHILD";

    private final FamilyAccessChecker familyAccessChecker;

    private final AccountTransactionService accountTransactionService;

    private final AuditService auditService;

    /**
     * 注入协作对象。
     *
     * @param familyAccessChecker       数据级访问守卫
     * @param accountTransactionService 记账原语
     * @param auditService              审计服务
     */
    public MoneyOperationService(FamilyAccessChecker familyAccessChecker,
                                 AccountTransactionService accountTransactionService,
                                 AuditService auditService) {
        this.familyAccessChecker = familyAccessChecker;
        this.accountTransactionService = accountTransactionService;
        this.auditService = auditService;
    }

    /**
     * 手动存入（#5）。
     *
     * @param principal    当前登录主体
     * @param targetUserId 目标账户持有人
     * @param request      存入请求
     * @return 记账结果
     */
    @Transactional
    public DepositWithdrawResponse deposit(UserIdPrincipal principal, long targetUserId,
                                           DepositRequest request) {
        MoneyTransaction tx = applyManual(principal, targetUserId, TxDirection.IN,
                TxBizType.MANUAL_ADD, request.amount(), request.remark());
        auditService.record(new AuditEntry(principal.userId(), AuditAction.MONEY_DEPOSIT,
                "MONEY_TRANSACTION", String.valueOf(tx.getId()), null));
        return new DepositWithdrawResponse(tx.getId(), targetUserId, tx.getAmount(),
                tx.getBalanceAfter());
    }

    /**
     * 手动取出（#6，自由提取，余额不足 300001）。
     *
     * @param principal    当前登录主体
     * @param targetUserId 目标账户持有人
     * @param request      取出请求
     * @return 记账结果
     */
    @Transactional
    public DepositWithdrawResponse withdraw(UserIdPrincipal principal, long targetUserId,
                                            WithdrawRequest request) {
        MoneyTransaction tx = applyManual(principal, targetUserId, TxDirection.OUT,
                TxBizType.WITHDRAW, request.amount(), request.remark());
        auditService.record(new AuditEntry(principal.userId(), AuditAction.MONEY_WITHDRAW,
                "MONEY_TRANSACTION", String.valueOf(tx.getId()), null));
        return new DepositWithdrawResponse(tx.getId(), targetUserId, tx.getAmount(),
                tx.getBalanceAfter());
    }

    private MoneyTransaction applyManual(UserIdPrincipal principal, long targetUserId,
                                         TxDirection direction, TxBizType bizType,
                                         java.math.BigDecimal amount, String remark) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        familyAccessChecker.requireMember(familyId, targetUserId);
        requireSelfIfChild(principal, targetUserId);
        return accountTransactionService.apply(new TxCommand(familyId, targetUserId, direction,
                bizType, amount, null, null, principal.userId(), remark,
                IdempotencyContext.currentKey()));
    }

    private void requireSelfIfChild(UserIdPrincipal principal, long targetUserId) {
        if (ROLE_CHILD.equals(principal.role()) && principal.userId() != targetUserId) {
            throw new AccessDeniedException(
                    "CHILD_SELF_ACCOUNT_ONLY user=" + principal.userId());
        }
    }
}
