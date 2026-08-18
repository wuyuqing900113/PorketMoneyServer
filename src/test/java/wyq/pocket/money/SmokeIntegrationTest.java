package wyq.pocket.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import wyq.pocket.money.common.persistence.SystemHealthMapper;

/**
 * M0 冒烟集成测试：应用上下文 + Flyway 迁移 + MyBatis 连通 + 健康检查 + API 文档。
 * 使用 H2 内存库（PostgreSQL 兼容模式），覆盖 M0 DoD 的"一键启动可验证"目标。
 * 注：Boot 4 测试切片包路径重构，此处改用 webAppContextSetup 手工构建 MockMvc。
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:h2:mem:pocket_money;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD="
})
class SmokeIntegrationTest {

    @Autowired
    private SystemHealthMapper systemHealthMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void contextLoadsAndMyBatisWorks() {
        assertThat(systemHealthMapper.ping()).isEqualTo(1);
    }

    @Test
    void livenessProbeShouldBeUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void readinessProbeShouldBeUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiDocsShouldBeAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
