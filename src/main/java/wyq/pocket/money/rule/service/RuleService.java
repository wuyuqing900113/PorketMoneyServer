package wyq.pocket.money.rule.service;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.domain.RuleGrantRecord;
import wyq.pocket.money.rule.dto.CreateRuleRequest;
import wyq.pocket.money.rule.dto.GrantRecordSummary;
import wyq.pocket.money.rule.dto.RuleDetailResponse;
import wyq.pocket.money.rule.dto.RuleErrorCode;
import wyq.pocket.money.rule.dto.RuleResponse;
import wyq.pocket.money.rule.dto.UpdateRuleRequest;
import wyq.pocket.money.rule.mapper.MoneyRuleMapper;
import wyq.pocket.money.rule.mapper.RuleGrantRecordMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 包月规则 CRUD 业务（M2 设计 §7、§8.2 #7–#14）。
 *
 * <p>约束：受益人未归档规则数上限（配置 pocket-money.rule.max-per-member）、
 * 家庭内规则名唯一、失效月不早于起始月、有发放记录的规则不可删除。
 * 读端点家庭内全透明，写端点接口级限 PARENT。
 */
@Component
public class RuleService {

    /** 规则详情页展示的近月发放记录条数。 */
    private static final int RECENT_GRANTS_LIMIT = 12;

    private final MoneyRuleMapper ruleMapper;

    private final RuleGrantRecordMapper grantRecordMapper;

    private final FamilyAccessChecker familyAccessChecker;

    private final UserService userService;

    private final AuditService auditService;

    private final Clock clock;

    private final int maxRulesPerMember;

    /**
     * 注入协作对象。
     *
     * @param ruleMapper          规则 Mapper
     * @param grantRecordMapper   发放记录 Mapper
     * @param familyAccessChecker 数据级访问守卫
     * @param userService         用户查询服务（昵称回显）
     * @param auditService        审计服务
     * @param clock               时钟
     * @param maxRulesPerMember   受益人未归档规则上限
     */
    public RuleService(MoneyRuleMapper ruleMapper, RuleGrantRecordMapper grantRecordMapper,
                       FamilyAccessChecker familyAccessChecker, UserService userService,
                       AuditService auditService, Clock clock,
                       @Value("${pocket-money.rule.max-per-member:10}") int maxRulesPerMember) {
        this.ruleMapper = ruleMapper;
        this.grantRecordMapper = grantRecordMapper;
        this.familyAccessChecker = familyAccessChecker;
        this.userService = userService;
        this.auditService = auditService;
        this.clock = clock;
        this.maxRulesPerMember = maxRulesPerMember;
    }

