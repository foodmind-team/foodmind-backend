package com.foodmind.foodmindbackend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(
        packages = "com.foodmind.foodmindbackend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final List<String> FEATURE_PACKAGES = List.of(
            "auth",
            "user",
            "preference",
            "catalog",
            "record",
            "group",
            "wanttotry",
            "search",
            "recommendation",
            "cooking",
            "chat",
            "analytics");

    @ArchTest
    static final ArchRule controllers_do_not_depend_on_persistence_repositories =
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .or()
                    .haveSimpleNameEndingWith("Controller")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence.repository..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_packages_do_not_depend_on_framework_or_integration_code =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web..",
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "..infrastructure.integration..",
                            "..integration..")
                    .allowEmptyShould(true);

    @Test
    void featureModulesDoNotImportAnotherFeatureInfrastructurePackage() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.foodmind.foodmindbackend");

        List<String> violations = importedClasses.stream()
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(this::isAnotherFeatureInfrastructureDependency)
                .map(this::formatDependency)
                .sorted()
                .toList();

        assertThat(violations).isEmpty();
    }

    private boolean isAnotherFeatureInfrastructureDependency(Dependency dependency) {
        String originFeature = featureName(dependency.getOriginClass());
        String targetFeature = featureName(dependency.getTargetClass());
        String targetPackageName = dependency.getTargetClass().getPackageName();

        return originFeature != null
                && targetFeature != null
                && !originFeature.equals(targetFeature)
                && targetPackageName.startsWith("com.foodmind.foodmindbackend." + targetFeature + ".infrastructure.");
    }

    private String featureName(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String basePackage = "com.foodmind.foodmindbackend.";
        if (!packageName.startsWith(basePackage)) {
            return null;
        }

        String remainder = packageName.substring(basePackage.length());
        String topLevelPackage = remainder.contains(".")
                ? remainder.substring(0, remainder.indexOf('.'))
                : remainder;

        return FEATURE_PACKAGES.contains(topLevelPackage) ? topLevelPackage : null;
    }

    private String formatDependency(Dependency dependency) {
        return dependency.getOriginClass().getName() + " -> " + dependency.getTargetClass().getName();
    }
}
