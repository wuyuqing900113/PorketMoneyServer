package wyq.pocket.money.notify.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import wyq.pocket.money.notify.domain.UserPushToken;
import wyq.pocket.money.notify.mapper.UserPushTokenMapper;

/**
 * 外部推送设备令牌注册服务（GA D68）：鸿蒙客户端登录后经 HMS Core 获取 push token
 * 并上报本服务，relay 投递时按 user_id + 渠道取令牌下发。
 *
 * <p>一人一渠道一条：重复注册覆盖更新（令牌轮换 / 换机会话）。设备令牌属敏感凭据，
 * 日志只记用户 ID，不回显令牌原文（脱敏约定见 mission 安全约束）。
 */
@Service
public class PushTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(PushTokenService.class);

    private final UserPushTokenMapper tokenMapper;

    /**
     * 注入 Mapper。
     *
     * @param tokenMapper 推送令牌 Mapper
     */
    public PushTokenService(UserPushTokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    /**
     * 注册 / 更新当前用户的鸿蒙推送令牌。
     *
     * @param userId      用户 ID
     * @param deviceToken HMS Push Kit 设备令牌
     */
    public void registerHarmonyToken(long userId, String deviceToken) {
        int inserted = tokenMapper.insertIgnoreConflict(userId,
                UserPushToken.PROVIDER_HARMONY, deviceToken);
        if (inserted == 0) {
            tokenMapper.updateToken(userId, UserPushToken.PROVIDER_HARMONY, deviceToken);
        }
        LOG.info("PUSH_TOKEN_REGISTERED userId={} provider={}",
                userId, UserPushToken.PROVIDER_HARMONY);
    }
}
