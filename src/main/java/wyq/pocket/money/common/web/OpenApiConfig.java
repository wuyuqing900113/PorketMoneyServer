package wyq.pocket.money.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * SpringDoc OpenAPI 配置：API 元信息与文档总则。
 *
 * <p>API 文档总则（供鸿蒙端对接）：
 * 全部接口以统一响应体 Result 包裹；code=0 为成功；
 * 90xxxx 系统段错误可携带幂等键重试；traceId 为排障入口。
 * prod profile 关闭 Swagger UI（application-prod.yml）。
 */
@Configuration
public class OpenApiConfig {

    /**
     * OpenAPI 元信息。
     *
     * @return OpenAPI 定义
     */
    @Bean
    public OpenAPI pocketMoneyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("零花钱管理系统 API")
                .description("鸿蒙 APP 零花钱管理后端服务。"
                        + "总则：统一 Result 包裹；code=0 成功；90xxxx 可重试；traceId 排障。")
                .version("v1.0"));
    }
}
