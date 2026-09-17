plugins {
    java
    checkstyle
    jacoco
    id("com.diffplug.spotless")
}

private val javaVersion = 25
private val palantirFormatVersion = "2.98.0"
private val checkstyleVersion = "14.1.0"
private val lombokVersion = "1.18.48"
private val archunitVersion = "1.5.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -parameters: 스프링 생성자 주입용 파라미터 이름 보존
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

dependencies {
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
}

spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(palantirFormatVersion)
        removeUnusedImports()
        importOrder("java", "javax", "jakarta", "", "pro.seol")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// 포맷은 spotless 담당. 여기는 포맷으로 못 잡는 규칙만
checkstyle {
    toolVersion = checkstyleVersion
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

// 테스트 메서드명이 한글이라 명명 규칙 제외
tasks.checkstyleTest {
    enabled = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
