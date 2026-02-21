plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "queuectl"

include(
    "queuectl-domain",
    "queuectl-application",
    "queuectl-infra",
    "queuectl-cli"
)
