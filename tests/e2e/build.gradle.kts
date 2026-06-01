plugins {
    java
}

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-path:5.4.0")
    // junit-jupiter is brought transitively by spring-boot-starter-test and its
    // version is governed by the Spring Boot BOM (3.5.14 -> JUnit 5.12.x). Do NOT
    // pin it explicitly here: an out-of-line pin (e.g. 5.10.2) drags in a
    // junit-platform-engine that is older than the junit-platform-launcher Gradle
    // puts on the test classpath, which fails discovery with
    // "OutputDirectoryProvider not available ... unaligned versions".
    testImplementation("org.awaitility:awaitility:4.2.1")
    // JUnit Platform 1.11 introduced OutputDirectoryProvider, which the 1.12.x
    // engine (Boot 3.5.14 -> JUnit 5.12.x) requires at discovery time. The service
    // modules get junit-platform-launcher 1.12.x auto-injected onto their default
    // `test` suite classpath, but this module's custom test/e2eTest tasks do not,
    // so we add the BOM-aligned launcher explicitly to keep engine and launcher in
    // lockstep. Without it discovery dies with
    // "OutputDirectoryProvider not available ... unaligned versions".
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// E2E tests target a fully-deployed stack (CI against stage/AKS or local docker-compose).
// They are tagged @Tag("e2e") and excluded from the default `test` task so that
// `./gradlew test` works in a plain local checkout without a running stack.
// To execute them explicitly, run `./gradlew :tests:e2e:e2eTest`.
tasks.test {
    useJUnitPlatform {
        excludeTags("e2e")
    }
    systemProperty("base.url", System.getProperty("base.url", "http://localhost"))
    systemProperty("auth.port", System.getProperty("auth.port", "8180"))
    systemProperty("identity.port", System.getProperty("identity.port", "8083"))
    systemProperty("form.port", System.getProperty("form.port", "8086"))
    systemProperty("promotion.port", System.getProperty("promotion.port", "8088"))
    systemProperty("notification.port", System.getProperty("notification.port", "8082"))
    systemProperty("dashboard.port", System.getProperty("dashboard.port", "8084"))
    systemProperty("gateway.port", System.getProperty("gateway.port", "8080"))
    systemProperty("file.port", System.getProperty("file.port", "8087"))
}

tasks.register<Test>("e2eTest") {
    description = "Runs tests tagged @Tag(\"e2e\") against a deployed stack."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("e2e")
    }
    systemProperty("base.url", System.getProperty("base.url", "http://localhost"))
    systemProperty("auth.port", System.getProperty("auth.port", "8180"))
    systemProperty("identity.port", System.getProperty("identity.port", "8083"))
    systemProperty("form.port", System.getProperty("form.port", "8086"))
    systemProperty("promotion.port", System.getProperty("promotion.port", "8088"))
    systemProperty("notification.port", System.getProperty("notification.port", "8082"))
    systemProperty("dashboard.port", System.getProperty("dashboard.port", "8084"))
    systemProperty("gateway.port", System.getProperty("gateway.port", "8080"))
    systemProperty("file.port", System.getProperty("file.port", "8087"))
}
