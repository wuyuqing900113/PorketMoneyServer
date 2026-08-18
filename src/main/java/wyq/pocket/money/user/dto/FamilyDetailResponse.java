package wyq.pocket.money.user.dto;

import java.util.List;

/**
 * 家庭详情响应：家庭基础信息与成员列表（M1 设计 §6.2 / §6.4）。
 *
 * <p>紧凑构造器以 {@link List#copyOf} 固化成员列表：持有方与调用方
 * 均无法再修改内部状态，成员列表对外仅暴露不可变快照。
 *
 * @param familyId    家庭 ID
 * @param familyName  家庭名
 * @param ownerUserId 创建者（家长）用户 ID
 * @param members     成员摘要列表（按加入顺序，构造后不可变）
 */
public record FamilyDetailResponse(long familyId, String familyName, long ownerUserId,
        List<MemberSummary> members) {

    /**
     * 紧凑构造器：成员列表做不可变快照，杜绝内外双向的可变共享。
     */
    public FamilyDetailResponse {
        members = List.copyOf(members);
    }
}
