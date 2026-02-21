plugins {
    application
}

dependencies {
    implementation(project(":queuectl-domain"))
    implementation(project(":queuectl-application"))
    implementation(project(":queuectl-infra"))
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")
    runtimeOnly("org.postgresql:postgresql:42.7.5")
}

application {
    mainClass.set("com.queuectl.cli.QueueCtlApplication")
}

tasks.named<org.gradle.jvm.application.tasks.CreateStartScripts>("startScripts") {
    applicationName = "queuectl"
}
