package wyq.pocket.money.support;

import java.util.UUID;

import io.restassured.filter.Filter;

/**
 * 幂等键测试辅助：为每个写请求注入唯一 {@code Idempotency-Key} 请求头。
 *
 * <p>M3 起全部写操作（POST/PUT/DELETE）强制要求幂等键，既有 H2 集成测试
 * 的认证 / 家庭写接口统一经此过滤器自动注入 UUID 键，避免逐一补头。
 * 已显式携带幂等键的请求（幂等专测）不受影响。
 */
public final class IdempotencyKeys {

    private static final String HEADER_NAME = "Idempotency-Key";

    private IdempotencyKeys() {
    }

    /**
     * 构造注入唯一幂等键的 RestAssured 过滤器。
     *
     * @return 过滤器
     */
    public static Filter uniqueKeyPerRequest() {
        return (spec, responseSpec, ctx) -> {
            if (isWrite(spec.getMethod()) && spec.getHeaders().getValue(HEADER_NAME) == null) {
                spec.header(HEADER_NAME, UUID.randomUUID().toString());
            }
            return ctx.next(spec, responseSpec);
        };
    }

    private static boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
    }
}
