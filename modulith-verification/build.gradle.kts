plugins {
    id("reconcile.spring-conventions")
}

dependencies {
    testImplementation(project(":app"))
    testImplementation(project(":shared"))
    testImplementation(project(":tenant"))
    testImplementation(project(":iam"))
    testImplementation(project(":ingestion"))
    testImplementation(project(":ledger"))
    testImplementation(project(":reconciliation"))
    testImplementation(project(":reporting"))
    testImplementation(project(":notification"))

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:redis")
}
