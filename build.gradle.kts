tasks.register("checkAll") {
    group = "verification"
    description = "모든 모듈의 포맷·정적분석·테스트 검사"
    dependsOn(subprojects.map { "${it.path}:check" })
}
