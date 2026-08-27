package wyq.pocket.money.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import wyq.pocket.money.common.crypto.Hashes;
import wyq.pocket.money.common.idempotency.mapper.IdempotencyRecordMapper;

/**
 * 幂等服务：两阶段幂等核心（M3 设计 §5）。
 *
 * <p>受理：INSERT IN_PROGRESS，唯一键冲突即读既有记录判定重放 / 冲突 /
 * 受理中 / 超期接管；成功：UPDATE 回填响应并置 PROCESSED；失败：DELETE 释放键。
 * 回填与释放为尽力而为（DB 异常仅记日志不阻断业务），残余 IN_PROGRESS 由
 * 接管宽限 + 资金写 request_id 唯一索引兜底。
 */
@Component
public class IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);

    /** 指纹分隔符（换行，与设计 §5 约定一致）。 */
    private static final byte NEWLINE = (byte) '\n';

    private final IdempotencyRecordMapper mapper;

    private final IdempotencyProperties properties;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param mapper     幂等记录 Mapper
     * @param properties 幂等配置
     * @param clock      时钟
     */
    public IdempotencyService(IdempotencyRecordMapper mapper,
                              IdempotencyProperties properties, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 受理一笔记请求（幂等第一阶段）。
     *
     * @param userId 用户 ID
     * @param key    幂等键
     * @param method 请求方法
     * @param path   请求路径
     * @param body   请求体原文
     * @return 受理结果（放行 / 重放 / 冲突 / 受理中）
     */
    public IdempotencyOutcome begin(long userId, String key, String method, String path,
                                    byte[] body) {
        String hash = payloadHash(method, path, body);
        try {
            mapper.insert(userId, key, method, path, hash, expiresAt());
            return IdempotencyOutcome.proceed();
        } catch (DuplicateKeyException conflict) {
            return onConflict(userId, key, method, path, hash);
        }
    }

    /**
     * 业务成功后回填响应并置 PROCESSED（尽力而为）。
     *
     * @param userId   用户 ID
     * @param key      幂等键
     * @param respCode 响应错误码
     * @param respBody 原始响应体 JSON
     */
    public void markProcessed(long userId, String key, int respCode, String respBody) {
        try {
            mapper.markProcessed(userId, key, respCode, respBody);
        } catch (RuntimeException e) {
            LOG.error("IDEMPOTENCY_MARK_FAILED user={} key={}", userId, key, e);
        }
    }

    /**
     * 业务失败后删除记录释放键（尽力而为）。
     *
     * @param userId 用户 ID
     * @param key    幂等键
     */
    public void markFailed(long userId, String key) {
        try {
            mapper.deleteByUserAndKey(userId, key);
        } catch (RuntimeException e) {
            LOG.error("IDEMPOTENCY_DELETE_FAILED user={} key={}", userId, key, e);
        }
    }

    private IdempotencyOutcome onConflict(long userId, String key, String method, String path,
                                          String hash) {
        IdempotencyRecord existing = mapper.findByUserAndKey(userId, key);
        if (existing == null) {
            return IdempotencyOutcome.inProgress();
        }
        if (IdempotencyRecord.STATUS_PROCESSED.equals(existing.getStatus())) {
            return hash.equals(existing.getPayloadHash())
                    ? IdempotencyOutcome.replay(existing) : IdempotencyOutcome.conflict();
        }
        if (!isStale(existing.getCreatedAt())) {
            return IdempotencyOutcome.inProgress();
        }
        return takeover(userId, key, method, path, hash);
    }

    private IdempotencyOutcome takeover(long userId, String key, String method, String path,
                                        String hash) {
        mapper.deleteByUserAndKey(userId, key);
        try {
            mapper.insert(userId, key, method, path, hash, expiresAt());
            return IdempotencyOutcome.proceed();
        } catch (DuplicateKeyException again) {
            LOG.warn("IDEMPOTENCY_TAKEOVER_LOST user={} key={}", userId, key);
            return IdempotencyOutcome.inProgress();
        }
    }

    private boolean isStale(Instant createdAt) {
        return createdAt != null
                && createdAt.plus(properties.takeoverGrace()).isBefore(clock.instant());
    }

    private Instant expiresAt() {
        return clock.instant().plus(properties.ttl());
    }

    private String payloadHash(String method, String path, byte[] body) {
        byte[] methodBytes = method.getBytes(StandardCharsets.UTF_8);
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[methodBytes.length + pathBytes.length + body.length + 2];
        int offset = 0;
        offset = copyInto(methodBytes, combined, offset);
        combined[offset++] = NEWLINE;
        offset = copyInto(pathBytes, combined, offset);
        combined[offset++] = NEWLINE;
        copyInto(body, combined, offset);
        return Hashes.sha256Hex(combined);
    }

    private int copyInto(byte[] source, byte[] target, int offset) {
        System.arraycopy(source, 0, target, offset, source.length);
        return offset + source.length;
    }
}
