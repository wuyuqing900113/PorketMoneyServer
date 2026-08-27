package wyq.pocket.money.common.scheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

/**
 * 启用 Spring 定时任务（M2 设计 §13 / D14）：包月结算、规则到期归档、对账。
 *
 * <p>TaskScheduler 使用虚拟线程执行器（JDK 25）；
 * 任务启停由各 Job Bean 的 @ConditionalOnProperty 控制。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 虚拟线程任务调度器。
     *
     * @return TaskScheduler Bean
     */
    @Bean
    public TaskScheduler taskScheduler() {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("m2-sched-");
        return scheduler;
    }
}
