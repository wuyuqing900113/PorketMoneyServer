package wyq.pocket.money.notify.service.push;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import wyq.pocket.money.notify.config.NotifyProperties;

/**
 * 鸿蒙 Push Kit 服务端适配器（GA D68）：实现 {@link PushPort}，经 HMS Push Kit
 * REST API 向鸿蒙设备下发通知。
 *
 * <p>协议要点：
 * <ul>
 *   <li>鉴权：OAuth2 client_credentials 表单换 access_token（缓存至过期前 60s，
 *       下发遇 401 失效并刷新重试一次）；</li>
 *   <li>下发：{@code POST {push-base-url}/v1/{app-id}/messages:send}，Bearer 令牌，
 *       成功响应 {@code code="80000000"}；</li>
 *   <li>结果语义：受理返回 true；HTTP 非 2xx / 业务码非 80000000 返回 false（relay 退避重试）；
 *       传输异常抛 RuntimeException（relay 同样按失败重试，耗尽置 DEAD）。</li>
 * </ul>
 *
 * <p>安全：client_secret / access_token / 设备令牌属敏感凭据，一律不进入日志
 * （mission 脱敏约束）；日志仅记录 notificationId / userId / HTTP 状态码 / 业务码。
 */
public class HarmonyPushPort implements PushPort {

    private static final Logger LOG = LoggerFactory.getLogger(HarmonyPushPort.class);

    /** 单次 HTTP 请求超时。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** 令牌提前刷新余量，避免临界过期。 */
    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofSeconds(60);

    /** 华为 Push Kit 成功业务码。 */
    private static final String SUCCESS_CODE = "80000000";

    /** 令牌失效 HTTP 状态码（触发一次刷新重试）。 */
    private static final int HTTP_UNAUTHORIZED = 401;

    private final HttpClient httpClient;

    private final JsonMapper jsonMapper;

    private final NotifyProperties.Push.Harmony harmony;

    /** 缓存的 access_token（volatile 读，synchronized 写）。 */
    private volatile CachedToken cachedToken;

    /**
     * 生产构造：默认 JDK HttpClient。
     *
     * @param harmony 鸿蒙 Push Kit 配置（凭据 / 端点）
     */
    public HarmonyPushPort(NotifyProperties.Push.Harmony harmony) {
        this(harmony, HttpClient.newHttpClient(), new JsonMapper());
    }

