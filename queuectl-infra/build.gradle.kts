plugins {
    `java-library`
}

dependencies {
    api(project(":queuectl-application"))
    api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("org.hibernate.orm:hibernate-core:6.6.11.Final")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.flywaydb:flyway-core:11.3.1")
    implementation("org.flywaydb:flyway-database-postgresql:11.3.1")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("io.micrometer:micrometer-core:1.14.4")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.4")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
}
