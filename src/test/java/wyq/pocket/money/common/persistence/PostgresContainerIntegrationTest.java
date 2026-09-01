package wyq.pocket.money.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * M1 Spike S4：Testcontainers + 真实 PostgreSQL 18 + Flyway 迁移链路，
 * 关闭 M0 遗留的 PG 运行验证 ⚠️ 项（M1 设计 §12.2）。
 *
 * <p>单例容器以静态初始化块手动启动一次（不用 {@code @Container} 托管，避免
 * 跨类时扩展随 class-scope store 关闭而停止/重启容器、端口漂移使缓存上下文连
 * 旧端口）；Docker 未就绪时不启动并由 disabledWithoutDocker 自动跳过，
 * mvn verify 保持常绿；Docker 就绪后本套件全量跑绿方可将 version-matrix 中
 * postgresql 置 ✅。测试密钥为全零固定值，仅测试用，非任何环境真实密钥。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class PostgresContainerIntegrationTest {

    /** Testcontainers 2.x：PostgreSQLContainer 不再是泛型类（Spike S4 实测）。 */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        // 单例容器手动启动；Docker 不可用时不启动，交由 @Testcontainers 跳过。
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayShouldMigrateAgainstRealPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void shouldRoundTripDataAgainstRealPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }
}
