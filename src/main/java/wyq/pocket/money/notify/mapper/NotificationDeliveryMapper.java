package wyq.pocket.money.notify.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.notify.domain.NotificationDelivery;
import wyq.pocket.money.notify.dto.PendingDelivery;

/**
 * 外部通道投递记录 Mapper（notification_delivery，M5 设计 §9.1）。
 */
@Mapper
public interface NotificationDeliveryMapper {

    /**
     * 插入投递记录（retry_count/next_retry_at 由库默认 0 / now() 填充）。
     *
     * @param delivery 投递记录（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO notification_delivery (notification_id, channel, status) "
            + "VALUES (#{notificationId}, #{channel}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NotificationDelivery delivery);

    /**
     * 扫描待投递记录（PENDING 且到点，联通知取接收人与文案）。
     *
     * <p>record 无默认构造器，显式 @ConstructorArgs 按列映射。
     *
     * @param now   当前时间
     * @param limit 单批上限
     * @return 待投递记录
     */
    @Select("SELECT d.id AS delivery_id, d.notification_id, d.retry_count, "
            + "n.user_id, n.title, n.content "
            + "FROM notification_delivery d JOIN notification n ON n.id = d.notification_id "
            + "WHERE d.status = 'PENDING' AND d.next_retry_at <= #{now} "
            + "ORDER BY d.next_retry_at LIMIT #{limit}")
    @ConstructorArgs({
        @Arg(column = "delivery_id", javaType = long.class),
        @Arg(column = "notification_id", javaType = long.class),
        @Arg(column = "retry_count", javaType = int.class),
        @Arg(column = "user_id", javaType = long.class),
        @Arg(column = "title", javaType = String.class),
        @Arg(column = "content", javaType = String.class)
    })
    List<PendingDelivery> findPendingDeliveries(@Param("now") Instant now,
                                                @Param("limit") int limit);

    /**
     * 标记投递成功。
     *
     * @param id 投递记录 ID
     * @return 影响行数
     */
    @Update("UPDATE notification_delivery SET status = 'SENT', sent_at = now() WHERE id = #{id}")
    int markSent(@Param("id") long id);

    /**
     * 退避重试：保持 PENDING，递增重试次数并顺延下次重试时间。
     *
     * @param id          投递记录 ID
     * @param retryCount  重试次数
     * @param nextRetryAt 下次重试时间
     * @param lastError   错误信息
     * @return 影响行数
     */
    @Update("UPDATE notification_delivery SET status = 'PENDING', retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, last_error = #{lastError} WHERE id = #{id}")
    int scheduleRetry(@Param("id") long id, @Param("retryCount") int retryCount,
                      @Param("nextRetryAt") Instant nextRetryAt,
                      @Param("lastError") String lastError);

    /**
     * 标记死信（重试耗尽）。
     *
     * @param id         投递记录 ID
     * @param retryCount 重试次数
     * @param lastError  错误信息
     * @return 影响行数
     */
    @Update("UPDATE notification_delivery SET status = 'DEAD', retry_count = #{retryCount}, "
            + "last_error = #{lastError} WHERE id = #{id}")
    int markDead(@Param("id") long id, @Param("retryCount") int retryCount,
                 @Param("lastError") String lastError);

    /**
     * 级联删除超保留期已读通知的投递记录（清理任务用，先删子表）。
     *
     * @param cutoff 截止时间
     * @return 影响行数
     */
    @Delete("DELETE FROM notification_delivery WHERE notification_id IN "
            + "(SELECT id FROM notification WHERE read_at IS NOT NULL AND read_at < #{cutoff})")
    int deleteByReadNotificationBefore(@Param("cutoff") Instant cutoff);
}
