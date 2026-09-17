plugins {
    `kotlin-dsl`
}

// 빌드 도구 버전. 라이브러리 버전은 gradle/libs.versions.toml
dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.2")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.1")
}
