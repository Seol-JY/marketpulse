plugins {
    `java-library`
    id("marketpulse.java-conventions")
}

dependencies {
    api(platform(libs.springBootBom))
    api(libs.jacksonDatabind)

    testImplementation(platform(libs.springBootBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
