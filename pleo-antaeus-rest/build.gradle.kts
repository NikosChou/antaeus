plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-core"))
    implementation(project(":pleo-antaeus-models"))

    implementation("io.javalin:javalin:3.7.0")
    implementation("org.thymeleaf:thymeleaf:3.0.9.RELEASE")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.3")
}
