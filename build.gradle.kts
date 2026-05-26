plugins {
    id("org.springframework.boot") version "3.2.4" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    kotlin("plugin.jpa") version "1.9.24" apply false
    // Aggregates JaCoCo XML reports of every subproject and uploads them to SonarQube.
    // Triggered from Jenkins via `./gradlew sonar` inside `withSonarQubeEnv('sonarqube')`.
    id("org.sonarqube") version "5.0.0.4638"
}

sonar {
    properties {
        property("sonar.organization", "juancamuba")
        property("sonar.projectKey", "JuanCamiloMunozB_circle-guard-public")
        property("sonar.projectName", "CircleGuard")
        property("sonar.sourceEncoding", "UTF-8")
        // Aggregated coverage report paths (one per service module).
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            subprojects.joinToString(",") { sp ->
                "${sp.projectDir}/build/reports/jacoco/test/jacocoTestReport.xml"
            }
        )
        // Same exclusions applied to JaCoCo: keep DTOs, model, config, Application
        // out of the coverage denominator so the headline number reflects real
        // business code.
        property(
            "sonar.coverage.exclusions",
            "**/*Application.java,**/dto/**,**/model/**,**/config/**,**/event/**,**/exception/**,**/SecurityConfig.java"
        )
    }
}

allprojects {
    group = "com.circleguard"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // JaCoCo 0.8.11+ supports JDK 21 bytecode. Earlier versions crash on JDK 21 classes.
    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.11"
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "testAnnotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.named<org.gradle.api.tasks.testing.Test>("test") {
        useJUnitPlatform {
            excludeTags("integration")
        }
    }

    val sourceSets = extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()
    tasks.register<org.gradle.api.tasks.testing.Test>("integrationTest") {
        description = "Runs tests tagged as integration."
        group = org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        shouldRunAfter(tasks.named("test"))
        useJUnitPlatform {
            includeTags("integration")
        }
        // Explicitly propagate Docker env vars to the forked test JVM.
        // Testcontainers (identity-service, dashboard-service) needs the Docker daemon.
        // Gradle daemons are long-lived and may predate the Jenkins environment block,
        // so we hardcode values here to guarantee the test fork always has them.
        //
        // DOCKER_HOST  — points Testcontainers' EnvironmentAndSystemPropertyClientProviderStrategy
        //                at the WSL2 socket mounted into the Jenkins container.
        // DOCKER_API_VERSION — docker-java 3.3.3 (Testcontainers 1.19.x) defaults to
        //                API 1.41, but Docker Desktop 4.61 / Engine 29.x enforces a
        //                minimum of 1.44 and rejects anything below with:
        //                "client version 1.41 is too old. Minimum supported API version
        //                is 1.44". Pinning to 1.44 satisfies the daemon minimum while
        //                remaining fully compatible with the operations Testcontainers
        //                uses (create/start/stop/remove container, pull image).
        // TESTCONTAINERS_RYUK_DISABLED — Ryuk needs to bind-mount the socket itself,
        //                which requires extra privileges; disabling avoids the error.
        environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
        environment("DOCKER_API_VERSION", System.getenv("DOCKER_API_VERSION") ?: "1.44")
        environment("TESTCONTAINERS_RYUK_DISABLED", System.getenv("TESTCONTAINERS_RYUK_DISABLED") ?: "true")
    }

    // --- JaCoCo coverage report ---
    // The report aggregates execution data from BOTH the `test` and `integrationTest`
    // tasks (when executed) so the headline number reflects unit + integration coverage.
    //
    // Exclusions are deliberately conservative: only main()-only Application
    // classes and pure data carriers (DTOs, JPA/Neo4j entities, Spring config,
    // domain events, custom exceptions) are stripped. Controllers, services,
    // repositories interfaces with default methods, listeners and clients
    // remain in the denominator so the metric reflects real business code.
    val coverageExcludes = listOf(
        "**/*Application.class",
        "**/dto/**",
        "**/model/**",
        "**/config/**",
        "**/event/**",
        "**/exception/**",
        // Spring Security configuration classes are bean wiring — they declare
        // SecurityFilterChain, AuthenticationManager, PasswordEncoder, etc. We
        // exclude them for the same reason we exclude **/config/** (boilerplate
        // tested in integration). Filters and providers with real logic stay in.
        "**/SecurityConfig.class"
    )

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        // Pick up exec data from integrationTest if it ran in this invocation.
        executionData(
            fileTree(layout.buildDirectory).include("/jacoco/test.exec", "/jacoco/integrationTest.exec")
        )
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude(coverageExcludes) }
            })
        )
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named<org.gradle.api.tasks.testing.Test>("test") {
        finalizedBy(tasks.named("jacocoTestReport"))
    }
    tasks.named<org.gradle.api.tasks.testing.Test>("integrationTest") {
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    // Quality gate: fail the build if a service drops below the agreed line floor.
    // The gate is wired but NOT bound to `check` here so the team can run a
    // baseline `gradlew test jacocoTestReport` for diagnosis without failing
    // immediately. Once every service is at >=80% in CI we will bind this to
    // `check` to enforce it on every build.
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("jacocoTestReport"))
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude(coverageExcludes) }
            })
        )
        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }
}
