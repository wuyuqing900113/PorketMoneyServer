package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.web.CommonErrorCode;

/**
 * 封闭意图目录单元测试（M4 设计 §5.1/§5.2）：工具定义清单、意图映射、
 * 资金写参数校验（金额正数 + 目标成员必填）。
 */
class IntentCatalogTest {

    private final IntentCatalog catalog = new IntentCatalog();

    @Test
    void shouldExposeElevenDefinitions() {
        assertThat(catalog.toolDefinitions()).hasSize(11);
    }

    @Test
    void shouldMapToolNameToIntent() {
        assertThat(catalog.requireIntent("BALANCE_QUERY")).isEqualTo(AiIntent.BALANCE_QUERY);
        assertThat(catalog.requireIntent("DEPOSIT")).isEqualTo(AiIntent.DEPOSIT);
    }

    @Test
    void shouldRejectUnknownOrBlankToolName() {
        assertThatThrownBy(() -> catalog.requireIntent(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(AiErrorCode.INTENT_UNRECOGNIZED));
        assertThatThrownBy(() -> catalog.requireIntent("NOPE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(AiErrorCode.INTENT_UNRECOGNIZED));
    }

    @Test
    void shouldSkipValidationForQueryIntent() {
        assertThatCode(() -> catalog.validate(AiIntent.BALANCE_QUERY, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectFundWriteWithoutAmountOrTarget() {
        assertThatThrownBy(() -> catalog.validate(AiIntent.DEPOSIT,
                Map.of("targetUserName", "小明")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(CommonErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> catalog.validate(AiIntent.DEPOSIT, Map.of("amount", "50")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(CommonErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> catalog.validate(AiIntent.DEPOSIT,
                Map.of("amount", "0", "targetUserName", "小明")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(CommonErrorCode.PARAM_INVALID));
    }

    @Test
    void shouldAcceptValidFundWrite() {
        assertThatCode(() -> catalog.validate(AiIntent.DEPOSIT,
                Map.of("amount", "50", "targetUserName", "小明")))
                .doesNotThrowAnyException();
    }
}
