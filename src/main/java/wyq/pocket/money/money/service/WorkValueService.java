package wyq.pocket.money.money.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import wyq.pocket.money.money.domain.TxRefType;
import wyq.pocket.money.money.domain.WorkValueRecord;
import wyq.pocket.money.money.dto.CreateWorkValueRequest;
import wyq.pocket.money.money.dto.WorkValueResponse;
import wyq.pocket.money.money.mapper.WorkValueRecordMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 工作价值业务（M2 设计 §9 业务决策）：父母记录每月工资收入，
 * 手动填写发放金额入账本人账户（操作人 = 收款人）。
 */
@Component
public class WorkValueService {

    /** 列表查询条数上限。 */
    private static final int LIST_LIMIT = 100;

    private final WorkValueRecordMapper recordMapper;

    private final AccountTransactionService accountTransactionService;

    private final FamilyAccessChecker familyAccessChecker;

    private final UserService userService;

    private final AuditService auditService;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param recordMapper              记录 Mapper
     * @param accountTransactionService 记账原语
     * @param familyAccessChecker       数据级访问守卫
     * @param userService               用户查询服务（昵称回显）
     * @param auditService              审计服务
     * @param clock                     时钟
     */
    public WorkValueService(WorkValueRecordMapper recordMapper,
                            AccountTransactionService accountTransactionService,
                            FamilyAccessChecker familyAccessChecker,
                            UserService userService,
                            AuditService auditService, Clock clock) {
        this.recordMapper = recordMapper;
        this.accountTransactionService = accountTransactionService;
        this.familyAccessChecker = familyAccessChecker;
        this.userService = userService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * 记录工作价值（#21，仅家长）：入账本人账户 + 落记录，同事务。
     *
     * <p>流水 ref_type=WORK_VALUE_RECORD、ref_id 留空，
     * 反向关联以 record.transaction_id 为准（同事务原子）。
     *
     * @param principal 当前登录主体（父母本人）
     * @param request   创建请求
     * @return 记录响应
     */
    @Transactional
    public WorkValueResponse create(UserIdPrincipal principal, CreateWorkValueRequest request) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        MoneyTransaction tx = accountTransactionService.apply(new TxCommand(familyId,
                principal.userId(), TxDirection.IN, TxBizType.WORK_VALUE,
                request.allowanceAmount(),
                TxRefType.WORK_VALUE_RECORD, null, principal.userId(),
                request.workSummary(), IdempotencyContext.currentKey()));
        WorkValueRecord record = new WorkValueRecord();
        record.setFamilyId(familyId);
        record.setParentUserId(principal.userId());
        record.setWorkMonth(request.workMonth());
        record.setSalaryIncome(request.salaryIncome());
        record.setAllowanceAmount(request.allowanceAmount());
        record.setWorkSummary(request.workSummary());
        record.setTransactionId(tx.getId());
        record.setRecordedBy(principal.userId());
        recordMapper.insert(record);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.WORK_VALUE_RECORD,
                "WORK_VALUE_RECORD", String.valueOf(record.getId()), null));
        return toResponse(record, userService.findNicknameMap(Set.of(record.getParentUserId())));
    }

    /**
     * 记录列表（#22）：家庭内全透明读，可选月份过滤。
     *
     * @param principal 当前登录主体
     * @param workMonth 可选月份过滤（YYYY-MM）
     * @return 记录响应列表（最多 {@value #LIST_LIMIT} 条）
     */
    public List<WorkValueResponse> list(UserIdPrincipal principal, String workMonth) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        List<WorkValueRecord> records = recordMapper.findList(familyId, workMonth, LIST_LIMIT);
        Map<Long, String> nicknames = userService.findNicknameMap(collectUserIds(records));
        List<WorkValueResponse> responses = new ArrayList<>();
        for (WorkValueRecord record : records) {
            responses.add(toResponse(record, nicknames));
        }
        return responses;
    }

    private Set<Long> collectUserIds(List<WorkValueRecord> records) {
        Set<Long> ids = new HashSet<>();
        for (WorkValueRecord record : records) {
            ids.add(record.getParentUserId());
        }
        return ids;
    }

    private WorkValueResponse toResponse(WorkValueRecord record, Map<Long, String> nicknames) {
        return new WorkValueResponse(record.getId(), record.getParentUserId(),
                nicknames.get(record.getParentUserId()), record.getWorkMonth(),
                record.getSalaryIncome(), record.getAllowanceAmount(), record.getWorkSummary(),
                record.getTransactionId(), record.getCreatedAt());
    }
}