    /**
     * 可注入 HttpClient / JsonMapper 的构造（测试指向 WireMock）。
     *
     * @param harmony    鸿蒙 Push Kit 配置
     * @param httpClient HTTP 客户端
     * @param jsonMapper JSON 序列化
     */
    HarmonyPushPort(NotifyProperties.Push.Harmony harmony, HttpClient httpClient,
                    JsonMapper jsonMapper) {
        this.harmony = harmony;
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean send(long notificationId, long userId, String deviceToken,
                        String title, String content) {
        if (deviceToken == null || deviceToken.isBlank()) {
            LOG.warn("PUSH_SKIP userId={} notificationId={} 无设备令牌", userId, notificationId);
            return false;
        }
        return sendOrThrow(notificationId, userId, deviceToken, title, content);
    }

    /**
     * 执行下发并把传输异常统一转译：中断重置中断标志，受检异常转
     * IllegalStateException 交由 relay 捕获按失败重试（耗尽置 DEAD）。
     */
    private boolean sendOrThrow(long notificationId, long userId, String deviceToken,
                                String title, String content) {
        try {
            return doSend(notificationId, userId, deviceToken, title, content);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("鸿蒙推送请求被中断 notificationId=" + notificationId, e);
        } catch (IOException e) {
            throw new IllegalStateException("鸿蒙推送请求失败 notificationId=" + notificationId, e);
        }
    }

    private boolean doSend(long notificationId, long userId, String deviceToken,
                           String title, String content)
            throws IOException, InterruptedException {
        SendResult result = postMessage(accessToken(), deviceToken, title, content);
        if (result.httpStatus() == HTTP_UNAUTHORIZED) {
            LOG.info("PUSH_TOKEN_EXPIRED notificationId={}，刷新令牌后重试一次", notificationId);
            invalidateToken();
            result = postMessage(accessToken(), deviceToken, title, content);
        }
        if (result.accepted()) {
            LOG.info("PUSH_ACCEPTED userId={} notificationId={}", userId, notificationId);
            return true;
        }
        LOG.warn("PUSH_REJECTED userId={} notificationId={} httpStatus={} code={}",
                userId, notificationId, result.httpStatus(), result.bizCode());
        return false;
    }

    /**
     * 取有效 access_token：缓存命中直接返回，否则加锁刷新（双重检查）。
     */
    private String accessToken() throws IOException, InterruptedException {
        CachedToken cached = cachedToken;
        if (cached != null && cached.validUntil().isAfter(Instant.now())) {
            return cached.token();
        }
        return refreshToken();
    }

    private synchronized String refreshToken() throws IOException, InterruptedException {
        CachedToken cached = cachedToken;
        if (cached != null && cached.validUntil().isAfter(Instant.now())) {
            return cached.token();
        }
        CachedToken fetched = fetchToken();
        cachedToken = fetched;
        return fetched.token();
    }

    /**
     * 向 OAuth2 端点换取 access_token 并折算失效时间（已扣提前量）。
     */
    private CachedToken fetchToken() throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(buildTokenRequest(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("鸿蒙 OAuth token 获取失败 HTTP " + response.statusCode());
        }
        JsonNode node = parseJson(response.body());
        String accessToken = requireAccessToken(node);
        long expiresIn = longField(node, "expires_in", 3600L);
        return new CachedToken(accessToken,
                Instant.now().plusSeconds(expiresIn).minus(TOKEN_EXPIRY_MARGIN));
    }

    /**
     * 从 token 响应取 access_token：缺失 / 空白一律抛 IllegalStateException。
     *
     * @param node token 响应 JSON 根节点
     * @return 非空 access_token
     */
    private String requireAccessToken(JsonNode node) {
        String accessToken = textField(node, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("鸿蒙 OAuth token 响应缺少 access_token");
        }
        return accessToken;
    }

    private void invalidateToken() {
        cachedToken = null;
    }

    private HttpRequest buildTokenRequest() {
        String form = "grant_type=client_credentials"
                + "&client_id=" + urlEncode(harmony.clientId())
                + "&client_secret=" + urlEncode(harmony.clientSecret());
        return HttpRequest.newBuilder(URI.create(harmony.tokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
    }

    private SendResult postMessage(String accessToken, String deviceToken,
                                   String title, String content)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(messageEndpoint())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildMessageBody(deviceToken, title, content)))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        return new SendResult(response.statusCode(), extractBizCode(response.body()));
    }

    private URI messageEndpoint() {
        return URI.create(harmony.pushBaseUrl() + "/v1/" + harmony.appId() + "/messages:send");
    }

    private String buildMessageBody(String deviceToken, String title, String content) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", content);
        Map<String, Object> message = new HashMap<>();
        message.put("token", List.of(deviceToken));
        message.put("notification", notification);
        return jsonMapper.writeValueAsString(Map.of("message", message));
    }

    private String extractBizCode(String body) {
        JsonNode node = parseJson(body);
        return node == null ? null : textField(node, "code");
    }

    private JsonNode parseJson(String body) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException | IllegalArgumentException e) {
            LOG.warn("鸿蒙推送响应非预期 JSON（已按失败处理）");
            return null;
        }
    }

    private String textField(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private long longField(JsonNode node, String field, long defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asLong();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 缓存令牌。
     *
     * @param token      access_token
     * @param validUntil 失效时间（已扣提前量）
     */
    private record CachedToken(String token, Instant validUntil) {
    }

    /**
     * 下发结果。
     *
     * @param httpStatus HTTP 状态码
     * @param bizCode    响应业务码（无法解析为 null）
     */
    private record SendResult(int httpStatus, String bizCode) {

        /**
         * 是否受理：HTTP 2xx 且业务码为 80000000。
         *
         * @return true 受理
         */
        boolean accepted() {
            return httpStatus >= 200 && httpStatus < 300 && SUCCESS_CODE.equals(bizCode);
        }
    }
}
