package wyq.pocket.money;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 零花钱管理系统后端服务启动类。
 *
 * <p>为鸿蒙 APP 提供零花钱管理能力的单体后端服务，
 * 包命名空间与模块划分见 M0-detailed-design.md §5。
 */
@SpringBootApplication
public class PocketMoneyApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PocketMoneyApplication.class, args);
    }
}
