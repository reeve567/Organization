import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.4.21"
	id("com.github.johnrengelman.shadow") version "6.1.0"
	java
}

group = "dev.reeve"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
	jcenter()
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.21") // reflection utils and stuffs
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2") // coroutines :D
	
	implementation("org.ini4j:ini4j:0.5.4") // ini file lib
	implementation("org.jclarion:image4j:0.7") // ico file lib

	implementation("khttp:khttp:1.0.0") // http request lib
	implementation("org.jsoup:jsoup:1.13.1") // http page lib
	implementation("com.google.code.gson:gson:2.8.6") // json lib

	implementation("org.apache.commons:commons-compress:1.20") // apache commons (.zip, .gz, .tar, etc)
	implementation("org.tukaani:xz:1.8") // 7z
	implementation("com.github.axet:java-unrar:1.7.0-8") //rar
	
	implementation("no.tornado:tornadofx:1.7.20")
}

tasks {
	jar {
		manifest {
			attributes["Main-Class"] = "dev.reeve.organization.MainApp"
		}
	}
}

tasks.withType<KotlinCompile>() {
	kotlinOptions.jvmTarget = "1.8"
}