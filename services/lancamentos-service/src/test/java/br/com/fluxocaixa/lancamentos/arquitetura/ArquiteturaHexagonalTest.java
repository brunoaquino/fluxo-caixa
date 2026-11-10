package br.com.fluxocaixa.lancamentos.arquitetura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArquiteturaHexagonalTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.fluxocaixa.lancamentos");

    @Test
    void dominioNaoDependeDeSpring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .check(CLASSES);
    }

    @Test
    void dominioNaoDependeDeAdaptadoresNemDaAplicacao() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapters..", "..application..")
                .check(CLASSES);
    }

    @Test
    void aplicacaoNaoDependeDeAdaptadores() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapters..")
                .check(CLASSES);
    }
}
