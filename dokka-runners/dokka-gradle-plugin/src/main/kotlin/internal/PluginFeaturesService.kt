/*
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.dokka.gradle.internal

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.extra

/**
 * Internal utility service for managing Dokka Plugin features and warnings.
 *
 * Using a [BuildService] is most useful for only logging a single warning for the whole project,
 * regardless of how many subprojects have applied DGP.
 */
internal abstract class PluginFeaturesService : BuildService<PluginFeaturesService.Params> {

    interface Params : BuildServiceParameters {
        /** @see PluginFeaturesService.v2PluginEnabled */
        val v2PluginEnabled: Property<Boolean>

        /** @see [PluginFeaturesService.v2PluginNoWarn] */
        val v2PluginNoWarn: Property<Boolean>

        /** @see [PluginFeaturesService.v2PluginMigrationHelpersEnabled] */
        val v2PluginMigrationHelpersEnabled: Property<Boolean>

        /** @see [PluginFeaturesService.primaryService] */
        val primaryService: Property<Boolean>

        /** If `true`, enable K2 analysis. */
        val k2AnalysisEnabled: Property<Boolean>

        /** If `true`, suppress the K2 analysis message. */
        val k2AnalysisNoWarn: Property<Boolean>
    }

    /**
     * Designate this [BuildService] as 'primary', meaning it should log messages to users.
     * Non-primary services should not log messages.
     *
     * Why? Because Gradle is buggy. Sometimes registering a BuildService fails.
     * See https://github.com/gradle/gradle/issues/17559.
     * If service registration fails then re-register the service, but with a distinct name
     * (so it doesn't clash with the existing but inaccessible BuildService), and don't mark it as 'primary'.
     *
     * @see org.jetbrains.dokka.gradle.internal.registerIfAbsent
     */
    private val primaryService: Boolean get() = parameters.primaryService.getOrElse(false)

    /**
     * Whether DGP should use V2 [org.jetbrains.dokka.gradle.DokkaBasePlugin].
     *
     * Otherwise, fallback to V1 [org.jetbrains.dokka.gradle.DokkaClassicPlugin].
     */
    internal val v2PluginEnabled: Boolean by lazy {
        val v2PluginEnabled = parameters.v2PluginEnabled.getOrElse(false)

        if (v2PluginEnabled) {
            logV2PluginMessage()
        } else {
            logV1PluginMessage()
        }

        v2PluginEnabled
    }

    /** If `true`, suppress any messages regarding V2 mode. */
    private val v2PluginNoWarn: Boolean
        get() = parameters.v2PluginNoWarn.getOrElse(false)


    private fun logV1PluginMessage() {
        if (primaryService) {
            logger.warn("warning: Dokka Gradle Plugin V1 mode is deprecated")
            logger.lifecycle(
                """
                |Dokka Gradle Plugin V1 mode is deprecated, and will be removed in Dokka version 2.1.0
                |
                |Please migrate Dokka Gradle Plugin to V2. This will require updating your project.
                |To get started check out the Dokka Gradle Plugin Migration guide
                |    https://kotl.in/dokka-gradle-migration
                |
                |Once you have prepared your project, enable V2 by adding
                |    $V2_PLUGIN_ENABLED_FLAG=true
                |to your project's `gradle.properties`
                |
                |Please report any feedback or problems to Dokka GitHub Issues
                |    https://github.com/Kotlin/dokka/issues/
                """.trimMargin().prependIndent(" > ")
            )
        }
    }

    private fun logV2PluginMessage() {
        if (primaryService && !v2PluginNoWarn) {
            logger.warn("warning: Dokka Gradle Plugin V2 is enabled")
            logger.lifecycle(
                """
                |We would appreciate your feedback on Dokka Gradle Plugin V2!
                |
                |Please report any feedback or problems to Dokka GitHub Issues
                |    https://github.com/Kotlin/dokka/issues/
                |
                |If you need help or advice, check out the migration guide
                |    https://kotl.in/dokka-gradle-migration
                |
                |You can suppress this message by adding
                |    $V2_PLUGIN_NO_WARN_FLAG=true
                |to your project's `gradle.properties`
                """.trimMargin().prependIndent(" > ")
            )
        }
    }

    internal val enableK2Analysis: Boolean by lazy {
        // use lazy {} to ensure messages are only logged once

        val enableK2Analysis = parameters.k2AnalysisEnabled.getOrElse(false)

        if (enableK2Analysis) {
            logK2AnalysisMessage()
        }

        enableK2Analysis
    }

