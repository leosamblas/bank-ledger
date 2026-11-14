package com.bankledger.account.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
                "jakarta.persistence..",
                "org.hibernate.."
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
    public static final ArchRule incoming_adapters_should_not_depend_on_outgoing_adapters =
        noClasses()
            .that().resideInAPackage("..adapters.in..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapters.out..")
            .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule outgoing_adapters_should_not_depend_on_incoming_ports =
        noClasses()
            .that().resideInAPackage("..adapters.out..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application.port.in..")
            .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule outgoing_adapters_should_not_depend_on_incoming_adapters =
        noClasses()
            .that().resideInAPackage("..adapters.out..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapters.in..")
            .allowEmptyShould(false);

    /**
     * Rule 8: JPA entities must reside in persistence.entity package.
     */
    @ArchTest
    public static final ArchRule entities_must_reside_in_persistence_entity_package =
        classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..adapters.out.persistence.entity..")
            .allowEmptyShould(false);

    /**
     * Rule 9: Spring Data repositories must reside in persistence.repository package.
     */
    @ArchTest
    public static final ArchRule repositories_must_reside_in_persistence_repository_package =
        classes()
            .that().areInterfaces()
            .and().areAssignableTo(Repository.class)
            .should().resideInAPackage("..adapters.out.persistence.repository..")
            .allowEmptyShould(false);

    /**
     * Rule 10: Production code must never use @Autowired annotation.
     */
    @ArchTest
    public static final ArchRule no_classes_should_use_autowired =
        noClasses()
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired")
            .allowEmptyShould(false);

    /**
     * Rule 11: JPA entities must follow standard naming convention ending with Entity.
     */
    @ArchTest
    public static final ArchRule entities_must_have_name_ending_with_entity =
        classes()
            .that().areAnnotatedWith(Entity.class)
            .should().haveSimpleNameEndingWith("Entity")
            .allowEmptyShould(false);
}
