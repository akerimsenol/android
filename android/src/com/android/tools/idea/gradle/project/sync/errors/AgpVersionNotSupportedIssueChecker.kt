/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.errors

import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.Version
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.upgrade.ForcedPluginPreviewVersionUpgradeDialog
import com.android.tools.idea.gradle.project.upgrade.performForcedPluginUpgrade
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * IssueChecker to handle projects with incompatible (too old or mismatched preview) AGP versions.
 */
class AgpVersionNotSupportedIssueChecker: GradleIssueChecker {
  private val AGP_VERSION_TOO_OLD_PATTERN = Pattern.compile(
    "The project is using an incompatible version \\(AGP (.+)\\) of the Android Gradle plugin\\. " +
    "Minimum supported version is AGP ${Pattern.quote(GRADLE_PLUGIN_MINIMUM_VERSION)}\\."
  )
  private val AGP_VERSION_INCOMPATIBLE_PREVIEW_PATTERN = Pattern.compile(
    "The project is using an incompatible preview version \\(AGP (.+)\\) of the Android Gradle plugin\\. " +
    "Current compatible (preview )?version is AGP ${Version.ANDROID_GRADLE_PLUGIN_VERSION}\\."
  )

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: ""
    if (message.isBlank()) return null

    val tooOldMatcher = AGP_VERSION_TOO_OLD_PATTERN.matcher(message)
    val incompatiblePreviewMatcher = AGP_VERSION_INCOMPATIBLE_PREVIEW_PATTERN.matcher(message)
    val (matcher, userMessage) = when {
      tooOldMatcher.find() -> tooOldMatcher to tooOldMatcher.group(0)
      incompatiblePreviewMatcher.find() -> incompatiblePreviewMatcher to incompatiblePreviewMatcher.group(0)
      else -> return null
    }
    val version = GradleVersion.tryParseAndroidGradlePluginVersion(matcher.group(1)) ?: return null

    logMetrics(issueData.projectPath)

    fetchIdeaProjectForGradleProject(issueData.projectPath)?.let { project ->
      updateAndRequestSync(project, version)
    }

    return BuildIssueComposer(userMessage).apply {
      addQuickFix(AgpUpgradeQuickFix(version))
      addQuickFix(
        "See Android Studio & AGP compatibility options.",
        OpenLinkQuickFix("https://android.devsite.corp.google.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility")
      )
    }.composeBuildIssue()
  }

  private fun logMetrics(issueDataProjectPath: String) {
    invokeLater {
      updateUsageTracker(issueDataProjectPath, AndroidStudioEvent.GradleSyncFailure.OLD_ANDROID_PLUGIN)
    }
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
  ): Boolean {
    return (failureCause.contains("The project is using an incompatible version") &&
            failureCause.contains("Minimum supported version is AGP $GRADLE_PLUGIN_MINIMUM_VERSION")) ||
           (failureCause.contains("The project is using an incompatible preview version") &&
            failureCause.contains("Current compatible"))
  }
}

/**
 * Hyperlink that triggers the showing of the [ForcedPluginPreviewVersionUpgradeDialog] letting the user
 * upgrade their Android Gradle plugin and Gradle versions.
 */
class AgpUpgradeQuickFix(val currentAgpVersion: GradleVersion) : DescribedBuildIssueQuickFix {
  override val id: String = "android.gradle.plugin.forced.update"
  override val description: String = "Upgrade to the latest version"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Unit>()
    updateAndRequestSync(project, currentAgpVersion, future)
    return future
  }
}

/**
 * Helper method to trigger the forced upgrade prompt and then request a sync if it was successful.
 */
private fun updateAndRequestSync(project: Project, currentAgpVersion: GradleVersion, future: CompletableFuture<Unit>? = null) {
  AndroidExecutors.getInstance().ioThreadExecutor.execute {
    val success = performForcedPluginUpgrade(project, currentAgpVersion)
    if (success) {
      val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)
      GradleSyncInvoker.getInstance().requestProjectSync(project, request)
    }
    future?.complete(Unit)
  }
}