    /**
     * 创建规则（#7）。
     *
     * @param principal 当前登录主体
     * @param request   创建请求
     * @return 规则响应
     * @throws BusinessException 400004 上限 / 400006 重名 / 100001 月份顺序非法
     */
    @Transactional
    public RuleResponse create(UserIdPrincipal principal, CreateRuleRequest request) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        familyAccessChecker.requireMember(familyId, request.beneficiaryUserId());
        requireMonthOrder(request.startMonth(), request.endMonth());
        requireNameUnique(familyId, request.ruleName(), null);
        if (ruleMapper.countUnarchivedByBeneficiary(request.beneficiaryUserId())
                >= maxRulesPerMember) {
            throw new BusinessException(RuleErrorCode.RULE_LIMIT_REACHED);
        }
        MoneyRule rule = new MoneyRule();
        rule.setFamilyId(familyId);
        rule.setBeneficiaryUserId(request.beneficiaryUserId());
        rule.setRuleName(request.ruleName());
        rule.setAmount(request.amount());
        rule.setGrantDay(request.grantDay());
        rule.setStatus(MoneyRule.STATUS_ACTIVE);
        rule.setStartMonth(request.startMonth());
        rule.setEndMonth(request.endMonth());
        rule.setRemark(request.remark());
        rule.setCreatedBy(principal.userId());
        ruleMapper.insert(rule);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.RULE_CREATE,
                "MONEY_RULE", String.valueOf(rule.getId()), null));
        return toResponse(ruleMapper.findById(rule.getId()), Map.of(), Set.of());
    }

    /**
     * 规则列表（#8）：含「当月已发放」标记。
     *
     * @param principal 当前登录主体
     * @return 规则响应列表
     */
    public List<RuleResponse> list(UserIdPrincipal principal) {
        long familyId = principal.familyId();
        familyAccessChecker.requireMember(familyId, principal.userId());
        List<MoneyRule> rules = ruleMapper.findListByFamily(familyId);
        String currentMonth = YearMonth.now(clock).toString();
        Set<Long> grantedIds = new HashSet<>(
                grantRecordMapper.findGrantedRuleIds(familyId, currentMonth));
        Map<Long, String> nicknames = userService.findNicknameMap(collectUserIds(rules));
        List<RuleResponse> responses = new ArrayList<>();
        for (MoneyRule rule : rules) {
            responses.add(toResponse(rule, nicknames, grantedIds));
        }
        return responses;
    }

    /**
     * 规则详情（#9）：含近 12 个月发放记录。
     *
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则详情
     * @throws BusinessException 400001 规则不存在
     */
    public RuleDetailResponse detail(long ruleId, UserIdPrincipal principal) {
        MoneyRule rule = requireRuleInFamily(ruleId, principal.familyId(), principal.userId());
        String currentMonth = YearMonth.now(clock).toString();
        Set<Long> grantedIds = new HashSet<>(
                grantRecordMapper.findGrantedRuleIds(rule.getFamilyId(), currentMonth));
        Map<Long, String> nicknames = userService.findNicknameMap(
                Set.of(rule.getBeneficiaryUserId()));
        List<GrantRecordSummary> grants = new ArrayList<>();
        for (RuleGrantRecord record
                : grantRecordMapper.findRecentByRule(ruleId, RECENT_GRANTS_LIMIT)) {
            grants.add(new GrantRecordSummary(record.getGrantMonth(), record.getAmount(),
                    record.getTransactionId(), record.getGrantedAt()));
        }
        return new RuleDetailResponse(toResponse(rule, nicknames, grantedIds), grants);
    }

    /**
     * 修改规则（#10，起始月不可改）。
     *
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @param request   修改请求
     * @return 规则响应
     * @throws BusinessException 400001 不存在 / 400006 重名 / 100001 月份顺序非法
     */
    @Transactional
    public RuleResponse update(long ruleId, UserIdPrincipal principal,
                               UpdateRuleRequest request) {
        MoneyRule rule = requireRuleInFamily(ruleId, principal.familyId(), principal.userId());
        requireMonthOrder(rule.getStartMonth(), request.endMonth());
        requireNameUnique(rule.getFamilyId(), request.ruleName(), ruleId);
        ruleMapper.update(ruleId, request.ruleName(), request.amount(), request.grantDay(),
                request.endMonth(), request.remark());
        auditService.record(new AuditEntry(principal.userId(), AuditAction.RULE_UPDATE,
                "MONEY_RULE", String.valueOf(ruleId), null));
        return toResponse(ruleMapper.findById(ruleId), Map.of(), Set.of());
    }

    /**
     * 暂停规则（#11）：ACTIVE → PAUSED。
     *
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则响应
     * @throws BusinessException 400001 不存在 / 400002 状态不允许
     */
    @Transactional
    public RuleResponse pause(long ruleId, UserIdPrincipal principal) {
        return changeStatus(ruleId, principal, MoneyRule.STATUS_ACTIVE,
                MoneyRule.STATUS_PAUSED, AuditAction.RULE_PAUSE);
    }

    /**
     * 恢复规则（#12）：PAUSED → ACTIVE。
     *
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则响应
     * @throws BusinessException 400001 不存在 / 400002 状态不允许
     */
    @Transactional
    public RuleResponse resume(long ruleId, UserIdPrincipal principal) {
        return changeStatus(ruleId, principal, MoneyRule.STATUS_PAUSED,
                MoneyRule.STATUS_ACTIVE, AuditAction.RULE_RESUME);
    }

    /**
     * 归档规则（#13）：ACTIVE / PAUSED → ARCHIVED（终态）。
     *
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则响应
     * @throws BusinessException 400001 不存在 / 400002 状态不允许
     */
    @Transactional
    public RuleResponse archive(long ruleId, UserIdPrincipal principal) {
        MoneyRule rule = requireRuleInFamily(ruleId, principal.familyId(), principal.userId());
        if (MoneyRule.STATUS_ARCHIVED.equals(rule.getStatus())) {
            throw new BusinessException(RuleErrorCode.RULE_STATUS_NOT_ALLOWED);
        }
        ruleMapper.updateStatus(ruleId, MoneyRule.STATUS_ARCHIVED);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.RULE_ARCHIVE,
                "MONEY_RULE", String.valueOf(ruleId), null));
        return toResponse(ruleMapper.findById(ruleId), Map.of(), Set.of());
    }

    /**
     * 删除规则（#14）：有发放记录不可删除（400005）。
     *
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @throws BusinessException 400001 不存在 / 400005 有发放记录
     */
    @Transactional
    public void delete(long ruleId, UserIdPrincipal principal) {
        requireRuleInFamily(ruleId, principal.familyId(), principal.userId());
        if (grantRecordMapper.countByRule(ruleId) > 0) {
            throw new BusinessException(RuleErrorCode.RULE_HAS_GRANTS);
        }
        ruleMapper.deleteById(ruleId);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.RULE_DELETE,
                "MONEY_RULE", String.valueOf(ruleId), null));
    }

    private RuleResponse changeStatus(long ruleId, UserIdPrincipal principal,
                                      String expectedStatus, String targetStatus,
                                      AuditAction action) {
        MoneyRule rule = requireRuleInFamily(ruleId, principal.familyId(), principal.userId());
        if (!expectedStatus.equals(rule.getStatus())) {
            throw new BusinessException(RuleErrorCode.RULE_STATUS_NOT_ALLOWED);
        }
        ruleMapper.updateStatus(ruleId, targetStatus);
        auditService.record(new AuditEntry(principal.userId(), action,
                "MONEY_RULE", String.valueOf(ruleId), null));
        return toResponse(ruleMapper.findById(ruleId), Map.of(), Set.of());
    }

    private MoneyRule requireRuleInFamily(long ruleId, long familyId, long operatorId) {
        familyAccessChecker.requireMember(familyId, operatorId);
        MoneyRule rule = ruleMapper.findById(ruleId);
        if (rule == null || rule.getFamilyId() != familyId) {
            throw new BusinessException(RuleErrorCode.RULE_NOT_FOUND);
        }
        return rule;
    }

    private void requireMonthOrder(String startMonth, String endMonth) {
        if (endMonth != null && endMonth.compareTo(startMonth) < 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "失效月不得早于起始月");
        }
    }

    private void requireNameUnique(long familyId, String ruleName, Long excludeId) {
        if (ruleMapper.countByName(familyId, ruleName, excludeId) > 0) {
            throw new BusinessException(RuleErrorCode.RULE_NAME_DUPLICATE);
        }
    }

    private Set<Long> collectUserIds(List<MoneyRule> rules) {
        Set<Long> ids = new HashSet<>();
        for (MoneyRule rule : rules) {
            ids.add(rule.getBeneficiaryUserId());
        }
        return ids;
    }

    private RuleResponse toResponse(MoneyRule rule, Map<Long, String> nicknames,
                                    Set<Long> grantedRuleIds) {
        return new RuleResponse(rule.getId(), rule.getBeneficiaryUserId(),
                nicknames.get(rule.getBeneficiaryUserId()), rule.getRuleName(),
                rule.getAmount(), rule.getGrantDay(), rule.getStatus(), rule.getStartMonth(),
                rule.getEndMonth(), rule.getRemark(), rule.getCreatedBy(), rule.getCreatedAt(),
                grantedRuleIds.contains(rule.getId()));
    }
}
