package wyq.pocket.money.integration.support;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * PG 集成测试库表重置监听器（Docker 对接加固）：
 *
 * <p>全部 PG 套件共享<b>同一个 Testcontainers 单例容器</b>与同一份 schema（见
 * {@link wyq.pocket.money.integration.AbstractPostgresIntegrationTest} 类 Javadoc）。
 * 跨测试类若不清库，先执行类遗留的数据会污染后执行类的断言：固定号段手机号跨类撞
 * {@code 200001 手机号已注册}、遗留 ACTIVE 规则进入结算聚合使 {@code settleDueRules()}
 * 计数偏离（实测 1 vs 11）。H2 套件靠「每类一个独立内存库」天然隔离；PG 单例库则在
 * <b>每个测试类开始前</b>（{@link #beforeTestClass}，先于子类 {@code @BeforeAll}）
 * 清空全部业务表并复位序列，等价于每类一个干净库。
 *
 * <p>采用「每类清库」而非「每方法清库」：既消除跨类污染，又保留类内
 * {@code @BeforeAll}（{@code @TestInstance(PER_CLASS)}）构建的共享夹具
 * （如权限矩阵矩阵行共用的家庭、性能基准 5 万流水）。{@code flyway_schema_history}
 * 不截断，避免破坏 Flyway 迁移校验状态。
 */
public class PgDatabaseResetListener extends AbstractTestExecutionListener {

    /** Flyway 自身的迁移历史表，清库时保留。 */
    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

    @Override
    public void beforeTestClass(TestContext testContext) {
        resetBusinessTables(testContext.getApplicationContext().getBean(JdbcTemplate.class));
    }

    /**
     * 清空 public schema 下全部业务表（CASCADE 处理外键）并重置自增序列。
     *
     * @param jdbcTemplate 测试上下文 JDBC 模板
     */
    public static void resetBusinessTables(JdbcTemplate jdbcTemplate) {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                        + "AND tablename <> ?", String.class, FLYWAY_HISTORY_TABLE);
        if (tables.isEmpty()) {
            return;
        }
        String tableList = tables.stream()
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute(
                "TRUNCATE TABLE " + tableList + " RESTART IDENTITY CASCADE");
    }
}
