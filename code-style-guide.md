# 项目编码规范指南

## 1. 命名规范
### 通用命名原则
- 类名：使用PascalCase（大驼峰），如 `UserService`, `PaymentController`
- 接口：推荐使用形容词或后缀为able的名词，如 `Runnable`, `Iterable`
- 方法和变量名：使用camelCase（小驼峰），如 `getUserInfo()`, `maxRetryCount`
- 常量：使用UPPER_SNAKE_CASE（大写+下划线），如 `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT`
- 包名：全小写字母，以 `wyq.pocket.money` 开头，如 `wyq.pocket.money.service.user`
- 布尔方法：以is/has/can/will/should等前缀开头，如 `isActive()`, `hasPermission()`

### 示例
```java
// 正确命名
package wyq.pocket.money.service;

public class UserService {
    private static final int MAX_RETRY_COUNT = 3;
    private boolean isActive;
    
    public boolean isActive() {
        return isActive;
    }
    
    public boolean hasPermission(User user) {
        // ...
    }
}
```

## 2. 格式规范
- 使用4个空格缩进（禁止使用Tab键）
- 大括号使用K&R风格（左括号在行尾）
- 运算符两侧添加空格，如 `a + b`
- 单行代码长度不超过120个字符
- import语句按标准分组，禁用*通配符导入
- 方法之间用空行分隔

## 3. 注释规范
- 所有公共类和公共方法必须有Javadoc注释
- 复杂算法或非直观逻辑必须添加解释性注释
- 使用TODO、FIXME、NOTE等标记临时解决方案或待办事项
- 避免冗余注释，代码本身应该自解释

### 示例
```java
/**
 * 用户服务类，负责用户相关业务逻辑处理
 */
package wyq.pocket.money.service.user;

public class UserService {
    /**
     * 获取用户基本信息
     *
     * @param userId 用户ID，不能为空
     * @return 用户信息，若不存在则返回null
     * @throws IllegalArgumentException 当userId为空时抛出
     */
    public UserInfo getUserInfo(String userId) {
        // ...
    }
}
```

## 4. 编码原则
- 消除魔法值，使用常量或枚举替代
- 使用卫语句减少嵌套层级
- 方法遵循单一职责原则，长度控制在80行以内
- 禁止使用空catch块吞掉异常
- 严格分层隔离，区分DO（数据对象）、DTO（数据传输对象）、VO（视图对象）
- 使用StringBuilder进行字符串拼接（特别是循环内）
- 集合判空后再进行遍历操作

## 5. Java特定规范
- 优先使用枚举管理状态值
- 合理使用Lombok减少样板代码
- 工具类使用静态方法，且构造函数私有化
- 使用泛型提高类型安全性
- 谨慎使用反射，仅在必要时使用

## 6. ArkTS特定规范
- 合理使用状态装饰器（@State, @Prop, @Link等）
- 主线程避免执行耗时操作，使用异步处理
- 组件状态管理要清晰，避免状态混乱
- 合理使用组件生命周期方法

## 7. 安全规范
- 密钥和敏感配置信息禁止硬编码到源码中
- 日志中屏蔽敏感隐私信息（如密码、身份证、银行卡号等）
- 所有外部输入参数必须进行有效性校验
- SQL查询使用预编译语句防止SQL注入
- 防止XSS和CSRF攻击

## 8. 错误处理
- 禁止空catch块，必须记录日志或妥善处理异常
- 自定义业务异常类，提供明确的错误码和消息
- API边界处进行异常转换，返回统一的错误响应格式
- 使用try-with-resources确保资源正确释放

## 9. 测试规范
- 单元测试遵循AAA模式（Arrange-App-Assert）
- 每个公共方法都应有对应的单元测试覆盖
- 测试方法命名清晰表达预期行为
- 覆盖率目标不低于80%
- 使用测试数据工厂创建测试对象

## 10. 性能优化
- 避免在循环中进行重复计算
- 合理使用缓存，注意缓存一致性和失效策略
- 避免创建不必要的对象，重用已有实例
- 大集合操作考虑使用流式处理
- 数据库查询使用索引，避免N+1查询问题

## 11. 版本控制
- Git提交信息采用约定式提交格式：`<type>(<scope>): <subject>`
- 分支命名规范：`feature/xxx`, `bugfix/xxx`, `hotfix/xxx`
- Pull Request需经过至少一人代码审查
- 避免在master/main分支直接开发

## 12. 代码审查要点
- 功能实现是否符合需求
- 是否遵循编码规范
- 代码可读性和可维护性
- 异常处理是否得当
- 安全漏洞检查
- 性能影响评估
- 单元测试覆盖情况

## 输出风格
输出精简，不打印大段日志堆栈；潜在问题写注释/说明，不要靠反复试探修改代码。

## 执行要求
所有输出代码必须满足以上规范，功能实现的同时保证代码整洁规范。违反规范的代码将被自动修正或要求重新实现。