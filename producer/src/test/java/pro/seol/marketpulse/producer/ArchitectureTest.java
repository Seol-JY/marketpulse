package pro.seol.marketpulse.producer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "pro.seol.marketpulse.producer", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "pro.seol.marketpulse.producer.";

    @ArchTest
    static final ArchRule 의존은_안쪽을_향한다 = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("presentation")
            .definedBy(BASE + "presentation..")
            .layer("application")
            .definedBy(BASE + "application..")
            .layer("domain")
            .definedBy(BASE + "domain..")
            .layer("infrastructure")
            .definedBy(BASE + "infrastructure..")
            // 어댑터 두 계층은 피참조 없음. 스프링이 런타임에 주입
            .whereLayer("presentation")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("infrastructure")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("application")
            .mayOnlyBeAccessedByLayers("presentation", "infrastructure")
            .whereLayer("domain")
            .mayOnlyBeAccessedByLayers("application", "infrastructure")
            .allowEmptyShould(true)
            .because("나가는 쪽 인터페이스를 application 이 소유해야 Kafka 없이 검증된다");

    @ArchTest
    static final ArchRule domain_은_프레임워크를_모른다 = noClasses()
            .that()
            .resideInAPackage(BASE + "domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "org.apache.kafka..", "java.net.http..")
            .allowEmptyShould(true)
            .because("순수 로직이라야 입력과 출력만으로 검증된다");

    @ArchTest
    static final ArchRule application_은_카프카를_모른다 = noClasses()
            .that()
            .resideInAPackage(BASE + "application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..")
            .allowEmptyShould(true)
            .because("가짜 발행기를 넘겨 큐 드롭 정책을 검증할 수 있어야 한다");
}
