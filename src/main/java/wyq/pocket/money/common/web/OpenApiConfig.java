package wyq.pocket.money.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * SpringDoc OpenAPI 配置：API 元信息、安全方案与文档总则。
 *
 * <p>API 文档总则（供鸿蒙端对接）：
 * 全部接口以统一响应体 Result 包裹；code=0 为成功；
 * 90xxxx 系统段错误可携带幂等键重试；traceId 为排障入口。
 *
 * <p>认证与错误码总则（M1 设计 §4.2 / §4.8）：
 * 受保护接口须携带 {@code Authorization: Bearer <access 令牌>}（本文件
 * bearerAuth 安全方案）；认证拒绝（令牌缺失/无效/过期）返回
 * HTTP 401 + Result(100003)，授权拒绝（角色不符/跨家庭数据访问）返回
 * HTTP 403 + Result(100004)——便于端上拦截器统一触发跳登录/无权限提示；
 * 其余业务错误维持 HTTP 200 + code 约定（100001 参数校验失败、
 * 20xxxx 用户域业务错误、90xxxx 系统错误）。refresh 令牌仅用于
 * POST /api/v1/auth/refresh 与登出，不得作为 Bearer 访问令牌使用。
 *
 * <p>prod profile 关闭 Swagger UI（application-prod.yml）。
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名：受保护接口经 @SecurityRequirement 引用本方案。 */
    public static final String BEARER_SECURITY_SCHEME = "bearerAuth";

    /**
     * OpenAPI 元信息与安全方案。
     *
     * @return OpenAPI 定义
     */
    @Bean
    public OpenAPI pocketMoneyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("零花钱管理系统 API")
                        .description("鸿蒙 APP 零花钱管理后端服务。"
                                + "总则：统一 Result 包裹；code=0 成功；90xxxx 可重试；traceId 排障。"
                                + "认证拒绝 HTTP 401 + 100003；授权拒绝 HTTP 403 + 100004；"
                                + "其余业务错误 HTTP 200 + code（100001 参数校验、20xxxx 用户域）。")
                        .version("v1.0"))
                .components(new Components().addSecuritySchemes(BEARER_SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("HS256 access 令牌（短 TTL）；"
                                        + "refresh 令牌仅用于 /auth/refresh 与登出")));
    }
}
