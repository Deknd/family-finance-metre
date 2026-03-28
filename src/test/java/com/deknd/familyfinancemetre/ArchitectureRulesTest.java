package com.deknd.familyfinancemetre;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
	packages = "com.deknd.familyfinancemetre",
	importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureRulesTest {

	@ArchTest
	static final ArchRule coreMustNotDependOnFlowPackages = noClasses()
		.that().resideInAPackage("..core..")
		.should().dependOnClassesThat().resideInAnyPackage("..flow..");

	@ArchTest
	static final ArchRule intakeFlowMustNotDependOnOtherFlows = noClasses()
		.that().resideInAPackage("..flow.intake..")
		.should().dependOnClassesThat().resideInAnyPackage("..flow.dashboard..", "..flow.collection..");

	@ArchTest
	static final ArchRule dashboardFlowMustNotDependOnOtherFlows = noClasses()
		.that().resideInAPackage("..flow.dashboard..")
		.should().dependOnClassesThat().resideInAnyPackage("..flow.intake..", "..flow.collection..");

	@ArchTest
	static final ArchRule collectionFlowMustNotDependOnOtherFlows = noClasses()
		.that().resideInAPackage("..flow.collection..")
		.should().dependOnClassesThat().resideInAnyPackage("..flow.intake..", "..flow.dashboard..");

	@ArchTest
	static final ArchRule legacyTopLevelLayerPackagesMustStayEmpty = noClasses()
		.should().resideInAnyPackage(
			"com.deknd.familyfinancemetre.config..",
			"com.deknd.familyfinancemetre.controller..",
			"com.deknd.familyfinancemetre.dto..",
			"com.deknd.familyfinancemetre.entity..",
			"com.deknd.familyfinancemetre.exception..",
			"com.deknd.familyfinancemetre.repository..",
			"com.deknd.familyfinancemetre.security..",
			"com.deknd.familyfinancemetre.service..",
			"com.deknd.familyfinancemetre.validation.."
		);
}
