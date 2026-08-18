package wyq.pocket.money.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import wyq.pocket.money.common.exception.BusinessException;

/**
 * 全局异常处理器测试：standalone MockMvc 验证异常 → Result 映射
 * （M0 设计 §6.3；M1 设计 §4.8 Security 异常映射）。
 */
class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void normalResponseShouldReturnCodeZero() throws Exception {
        JsonNode node = perform(get("/test/ok"));

        assertThat(node.get("code").asInt()).isZero();
        assertThat(node.get("data").asText()).isEqualTo("ok");
    }

    @Test
    void businessExceptionShouldReturnItsErrorCode() throws Exception {
        JsonNode node = perform(get("/test/business"));

        assertThat(node.get("code").asInt()).isEqualTo(100007);
        assertThat(node.get("message").asText()).isEqualTo("请求过于频繁");
    }

    @Test
    void unexpectedExceptionShouldReturnInternalErrorWithoutDetail() throws Exception {
        JsonNode node = perform(get("/test/internal"));

        assertThat(node.get("code").asInt()).isEqualTo(900001);
        assertThat(node.get("message").asText()).isEqualTo("系统内部错误");
        assertThat(node.toString()).doesNotContain("boom");
    }

    @Test
    void invalidBodyFieldShouldReturnParamInvalidWithFieldDetail() throws Exception {
        String body = mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(body);

        assertThat(node.get("code").asInt()).isEqualTo(100001);
        assertThat(node.get("message").asText()).contains("amount").contains("金额不能为空");
    }

    @Test
    void malformedJsonShouldReturnRequestMalformed() throws Exception {
        String body = mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(body);

        assertThat(node.get("code").asInt()).isEqualTo(100002);
    }

    @Test
    void noResourceShouldReturn100005() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Result<Void> result = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "missing", "资源不存在"));

        assertThat(result.code()).isEqualTo(100005);
    }

    @Test
    void constraintViolationShouldReturn100001WithDetail() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ConstraintViolation<?> violation = mockViolation("amount", "不能为空");

        Result<Void> result = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)));

        assertThat(result.code()).isEqualTo(100001);
        assertThat(result.message()).contains("amount").contains("不能为空");
    }

    @Test
    void accessDeniedShouldReturn403With100004() throws Exception {
        JsonNode node = performExpecting(get("/test/denied"), status().isForbidden());

        assertThat(node.get("code").asInt()).isEqualTo(100004);
        assertThat(node.get("message").asText()).isEqualTo("无权限执行该操作");
    }

    @Test
    void authenticationExceptionShouldReturn401With100003() throws Exception {
        JsonNode node = performExpecting(get("/test/unauthenticated"), status().isUnauthorized());

        assertThat(node.get("code").asInt()).isEqualTo(100003);
        assertThat(node.get("message").asText()).isEqualTo("未认证或登录态失效");
    }

    private JsonNode perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return performExpecting(request, status().isOk());
    }

    private JsonNode performExpecting(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            ResultMatcher statusMatcher) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(statusMatcher)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    @SuppressWarnings("unchecked")
    private ConstraintViolation<?> mockViolation(String propertyPath, String message) {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(propertyPath);
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    /**
     * 测试桩控制器。
     */
    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        Result<String> ok() {
            return Result.success("ok");
        }

        @GetMapping("/test/business")
        Result<Void> business() {
            throw new BusinessException(CommonErrorCode.RATE_LIMITED);
        }

        @GetMapping("/test/internal")
        Result<Void> internal() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/test/denied")
        Result<Void> denied() {
            throw new AccessDeniedException("not allowed");
        }

        @GetMapping("/test/unauthenticated")
        Result<Void> unauthenticated() {
            throw new BadCredentialsException("bad credentials");
        }

        @PostMapping("/test/valid")
        Result<Void> valid(@Valid @RequestBody SampleRequest request) {
            return Result.success();
        }
    }

    /**
     * 参数校验测试对象。
     */
    record SampleRequest(@NotNull(message = "金额不能为空") BigDecimal amount) {
    }
}
