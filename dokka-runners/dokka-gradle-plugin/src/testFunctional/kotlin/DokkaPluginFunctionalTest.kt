/*
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.dokka.gradle

import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.dokka.gradle.internal.DokkaConstants.DOKKA_VERSION
import org.jetbrains.dokka.gradle.utils.*

class DokkaPluginFunctionalTest : FunSpec({
    val testProject = gradleKtsProjectTest("DokkaPluginFunctionalTest") {

        buildGradleKts = """
            |plugins {
            |  id("org.jetbrains.dokka") version "$DOKKA_VERSION"
            |}
            |
            |val printDeclarableConfigurations by tasks.registering {
            |  val declarableConfNames = provider { configurations.matching { it.isCanBeDeclared }.names }
            |  inputs.property("declarableConfNames", declarableConfNames)
            |  doLast {
            |    println("declarableConfigurations:" + declarableConfNames.get())
            |  }
            |}
            |
            """.trimMargin()
    }

    test("expect Dokka Plugin creates Dokka tasks") {
        testProject.runner
            .addArguments("tasks", "--group=dokka", "--quiet")
            .build {
                withClue(output) {
                    val dokkaTasks = output
                        .substringAfter("Dokka tasks")
                        .lines()
                        .filter { it.contains(" - ") }
                        .associate { it.splitToPair(" - ") }

                    dokkaTasks.shouldContainExactly(
                        //@formatter:off
                        "dokkaGenerate"                to "Generates Dokka publications for all formats",
                        "dokkaGenerateModuleHtml"      to "Executes the Dokka Generator, generating a html module",
                        "dokkaGeneratePublicationHtml" to "Executes the Dokka Generator, generating the html publication",
                        //@formatter:on
                    )
                }
            }
    }

    test("expect Dokka Plugin creates Dokka declarable configurations") {
        testProject.runner
            .addArguments(
                ":printDeclarableConfigurations",
                "--quiet",
                "-Porg.jetbrains.dokka.experimental.gradlePlugin.enableV2MigrationHelpers=false",
            )
            .build {
                withClue(output) {
                    val declarableConfigurations = output
                        .substringAfter("declarableConfigurations:[")
                        .substringBefore("]")
                        .split(",")
                        .map { it.trim() }
                        .sorted()

                    declarableConfigurations.shouldContainExactly(
                        "dokka",
                        "dokkaHtmlGeneratorRuntime",
                        "dokkaHtmlPlugin",
                        "dokkaHtmlPublicationPlugin",
                        "dokkaHtmlPublicationPluginApiOnly.internal",
                        "dokkaPlugin",
                    )
                }
            }
    }

    test("expect Dokka Plugin creates Dokka outgoing variants") {
        testProject.runner
            .addArguments("outgoingVariants", "--quiet")
            .build {
                val variants = output.invariantNewlines().replace('\\', '/')

                val dokkaVariants = variants.lines()
                    .filter { it.startsWith("Variant ") && it.contains("dokka", ignoreCase = true) }
                    .mapNotNull { it.substringAfter("Variant ", "").ifBlank { null } }

                dokkaVariants.shouldContainExactlyInAnyOrder(
                    expectedFormats.flatMap {
                        listOf(
                            "dokka${it}ModuleOutputDirectoriesConsumable.internal",
                            "dokka${it}PublicationPluginApiOnlyConsumable.internal",
                        )
                    }
                )

                fun checkVariant(format: String) {
                    @Suppress("LocalVariableName")
                    val Format = format.uppercaseFirstChar()

                    variants shouldContain /* language=text */ """
                        |--------------------------------------------------
                        |Variant dokka${Format}ModuleOutputDirectoriesConsumable.internal
                        |--------------------------------------------------
                        |[Internal Dokka Configuration] Provides Dokka $format ModuleOutputDirectories files for consumption by other subprojects.
                        |
                        |Capabilities
                        |    - :test:unspecified (default capability)
                        |Attributes
                        |    - org.gradle.usage                     = org.jetbrains.dokka
                        |    - org.jetbrains.dokka.format           = $format
                        |    - org.jetbrains.dokka.module-component = ModuleOutputDirectories
                        |Artifacts
                        |    - build/dokka-module/$format (artifactType = dokka-module-directory)
                        """.trimMargin()
                }

                expectedFormats.forEach {
                    checkVariant(it.lowercase())
                }
            }
    }

    test("expect Dokka Plugin creates Dokka resolvable configurations") {
        testProject.runner
            .addArguments("resolvableConfigurations", "--quiet")
            .build {
                val configurationsDump = parseConfigurationsDump(output)
                    .mapValues { (_, v) -> v.removeBuggedGradleWarning() }

                configurationsDump
                    .filter { (k, v) -> "$k=$v".contains("dokka", ignoreCase = true) }
                    .asClue { dokkaConfigurations ->
                        expectedFormats.forEach { expectedFormat ->

                            val format = expectedFormat.lowercase()

                            @Suppress("LocalVariableName")
                            val Format = format.uppercaseFirstChar()

                            dokkaConfigurations.keys.shouldContainExactlyInAnyOrder(
                                listOf(
                                    "Configuration dokka${Format}GeneratorRuntimeResolver.internal",
                                    "Configuration dokka${Format}ModuleOutputDirectoriesResolver.internal",
                                    "Configuration dokka${Format}PluginIntransitiveResolver.internal",
                                    "Configuration dokka${Format}PublicationPluginResolver.internal",
                                )
                            )

                            mapOf(
                                "Configuration dokka${Format}GeneratorRuntimeResolver.internal" to /* language=text */ """
                                    |[Internal Dokka Configuration] Dokka Generator runtime classpath for $format - will be used in Dokka Worker. Should contain all transitive dependencies, plugins (and their transitive dependencies), so Dokka Worker can run.
                                    |
                                    |Attributes
                                    |    - org.gradle.category            = DGP~library
                                    |    - org.gradle.dependency.bundling = DGP~external
                                    |    - org.gradle.jvm.environment     = DGP~standard-jvm
                                    |    - org.gradle.libraryelements     = DGP~jar
                                    |    - org.gradle.usage               = DGP~java-runtime
                                    |    - org.jetbrains.dokka.classpath  = dokka-generator
                                    |    - org.jetbrains.dokka.format     = $format
                                    |Extended Configurations
                                    |    - dokka${Format}GeneratorRuntime
                                    """.trimMargin(),

                                "Configuration dokka${Format}ModuleOutputDirectoriesResolver.internal" to /* language=text */ """
                                    |[Internal Dokka Configuration] Resolves Dokka $format ModuleOutputDirectories files.
                                    |
                                    |Attributes
                                    |    - org.gradle.usage                     = org.jetbrains.dokka
                                    |    - org.jetbrains.dokka.format           = $format
                                    |    - org.jetbrains.dokka.module-component = ModuleOutputDirectories
                                    |Extended Configurations
                                    |    - dokka
                                    """.trimMargin(),

                                "Configuration dokka${Format}PluginIntransitiveResolver.internal" to /* language=text */ """
                                    |[Internal Dokka Configuration] Resolves Dokka Plugins classpath for $format. Fetch only the plugins (no transitive dependencies) for use in the Dokka JSON Configuration.
                                    |
                                    |Attributes
                                    |    - org.gradle.category            = DGP~library
                                    |    - org.gradle.dependency.bundling = DGP~external
                                    |    - org.gradle.jvm.environment     = DGP~standard-jvm
                                    |    - org.gradle.libraryelements     = DGP~jar
                                    |    - org.gradle.usage               = DGP~java-runtime
                                    |    - org.jetbrains.dokka.classpath  = dokka-plugins
                                    |    - org.jetbrains.dokka.format     = $format
                                    |Extended Configurations
                                    |    - dokka${Format}Plugin
                                    """.trimMargin(),

                                "Configuration dokka${Format}PublicationPluginResolver.internal" to /* language=text */ """
                                    |[Internal Dokka Configuration] Resolves Dokka Plugins classpath for a $format Publication (consisting of 1+ Dokka Module).
                                    |
                                    |Attributes
                                    |    - org.gradle.category            = DGP~library
                                    |    - org.gradle.dependency.bundling = DGP~external
                                    |    - org.gradle.jvm.environment     = DGP~standard-jvm
                                    |    - org.gradle.libraryelements     = DGP~jar
                                    |    - org.gradle.usage               = DGP~java-runtime
                                    |    - org.jetbrains.dokka.classpath  = dokka-publication-plugins
                                    |    - org.jetbrains.dokka.format     = $format
                                    |Extended Configurations
                                    |    - dokka${Format}PublicationPlugin
                                    """.trimMargin(),
                            ).forEach { (t, u) ->
                                dokkaConfigurations[t] shouldBe u
                            }
                        }
                    }
            }
    }
}) {
    companion object {
        /**
         * The output formats that Dokka supports.
         */
        private val expectedFormats = listOf(
            //"Gfm",
            "Html",
            //"Javadoc",
            //"Jekyll",
        )

        /**
         * Split the Gradle configuration dump to a map.
         *
         * Example input:
         *
         * ```text
         * --------------------------------------------------
         * Configuration foo
         * --------------------------------------------------
         * Foo description
         *
         * [...]
         *
         * --------------------------------------------------
         * Configuration bar
         * --------------------------------------------------
         * Bar description
         *
         * [...]
         * ```
         *
         * Result:
         *
         * ```
         * {
         *   "Configuration foo": "Foo description [...]",
         *   "Configuration bar": "Bar description [...]",
         * }
         * ```
         */
        private fun parseConfigurationsDump(text: String): Map<String, String> {
            val separator = "-".repeat(50) + "\n"
            return text
                .split(separator)
                .filter { it.isNotBlank() }
                .chunked(2)
                .associate {
                    fun getOrMissing(index: Int) = (it.getOrNull(index) ?: "missing").trim()
                    getOrMissing(0) to getOrMissing(1)
                }
        }

        /**
         * Gradle is bugged and inserts an annoying warning into the Configuration info dump.
         * ```text
         * Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes, but configuration ':dokkaHtmlModuleOutputDirectoriesResolver.internal' and [configuration ':dokkaHtmlModuleOutputDirectoriesConsumable.internal'] contain identical attribute sets. Consider adding an additional attribute to one of the configurations to disambiguate them. For more information, please refer to https://docs.gradle.org/8.9/userguide/upgrading_version_7.html#unique_attribute_sets in the Gradle documentation.
         * ```
         * As a workaround, remove the message.
         */
        // https://github.com/gradle/gradle/issues/28733
        private fun String.removeBuggedGradleWarning(): String {
            return lines()
                .filterNot { it.startsWith("Consumable configurations with identical capabilities within a project") }
                .joinToString("\n")
        }

    }
}