plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "dev.budgettracker"
version = "0.1.0"

repositories { mavenCentral() }

val ktorVersion = "3.6.0"
dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
kotlin { jvmToolchain(21) }
application { mainClass.set("dev.budgettracker.ApplicationKt") }
tasks.test { useJUnitPlatform() }
