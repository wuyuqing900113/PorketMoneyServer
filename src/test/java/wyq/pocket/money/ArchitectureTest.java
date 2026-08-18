package wyq.pocket.money;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * 架构守护测试：固化 M0 设计 §5.3 的分层与模块依赖规则，防止后续开发腐化。
 *
 * <p>各规则均启用 {@code allowEmptyShould(true)}：M0 阶段业务模块仅含
 * package-info 骨架（无真实类），规则允许零匹配；M1+ 有真实类后规则自动生效。
 */
class ArchitectureTest {

    private static final List<String> BUSINESS_MODULES =
            List.of("user", "money", "rule", "finance", "ai", "notify");

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("wyq.pocket.money");
    }

    @Test
    void businessModulesShouldNotDependOnEachOther() {
        for (String module : BUSINESS_MODULES) {
            String[] others = BUSINESS_MODULES.stream()
                    .filter(other -> !other.equals(module))
                    .map(other -> "wyq.pocket.money." + other + "..")
                    .toArray(String[]::new);
            noClasses()
                    .that().resideInAPackage("wyq.pocket.money." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(others)
                    .because("业务模块之间禁止直接依赖，跨模块协作须经 common 层或事件（M5）")
                    .allowEmptyShould(true)
                    .check(importedClasses);
        }
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
