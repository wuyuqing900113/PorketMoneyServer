package wyq.pocket.money;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * 架构守护测试：固化 M0 设计 §5.3 的分层规则与 M2 设计 §3.4 的模块依赖增量，
 * 防止后续开发腐化。
 *
 * <p>M2 允许的业务模块依赖方向（设计 §3.4）：
 * finance→money/user（报表只经 money 服务层取数），money→user，rule→user/money；
 * user 不依赖任何其他业务模块（仅发布领域事件，监听方向单向，D11）。
 *
 * <p>各规则均启用 {@code allowEmptyShould(true)}：ai/notify 仍为空骨架，
 * 规则允许零匹配；有真实类后规则自动生效。
 */
class ArchitectureTest {

    private static final List<String> BUSINESS_MODULES =
            List.of("user", "money", "rule", "finance", "ai", "notify");

    /** M2 设计 §3.4 允许的业务模块间依赖方向（common 层为公共叶子，不在此列）。 */
    private static final Map<String, List<String>> ALLOWED_MODULE_DEPS = Map.of(
            "finance", List.of("money", "user"),
            "money", List.of("user"),
            "rule", List.of("user", "money"),
            "user", List.of(),
            "ai", List.of(),
            "notify", List.of());

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("wyq.pocket.money");
    }

    @Test
    void businessModulesShouldOnlyDependOnAllowedDirections() {
        for (String module : BUSINESS_MODULES) {
            List<String> allowed = ALLOWED_MODULE_DEPS.get(module);
            String[] forbidden = BUSINESS_MODULES.stream()
                    .filter(other -> !other.equals(module) && !allowed.contains(other))
                    .map(other -> "wyq.pocket.money." + other + "..")
                    .toArray(String[]::new);
            noClasses()
                    .that().resideInAPackage("wyq.pocket.money." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                    .because("M2 设计 §3.4 依赖方向：finance→money/user，money→user，"
                            + "rule→user/money，其余模块间禁止直接依赖")
                    .allowEmptyShould(true)
                    .check(importedClasses);
        }
    }

    @Test
    void financeShouldNotAccessMoneyOrRuleMappers() {
        noClasses()
                .that().resideInAPackage("wyq.pocket.money.finance..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "wyq.pocket.money.money.mapper..", "wyq.pocket.money.rule.mapper..")
                .because("finance 不得直连 money/rule 的 mapper，取数须经 money 服务层门面"
                        + "（M2 设计 §3.4 规则①）")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    @Test
    void userShouldNotDependOnOtherBusinessModules() {
        noClasses()
                .that().resideInAPackage("wyq.pocket.money.user..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "wyq.pocket.money.money..", "wyq.pocket.money.rule..",
                        "wyq.pocket.money.finance..", "wyq.pocket.money.ai..",
                        "wyq.pocket.money.notify..")
                .because("user 仅发布领域事件，不得依赖任何业务模块（M2 设计 §3.4 规则②）")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    @Test
    void commonShouldNotDependOnBusinessModules() {
        String[] modulePackages = BUSINESS_MODULES.stream()
                .map(module -> "wyq.pocket.money." + module + "..")
                .toArray(String[]::new);
        noClasses()
                .that().resideInAPackage("wyq.pocket.money.common..")
                .should().dependOnClassesThat().resideInAnyPackage(modulePackages)
                .because("公共层是依赖叶子，禁止反向依赖任何业务模块")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    @Test
    void mapperAndDomainShouldNotDependOnUpperLayers() {
        noClasses()
                .that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
                .because("数据访问层禁止反向依赖上层")
                .allowEmptyShould(true)
                .check(importedClasses);
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
                .because("领域对象禁止反向依赖上层")
                .allowEmptyShould(true)
                .check(importedClasses);
    }
}
