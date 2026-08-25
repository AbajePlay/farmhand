pluginManagement {
	repositories {
		mavenLocal()
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.9.2"
	id("dev.kikugie.loom-back-compat") version "0.4.1"
}

rootProject.name = "farmhand"

stonecutter {
	create(rootProject) {
		fun match(version: String, vararg loaders: String) =
			loaders.forEach { version("$version-$it", version).buildscript = "build.$it.gradle.kts" }

		// Each line is a version group sharing one source tree; neighbouring patch versions
		// are added via publish.additionalVersions in stonecutter.properties.toml.
		//
		// Forge is excluded from the matrix: the legacyforge plugin only builds it up to 1.20.1
		// (Forge changed its distribution format afterwards and no longer ships universal-srg),
		// and NeoForge took over its audience on modern versions.
		match("26.2", "fabric", "neoforge")
		match("26.1.2", "fabric", "neoforge")
		match("1.21.11", "fabric", "neoforge")
		match("1.21.8", "fabric", "neoforge")
		match("1.21.4", "fabric", "neoforge")
		match("1.21.1", "fabric", "neoforge")
		match("1.20.6", "fabric", "neoforge")
		match("1.20.4", "fabric", "neoforge")
		// NeoForge does not exist for 1.20.1 - it starts at 1.20.2.
		match("1.20.1", "fabric")

		vcsVersion = "26.2-fabric"
	}
}
