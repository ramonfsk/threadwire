plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

application {
    mainClass.set("com.fsk.threadwire.tools.fakesse.MainKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.coroutines.core)
}
