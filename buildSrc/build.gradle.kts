plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.15")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.7.0")
}
