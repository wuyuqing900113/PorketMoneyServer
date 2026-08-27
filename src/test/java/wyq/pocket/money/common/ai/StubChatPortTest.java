package wyq.pocket.money.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 确定性意图路由桩单元测试（M4 设计 §4.3）：关键词路由、金额 / 成员名
 * 正则抽取、未识别兜底与降级演练开关。
 */
class StubChatPortTest {

    private static final List<ToolDefinition> TOOLS = List.of();

    private final StubChatPort stub = new StubChatPort(false);

    @Test
    void shouldRouteBalanceQuery() {
        IntentResult result = stub.parseIntent("查一下余额", TOOLS);

        assertThat(result.toolName()).isEqualTo("BALANCE_QUERY");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldRouteFundWriteAndExtractAmountAndTarget() {
        IntentResult result = stub.parseIntent("给小明存50", TOOLS);

        assertThat(result.toolName()).isEqualTo("DEPOSIT");
        assertThat(result.rawParams())
                .containsEntry("amount", "50")
                .containsEntry("targetUserName", "小明");
    }

    @Test
    void shouldRouteWithdrawWithDecimalAmount() {
        IntentResult result = stub.parseIntent("给小明取20.5", TOOLS);

        assertThat(result.toolName()).isEqualTo("WITHDRAW");
        assertThat(result.rawParams()).containsEntry("amount", "20.5");
    }

    @Test
    void shouldReturnNullToolForUnknownText() {
        IntentResult result = stub.parseIntent("明天天气怎么样", TOOLS);

        assertThat(result.toolName()).isNull();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void shouldFailWhenConfiguredToFail() {
        StubChatPort failing = new StubChatPort(true);

        assertThatThrownBy(() -> failing.parseIntent("查余额", TOOLS))
                .isInstanceOf(IllegalStateException.class);
    }
}
