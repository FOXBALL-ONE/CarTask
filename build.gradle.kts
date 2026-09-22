plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.hibernate.orm") version "7.4.1.Final"
    kotlin("plugin.jpa") version "2.3.21"
    kotlin("plugin.lombok") version "2.3.21"
}

group = "top.foxball"
version = "0.0.1-SNAPSHOT"
description = "carTask"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Source: https://mvnrepository.com/artifact/com.alibaba/easyexcel
    implementation("com.alibaba:easyexcel:4.0.3")
    implementation("com.aliyun:dysmsapi20180501:1.0.12") {
        // tea-xml -> dom4j -> pull-parser:2 里带 META-INF/services/javax.xml.parsers.SAXParserFactory，
        // 声明 org.gjt.xpp.jaxp11.SAXParserFactoryImpl。JDK 9+ 的 SPI 查找会让它盖过 JDK 自带的
        // Xerces 工厂，而它不支持 SAX2 特性设置，导致 logback 解析 logback-spring.xml 抛
        // SAXNotRecognizedException、整个日志配置静默失效。此处排除以保住默认 JAXP 实现。
        exclude(group = "pull-parser", module = "pull-parser")
    }
    implementation("org.lionsoul:ip2region:2.7.0")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation(kotlin("test"))
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.1.0")
    testRuntimeOnly("com.h2database:h2:2.4.240")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

hibernate {
    enhancement {
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

