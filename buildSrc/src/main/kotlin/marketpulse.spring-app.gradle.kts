plugins {
    id("marketpulse.java-conventions")
    id("org.springframework.boot")
}

// 이미지는 GitHub Actions 의 Dockerfile 로 생성
tasks.named("bootBuildImage") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "app.jar"
}
