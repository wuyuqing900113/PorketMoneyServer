package wyq.pocket.money.common.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 系统健康冒烟 Mapper：验证 MyBatis 与数据库连通性（M0 集成冒烟用）。
 *
 * <p>SQL 一律使用注解/XML 参数化（#{}），禁止拼接（tech-stack.md 安全约束）。
 */
@Mapper
public interface SystemHealthMapper {

    /**
     * 连通性探测。
     *
     * @return 恒为 1
     */
    @Select("SELECT 1")
    Integer ping();
}
