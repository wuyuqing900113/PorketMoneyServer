package wyq.pocket.money.common.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局时钟 Bean（M2 设计 D15）：业务日期计算统一入口，便于测试注入固定时钟。
 *
 * <p>采用 Asia/Shanghai 业务时区（设计 §3.2，v1.2 回改对齐）：
 * 结算发放日、趋势日/周分桶等“业务日”边界必须按北京时间计算，
 * 否则 00:00–08:00 的交易会落入前一日。时间戳本身仍是 TIMESTAMPTZ（时区无关）。
 */
@Configuration
public class ClockConfig {

    /** 业务时区：北京时间。 */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 系统时钟（业务时区）。
     *
     * @return Clock Bean
     */
    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
