package wyq.pocket.money.common.log;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏规则（mission.md 安全约束：日志中屏蔽敏感隐私信息）。
 *
 * <p>规则按序执行：身份证 → 手机号 → 银行卡 → 密钥类键值对。
 * 先处理身份证（18 位），避免其数字串被银行卡规则误匹配。
 */
public final class MaskingRules {

    private MaskingRules() {
    }

    private record MaskRule(Pattern pattern, String replacement) {

        MaskRule(String regex, String replacement) {
            this(Pattern.compile(regex), replacement);
        }

        String apply(String input) {
            return pattern.matcher(input).replaceAll(replacement);
        }
    }

    private static final List<MaskRule> RULES = List.of(
            // 身份证号（18 位，末位可为 X/x）：保留前 3 后 4
            new MaskRule("(?<!\\d)(\\d{3})\\d{11}(\\d{3}[0-9Xx])(?!\\d)", "$1***********$2"),
            // 大陆手机号（11 位）：保留前 3 后 4
            new MaskRule("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2"),
            // 银行卡号（16~19 位）：仅保留后 4
            new MaskRule("(?<!\\d)\\d{12,15}(\\d{4})(?!\\d)", "************$1"),
            // 密钥类键值对（password/secret/token/api_key/authorization）：值整体替换
            new MaskRule(
                    "(?i)(password|passwd|pwd|secret|api[_-]?key|token|authorization)"
                            + "([\"']?\\s*[:=：]\\s*)([\"']?)[^\"'\\s,;)}\\]]+",
                    "$1$2$3******"));

    /**
     * 对文本执行全部脱敏规则。
     *
     * @param text 原始文本，可为 null
     * @return 脱敏后文本；null 或空串原样返回
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (MaskRule rule : RULES) {
            result = rule.apply(result);
        }
        return result;
    }
}
