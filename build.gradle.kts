plugins {
    id("java")
    application
}

group = "me.markerra"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "me.markerra.rtcbridge.Main"
}

fun registerClientTask(name: String, mainClassName: String) {
    tasks.register<JavaExec>(name) {
        group = "application"
        description = "Runs $name for the local PCM bridge test."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass = mainClassName
        standardInput = System.`in`
    }
}

registerClientTask("runTestSource", "me.markerra.rtcbridge.testclient.PcmTestSource")
registerClientTask("runTestConsumer", "me.markerra.rtcbridge.testclient.PcmTestConsumer")

tasks.test {
    useJUnitPlatform()
}
