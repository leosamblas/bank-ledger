package com.bankledger.account.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.bankledger.account",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class HexagonalArchitectureTest {

    /**
     * Rule 1: domain has no dependencies on application, adapters, config,
     * nor Spring or Jakarta persistence frameworks.
     */
    @ArchTest
    public static final ArchRule domain_should_not_depend_on_application_adapters_config_or_frameworks =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..com.bankledger.account.application..",
                "..com.bankledger.account.adapters..",
                "..com.bankledger.account.config..",
                "org.springframework..",
                "jakarta.persistence.."
            )
            .allowEmptyShould(false);

    /**
     * Rule 2: application has no dependencies on adapters or config.
     */
    @ArchTest
    public static final ArchRule application_should_not_depend_on_adapters_or_config =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..com.bankledger.account.adapters..",
                "..com.bankledger.account.config.."
            )
            .allowEmptyShould(false);

    /**
     * Rule 3: adapters only access application.port.in and implement application.port.out.
     * Adapters must NOT depend on application services or internals outside ports.
     */
    @ArchTest
    public static final ArchRule adapters_should_only_depend_on_application_ports =
        noClasses()
            .that().resideInAPackage("..adapters..")
            .should().dependOnClassesThat(
                resideInAPackage("..com.bankledger.account.application..")
                    .and(not(resideInAPackage("..com.bankledger.account.application.port..")))
            )
            .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule incoming_adapters_should_not_depend_on_outgoing_ports =
        noClasses()
            .that().resideInAPackage("..adapters.in..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application.port.out..")
            .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule outgoing_adapters_should_not_depend_on_incoming_ports =
        noClasses()
            .that().resideInAPackage("..adapters.out..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application.port.in..")
            .allowEmptyShould(false);
}
