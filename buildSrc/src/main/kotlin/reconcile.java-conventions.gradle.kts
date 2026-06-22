plugins {
    java
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "1g"
    jvmArgs("-XX:+UseG1GC")
    systemProperty("java.security.egd", "file:/dev/./urandom")
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat("2.93.0")
        importOrder()
        removeUnusedImports()
    }
}
