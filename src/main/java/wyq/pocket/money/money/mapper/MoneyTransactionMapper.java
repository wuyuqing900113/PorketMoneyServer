package wyq.pocket.money.money.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.BizTypeSum;
import wyq.pocket.money.money.dto.DirectionSum;
import wyq.pocket.money.money.dto.TransactionRow;
import wyq.pocket.money.money.dto.TxWindowRow;
import wyq.pocket.money.money.dto.UserDirectionSum;
import wyq.pocket.money.money.dto.UserSum;

/**
 * 零花钱流水 Mapper（money_transaction，只追加，M2 设计 §4 / §11.1）。
 *
 * <p>动态过滤分页采用 &lt;script&gt; 内联，参数一律 #{} 占位。
 */
@Mapper
public interface MoneyTransactionMapper {

    /**
     * 插入流水。
     *
     * @param tx 流水（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO money_transaction (family_id, account_id, user_id, direction, "
            + "biz_type, amount, balance_after, ref_type, ref_id, operator_user_id, "
            + "remark, request_id) VALUES ("
            + "#{familyId}, #{accountId}, #{userId}, #{direction}, #{bizType}, #{amount}, "
            + "#{balanceAfter}, #{refType}, #{refId}, #{operatorUserId}, #{remark}, #{requestId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MoneyTransaction tx);

    /**
     * 流水分页查询（按时间倒序，含昵称回显）。
     *
     * @param familyId    家庭 ID
     * @param userId      可选：账户持有人过滤
     * @param direction   可选：方向过滤
     * @param bizType     可选：业务类型过滤
     * @param from        可选：起始时间（含）
     * @param toExclusive 可选：截止时间（不含）
     * @param limit       页大小
     * @param offset      偏移量
     * @return 流水行列表
     */
    @Select("<script>SELECT t.id, t.user_id, u.nickname, t.direction, t.biz_type, t.amount, "
            + "t.balance_after, op.nickname AS operator_nickname, t.remark, t.created_at "
            + "FROM money_transaction t "
            + "JOIN app_user u ON u.id = t.user_id "
            + "LEFT JOIN app_user op ON op.id = t.operator_user_id "
            + "WHERE t.family_id = #{familyId} "
            + "<if test='userId != null'>AND t.user_id = #{userId} </if>"
            + "<if test='direction != null'>AND t.direction = #{direction} </if>"
            + "<if test='bizType != null'>AND t.biz_type = #{bizType} </if>"
            + "<if test='from != null'>AND t.created_at &gt;= #{from} </if>"
            + "<if test='toExclusive != null'>AND t.created_at &lt; #{toExclusive} </if>"
            + "ORDER BY t.created_at DESC, t.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "user_id", javaType = Long.class),
        @Arg(column = "nickname", javaType = String.class),
        @Arg(column = "direction", javaType = TxDirection.class),
        @Arg(column = "biz_type", javaType = TxBizType.class),
        @Arg(column = "amount", javaType = BigDecimal.class),
        @Arg(column = "balance_after", javaType = BigDecimal.class),
        @Arg(column = "operator_nickname", javaType = String.class),
        @Arg(column = "remark", javaType = String.class),
        @Arg(column = "created_at", javaType = Instant.class)
    })
    List<TransactionRow> findPage(@Param("familyId") long familyId,
                                  @Param("userId") Long userId,
                                  @Param("direction") TxDirection direction,
                                  @Param("bizType") TxBizType bizType,
                                  @Param("from") Instant from,
                                  @Param("toExclusive") Instant toExclusive,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /**
     * 流水分页计数（过滤条件同 findPage）。
     *
     * @param familyId    家庭 ID
     * @param userId      可选过滤
     * @param direction   可选过滤
     * @param bizType     可选过滤
     * @param from        可选：起始时间（含）
     * @param toExclusive 可选：截止时间（不含）
     * @return 总条数
     */
    @Select("<script>SELECT COUNT(*) FROM money_transaction t "
            + "WHERE t.family_id = #{familyId} "
            + "<if test='userId != null'>AND t.user_id = #{userId} </if>"
            + "<if test='direction != null'>AND t.direction = #{direction} </if>"
            + "<if test='bizType != null'>AND t.biz_type = #{bizType} </if>"
            + "<if test='from != null'>AND t.created_at &gt;= #{from} </if>"
            + "<if test='toExclusive != null'>AND t.created_at &lt; #{toExclusive} </if>"
            + "</script>")
    long countPage(@Param("familyId") long familyId,
                   @Param("userId") Long userId,
                   @Param("direction") TxDirection direction,
                   @Param("bizType") TxBizType bizType,
                   @Param("from") Instant from,
                   @Param("toExclusive") Instant toExclusive);

    /**
     * 趋势窗口流水（仅方向 / 金额 / 时间）。
     *
     * @param familyId 家庭 ID
     * @param userId   可选：个人趋势过滤
     * @param since    窗口起始时间（含）
     * @return 窗口流水行列表
     */
    @Select("<script>SELECT t.direction, t.amount, t.created_at FROM money_transaction t "
            + "WHERE t.family_id = #{familyId} AND t.created_at &gt;= #{since} "
            + "<if test='userId != null'>AND t.user_id = #{userId} </if>"
            + "ORDER BY t.created_at</script>")
    @ConstructorArgs({
        @Arg(column = "direction", javaType = TxDirection.class),
        @Arg(column = "amount", javaType = BigDecimal.class),
        @Arg(column = "created_at", javaType = Instant.class)
    })
    List<TxWindowRow> findWindow(@Param("familyId") long familyId,
                                 @Param("userId") Long userId,
                                 @Param("since") Instant since);

    /**
     * 起始时间后按方向聚合金额（看板周 / 月收支）。
     *
     * @param familyId 家庭 ID
     * @param since    起始时间（含）
     * @return 方向合计列表
     */
    @Select("SELECT direction, COALESCE(SUM(amount), 0) AS total FROM money_transaction "
            + "WHERE family_id = #{familyId} AND created_at >= #{since} GROUP BY direction")
    @ConstructorArgs({
        @Arg(column = "direction", javaType = TxDirection.class),
        @Arg(column = "total", javaType = BigDecimal.class)
    })
    List<DirectionSum> sumByDirectionSince(@Param("familyId") long familyId,
                                           @Param("since") Instant since);

    /**
     * 区间内按业务类型 + 方向聚合（收支报表）。
     *
     * @param familyId    家庭 ID
     * @param from        起始时间（含）
     * @param toExclusive 截止时间（不含）
     * @return 业务类型合计列表
     */
    @Select("SELECT biz_type, direction, COALESCE(SUM(amount), 0) AS total "
            + "FROM money_transaction WHERE family_id = #{familyId} "
            + "AND created_at >= #{from} AND created_at < #{toExclusive} "
            + "GROUP BY biz_type, direction")
    @ConstructorArgs({
        @Arg(column = "biz_type", javaType = TxBizType.class),
        @Arg(column = "direction", javaType = TxDirection.class),
        @Arg(column = "total", javaType = BigDecimal.class)
    })
    List<BizTypeSum> sumByBizType(@Param("familyId") long familyId,
                                  @Param("from") Instant from,
                                  @Param("toExclusive") Instant toExclusive);

    /**
     * 区间内按用户 + 方向聚合（报表成员行）。
     *
     * @param familyId    家庭 ID
     * @param from        起始时间（含）
     * @param toExclusive 截止时间（不含）
     * @return 用户方向合计列表
     */
    @Select("SELECT user_id, direction, COALESCE(SUM(amount), 0) AS total "
            + "FROM money_transaction WHERE family_id = #{familyId} "
            + "AND created_at >= #{from} AND created_at < #{toExclusive} "
            + "GROUP BY user_id, direction")
    @ConstructorArgs({
        @Arg(column = "user_id", javaType = Long.class),
        @Arg(column = "direction", javaType = TxDirection.class),
        @Arg(column = "total", javaType = BigDecimal.class)
    })
    List<UserDirectionSum> sumByUserAndDirection(@Param("familyId") long familyId,
                                                 @Param("from") Instant from,
                                                 @Param("toExclusive") Instant toExclusive);

    /**
     * 起始时间后各用户收入合计（本周收入榜）。
     *
     * @param familyId 家庭 ID
     * @param since    起始时间（含）
     * @return 用户收入合计列表
     */
    @Select("SELECT user_id, COALESCE(SUM(amount), 0) AS total FROM money_transaction "
            + "WHERE family_id = #{familyId} AND direction = 'IN' AND created_at >= #{since} "
            + "GROUP BY user_id")
    @ConstructorArgs({
        @Arg(column = "user_id", javaType = Long.class),
        @Arg(column = "total", javaType = BigDecimal.class)
    })
    List<UserSum> sumIncomeByUserSince(@Param("familyId") long familyId,
                                       @Param("since") Instant since);
}
