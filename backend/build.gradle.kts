plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("nu.studer.jooq") version "9.0"
    java
}

group = "dev.soupbase"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:2.28.0")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.1")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jooq:jooq:3.19.15")
    implementation("org.flywaydb:flyway-core:10.21.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.21.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("software.amazon.awssdk:secretsmanager")
    runtimeOnly("org.postgresql:postgresql")
    jooqGenerator("org.postgresql:postgresql")
    jooqGenerator("org.jooq:jooq-meta-extensions:3.19.15")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jooq {
    version.set("3.19.15")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)
            jooqConfiguration.apply {
                generator.apply {
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        properties.add(org.jooq.meta.jaxb.Property().apply {
                            key = "scripts"
                            value = "src/main/resources/db/migration/*.sql"
                        })
                        properties.add(org.jooq.meta.jaxb.Property().apply {
                            key = "sort"
                            value = "flyway"
                        })
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isFluentSetters = true
                    }
                    target.apply {
                        packageName = "dev.soupbase.db.generated"
                        directory = "src/main/generated"
                    }
                }
            }
        }
    }
}
