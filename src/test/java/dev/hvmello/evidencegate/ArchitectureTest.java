package dev.hvmello.evidencegate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.hvmello.evidencegate", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule llmLibraryStaysInTheAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.llm..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.langchain4j..");

    @ArchTest
    static final ArchRule coreStaysFrameworkFree = noClasses()
            .that().resideInAnyPackage("..domain..", "..gate..", "..port..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");
}
