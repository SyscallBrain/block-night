plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation(project(":core"))
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
application { mainClass.set("pt.tiago.blocknight.leveltool.MainKt") }
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    maxHeapSize = "1g"
}
tasks.withType<Test>().configureEach { maxHeapSize = "768m" }

// Conteúdo para o APK: validação e replay completos antes de exportar (PLANO §8). Nunca escreve em content/.
val contentRoot = rootProject.layout.projectDirectory.dir("content")
val assetOutput = rootProject.layout.projectDirectory.dir("app/build/generated/blockNightAssets/content")

val validateCatalog = tasks.register<JavaExec>("validateCatalog") {
    group = "verification"
    description = "Valida o catálogo de conteúdo (estrutura, regras, duplicados e contagens do estágio)."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootProject.projectDir
    args("validate", "--root", "content", "--strict")
    inputs.dir(contentRoot)
    outputs.upToDateWhen { false }
}

val verifyCertificates = tasks.register<JavaExec>("verifyCertificates") {
    group = "verification"
    description = "Repete todos os certificados com o motor atual."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootProject.projectDir
    args("verify-certificates", "--root", "content")
    inputs.dir(contentRoot)
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("exportGameAssets") {
    group = "build"
    description = "Exporta o catálogo validado para os assets do APK, depois de validação e replay."
    dependsOn(validateCatalog, verifyCertificates)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootProject.projectDir
    args("export-assets", "--root", "content", "--output", assetOutput.asFile.absolutePath)
    inputs.dir(contentRoot)
    outputs.dir(assetOutput)
}
