package pro.seol.marketpulse.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "pro.seol.marketpulse.common", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule 다른_모듈을_참조하지_않는다 = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("pro.seol.marketpulse.producer..", "pro.seol.marketpulse.consumer..")
            .because("common 은 스키마의 단일 출처라 어느 모듈에서도 쓸 수 있어야 한다");

    @ArchTest
    static final ArchRule 프레임워크에_의존하지_않는다 = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "org.apache.kafka..", "jakarta.websocket..", "java.net.http..")
            .because("타입 라이브러리라 어느 실행 환경에서도 쓸 수 있어야 한다");
}
