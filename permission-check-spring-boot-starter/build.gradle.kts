plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.7"
    `java-library`
    `maven-publish`
}

group = "io.github.common"
version = "1.0.0-SNAPSHOT"
description = "Spring Boot starter for annotation-based permission checking"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.5")
    }
}

dependencies {
    // Spring Boot Starter dependencies
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")

    // Spring Security (optional for defaults, but required for DefaultPrincipalIdExtractor/DefaultPermissionRepository)
    compileOnly("org.springframework.security:spring-security-core")

    // Kotlin support
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Optional dependencies for configuration
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Permission Check Spring Boot Starter")
                description.set("A Spring Boot starter for annotation-based permission checking with AOP")
                url.set("https://github.com/common/permission-check-spring-boot-starter")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("common")
                        name.set("Common Libraries Team")
                    }
                }
            }
        }
    }
}