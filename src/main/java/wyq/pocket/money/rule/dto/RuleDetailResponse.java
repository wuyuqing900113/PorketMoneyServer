package wyq.pocket.money.rule.dto;

import java.util.List;

/**
 * 规则详情响应（规则信息 + 近 12 个月发放记录）。
 *
 * @param rule        规则信息
 * @param recentGrants 近期发放记录（月份倒序，构造后不可变）
 */
public record RuleDetailResponse(RuleResponse rule, List<GrantRecordSummary> recentGrants) {

    /**
     * 紧凑构造器：发放记录做不可变快照，杜绝内外双向的可变共享。
     */
    public RuleDetailResponse {
        recentGrants = List.copyOf(recentGrants);
    }
}
