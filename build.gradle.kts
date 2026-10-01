plugins {
    id("com.android.application") version "9.4.1" apply false
    // Not applied: AGP 9 compiles Kotlin itself. Declared so the Kotlin it
    // compiles with is this version rather than whatever AGP happens to bundle.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
