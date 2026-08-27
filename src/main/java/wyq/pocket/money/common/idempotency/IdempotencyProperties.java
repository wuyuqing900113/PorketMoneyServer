package wyq.pocket.money.common.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 幂等配置项（M3 设计 §5）。
 *
 * @param keyMaxLength  幂等键最大长度（超出视为非法）
 * @param ttl           幂等记录存活期（超期由清理任务删除）
 * @param takeoverGrace IN_PROGRESS 接管宽限期：超过即视为前次请求已死，可删除重试
 */
@ConfigurationProperties(prefix = "pocket-money.idempotency")
public record IdempotencyProperties(int keyMaxLength, Duration ttl, Duration takeoverGrace) {
}
