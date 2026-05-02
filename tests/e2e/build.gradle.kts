plugins {
    java
}

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-path:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.awaitility:awaitility:4.2.1")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("base.url", System.getProperty("base.url", "http://localhost"))
    systemProperty("auth.port", System.getProperty("auth.port", "8180"))
    systemProperty("identity.port", System.getProperty("identity.port", "8083"))
    systemProperty("form.port", System.getProperty("form.port", "8086"))
    systemProperty("promotion.port", System.getProperty("promotion.port", "8088"))
    systemProperty("notification.port", System.getProperty("notification.port", "8082"))
    systemProperty("dashboard.port", System.getProperty("dashboard.port", "8084"))
}