    private fun logK2AnalysisMessage() {
        if (primaryService && !parameters.k2AnalysisNoWarn.getOrElse(false)) {
            logger.warn("warning: Dokka K2 Analysis is enabled")
            logger.lifecycle(
                """
                |Dokka K2 Analysis is Experimental and is still under active development.
                |It can cause build failures or generate incorrect documentation. 
                |
                |We would appreciate your feedback!
                |Please report any feedback or problems to Dokka GitHub Issues
                |    https://github.com/Kotlin/dokka/issues/
                |
                |You can suppress this message by adding
                |    $K2_ANALYSIS_NO_WARN_FLAG=true
                |to your project's `gradle.properties`
                """.trimMargin().prependIndent(" > ")
            )
        }
    }

    /**
     * Enable some migration helpers to aid in migrating DGP from V1 to V2.
     *
     * @see addV2MigrationHelpers
     */
    internal val v2PluginMigrationHelpersEnabled: Boolean by lazy {
        parameters.v2PluginMigrationHelpersEnabled.getOrElse(true)
    }

    companion object {
        private val logger = Logging.getLogger(PluginFeaturesService::class.java)

        /** @see [PluginFeaturesService.v2PluginEnabled] */
        internal const val V2_PLUGIN_ENABLED_FLAG =
            "org.jetbrains.dokka.experimental.gradlePlugin.enableV2"

        /** @see [PluginFeaturesService.v2PluginNoWarn] */
        internal const val V2_PLUGIN_NO_WARN_FLAG =
            "$V2_PLUGIN_ENABLED_FLAG.nowarn"

        /** The same as [V2_PLUGIN_NO_WARN_FLAG], but it doesn't trigger spell-checks. */
        private const val V2_PLUGIN_NO_WARN_FLAG_PRETTY =
            "$V2_PLUGIN_ENABLED_FLAG.noWarn"

        private const val V2_PLUGIN_MIGRATION_HELPERS_FLAG =
            "org.jetbrains.dokka.experimental.gradlePlugin.enableV2MigrationHelpers"

        private const val K2_ANALYSIS_ENABLED_FLAG =
            "org.jetbrains.dokka.experimental.tryK2"

        private const val K2_ANALYSIS_NO_WARN_FLAG =
            "$K2_ANALYSIS_ENABLED_FLAG.nowarn"

        private const val K2_ANALYSIS_NO_WARN_FLAG_PRETTY =
            "$K2_ANALYSIS_ENABLED_FLAG.noWarn"

        /**
         * Register a new [PluginFeaturesService], or get an existing instance.
         */
        val Project.pluginFeaturesService: PluginFeaturesService
            get() {
                val setFlags = Action<Params> {
                    v2PluginEnabled.set(getFlag(V2_PLUGIN_ENABLED_FLAG))
                    v2PluginNoWarn.set(getFlag(V2_PLUGIN_NO_WARN_FLAG_PRETTY).orElse(getFlag(V2_PLUGIN_NO_WARN_FLAG)))
                    v2PluginMigrationHelpersEnabled.set(getFlag(V2_PLUGIN_MIGRATION_HELPERS_FLAG))
                    k2AnalysisEnabled.set(getFlag(K2_ANALYSIS_ENABLED_FLAG))
                    k2AnalysisNoWarn.set(
                        getFlag(K2_ANALYSIS_NO_WARN_FLAG_PRETTY)
                            .orElse(getFlag(K2_ANALYSIS_NO_WARN_FLAG))
                    )
                }

                return try {
                    gradle.sharedServices.registerIfAbsent(PluginFeaturesService::class) {
                        parameters(setFlags)
                        // This service was successfully registered, so it is considered 'primary'.
                        parameters.primaryService.set(true)
                    }.get()
                } catch (ex: ClassCastException) {
                    try {
                        // Recover from Gradle bug: re-register the service, but don't mark it as 'primary'.
                        gradle.sharedServices.registerIfAbsent(
                            PluginFeaturesService::class,
                            classLoaderScoped = true,
                        ) {
                            parameters(setFlags)
                            parameters.primaryService.set(false)
                        }.get()
                    } catch (ex: ClassCastException) {
                        throw GradleException(
                            "Failed to register BuildService. Please report this problem https://github.com/gradle/gradle/issues/17559",
                            ex
                        )
                    }
                }
            }

        private fun Project.getFlag(flag: String): Provider<Boolean> =
            providers
                .gradleProperty(flag)
                .forUseAtConfigurationTimeCompat()
                .orElse(
                    // Note: Enabling/disabling features via extra-properties is only intended for unit tests.
                    // (Because org.gradle.testfixtures.ProjectBuilder doesn't support mocking Gradle properties.
                    // But maybe soon! https://github.com/gradle/gradle/pull/30002)
                    project
                        .provider { project.extra.properties[flag]?.toString() }
                        .forUseAtConfigurationTimeCompat()
                )
                .map(String::toBoolean)
    }
}
