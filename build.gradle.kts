buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Security fixes for transitive build-tool libraries; see docs/SECURITY_AUDIT.md.
        classpath("org.apache.commons:commons-lang3:3.18.0")
        classpath("org.bitbucket.b_c:jose4j:0.9.6")
        classpath("org.bouncycastle:bcpkix-jdk18on:1.84")
        classpath("org.bouncycastle:bcprov-jdk18on:1.84")
        classpath("org.bouncycastle:bcutil-jdk18on:1.84")
        classpath("org.jdom:jdom2:2.0.6.1")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
