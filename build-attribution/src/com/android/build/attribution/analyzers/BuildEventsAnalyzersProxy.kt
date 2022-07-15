/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.idea.flags.StudioFlags
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

interface BuildEventsAnalysisResult {
  fun getAnnotationProcessorsData(): List<AnnotationProcessorData>
  fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData>
  fun getTotalBuildTimeMs(): Long
  fun getConfigurationPhaseTimeMs(): Long
  fun getCriticalPathTasks(): List<TaskData>
  fun getTasksDeterminingBuildDuration(): List<TaskData>
  fun getPluginsDeterminingBuildDuration(): List<PluginBuildData>

  /**
   * Total configuration data summed over all subprojects.
   */
  fun getTotalConfigurationData(): ProjectConfigurationData

  /**
   * List of subprojects individual configuration data.
   */
  fun getProjectsConfigurationData(): List<ProjectConfigurationData>
  fun getAlwaysRunTasks(): List<AlwaysRunTaskData>
  fun getNonCacheableTasks(): List<TaskData>
  fun getTasksSharingOutput(): List<TasksSharingOutputData>

  /**
   * returns a list of all applied plugins for each configured project.
   * May contain internal plugins
   */
  fun getAppliedPlugins(): Map<String, List<PluginData>>

  /**
   * Result of configuration cache compatibility analysis, describes the state and incompatible plugins if any.
   */
  fun getConfigurationCachingCompatibility(): ConfigurationCachingCompatibilityProjectResult

  /**
   * Result Jetifier usage analyzer, describes the state of jetifier flags and AndroidX incompatible libraries if any.
   */
  fun getJetifierUsageResult(): JetifierUsageAnalyzerResult

  /**
   * List of garbage collection data for this build.
   */
  fun getGarbageCollectionData(): List<GarbageCollectionData>
  /**
   * Total time spent in garbage collection for this build.
   */
  fun getTotalGarbageCollectionTimeMs(): Long
  fun getJavaVersion(): Int?
  fun isGCSettingSet(): Boolean?
  fun buildUsesConfigurationCache(): Boolean
  fun getDownloadsAnalyzerResult(): DownloadsAnalyzer.Result
}

/**
 * A way of interaction between the build events analyzers and the build attribution manager.
 * Used to fetch the final data from the analyzers after the build is complete.
 */
class BuildEventsAnalyzersProxy(
  taskContainer: TaskContainer,
  pluginContainer: PluginContainer
) {
  val alwaysRunTasksAnalyzer = AlwaysRunTasksAnalyzer(taskContainer, pluginContainer)
  val annotationProcessorsAnalyzer = AnnotationProcessorsAnalyzer(taskContainer)
  val criticalPathAnalyzer = CriticalPathAnalyzer(taskContainer, pluginContainer)
  val noncacheableTasksAnalyzer = NoncacheableTasksAnalyzer(taskContainer)
  val garbageCollectionAnalyzer = GarbageCollectionAnalyzer()
  val projectConfigurationAnalyzer = ProjectConfigurationAnalyzer(pluginContainer)
  val tasksConfigurationIssuesAnalyzer = TasksConfigurationIssuesAnalyzer(taskContainer)
  val configurationCachingCompatibilityAnalyzer = ConfigurationCachingCompatibilityAnalyzer()
  val jetifierUsageAnalyzer = JetifierUsageAnalyzer()
  val downloadsAnalyzer = StudioFlags.BUILD_ANALYZER_DOWNLOADS_ANALYSIS.get().ifTrue { DownloadsAnalyzer() }



  val buildAnalyzers: List<BaseAnalyzer<*>>
    get() = listOfNotNull(
      alwaysRunTasksAnalyzer,
      annotationProcessorsAnalyzer,
      criticalPathAnalyzer,
      noncacheableTasksAnalyzer,
      garbageCollectionAnalyzer,
      projectConfigurationAnalyzer,
      tasksConfigurationIssuesAnalyzer,
      configurationCachingCompatibilityAnalyzer,
      jetifierUsageAnalyzer,
      downloadsAnalyzer
    )

  fun getAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return annotationProcessorsAnalyzer.result.annotationProcessorsData
  }

  fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return annotationProcessorsAnalyzer.result.nonIncrementalAnnotationProcessorsData
  }

  /** Time that includes task graph computation and other configuration activities before the tasks execution starts. */
  fun getConfigurationPhaseTimeMs(): Long {
    return criticalPathAnalyzer.result.run {
      val firstTaskStartTime = tasksDeterminingBuildDuration.minByOrNull { it.executionStartTime } ?.executionStartTime
      // TODO (b/183590011): also change starting point based on first configuration event
      // If there are no tasks on critical path (no-op build?) let's use buildFinishedTimestamp.
      (firstTaskStartTime ?: buildFinishedTimestamp) - buildStartedTimestamp
    }
  }

  fun getTotalBuildTimeMs(): Long {
    return criticalPathAnalyzer.result.run { buildFinishedTimestamp - buildStartedTimestamp }
  }

  fun getBuildFinishedTimestamp(): Long {
    return criticalPathAnalyzer.result.buildFinishedTimestamp
  }

  fun getCriticalPathTasks(): List<TaskData> {
    return criticalPathAnalyzer.result.tasksDeterminingBuildDuration.filter(TaskData::isOnTheCriticalPath)
  }

  fun getTasksDeterminingBuildDuration(): List<TaskData> {
    return criticalPathAnalyzer.result.tasksDeterminingBuildDuration
  }

  fun getPluginsDeterminingBuildDuration(): List<PluginBuildData> {
    return criticalPathAnalyzer.result.pluginsDeterminingBuildDuration
  }

  fun getGarbageCollectionData(): List<GarbageCollectionData> {
    return garbageCollectionAnalyzer.result.garbageCollectionData
  }

  fun getTotalGarbageCollectionTimeMs(): Long {
    return garbageCollectionAnalyzer.result.totalGarbageCollectionTimeMs
  }

  fun getJavaVersion(): Int? {
    return garbageCollectionAnalyzer.result.javaVersion
  }

  fun isGCSettingSet(): Boolean? {
    return garbageCollectionAnalyzer.result.isSettingSet
  }

  fun getTotalConfigurationData(): ProjectConfigurationData = projectConfigurationAnalyzer.result.run {
    val totalConfigurationTime = projectsConfigurationData.sumByLong { it.totalConfigurationTimeMs }

    val totalPluginConfiguration = pluginsConfigurationDataMap.map { entry ->
      PluginConfigurationData(entry.key, entry.value)
    }

    val totalConfigurationSteps = projectsConfigurationData.flatMap { it.configurationSteps }.groupBy { it.type }.map { entry ->
      ProjectConfigurationData.ConfigurationStep(entry.key, entry.value.sumByLong { it.configurationTimeMs })
    }

    return ProjectConfigurationData("Total Configuration Data", totalConfigurationTime, totalPluginConfiguration, totalConfigurationSteps)
  }

  fun getProjectsConfigurationData(): List<ProjectConfigurationData> {
    return projectConfigurationAnalyzer.result.projectsConfigurationData
  }

  fun getAppliedPlugins(): Map<String, List<PluginData>> {
    return projectConfigurationAnalyzer.result.allAppliedPlugins.toImmutableMap()
  }

  fun getConfigurationCachingCompatibility(): ConfigurationCachingCompatibilityProjectResult {
    return configurationCachingCompatibilityAnalyzer.result
  }

  fun buildUsesConfigurationCache(): Boolean = configurationCachingCompatibilityAnalyzer.result.let {
    it == ConfigurationCachingTurnedOn || it == ConfigurationCacheCompatibilityTestFlow
  }

  fun getJetifierUsageResult(): JetifierUsageAnalyzerResult {
    return jetifierUsageAnalyzer.result
  }

  fun getAlwaysRunTasks(): List<AlwaysRunTaskData> {
    return alwaysRunTasksAnalyzer.result.alwaysRunTasks
  }

  fun getNonCacheableTasks(): List<TaskData> {
    return noncacheableTasksAnalyzer.result.noncacheableTasks
  }

  fun getTasksSharingOutput(): List<TasksSharingOutputData> {
    return tasksConfigurationIssuesAnalyzer.result.tasksSharingOutput
  }

  fun getDownloadsAnalyzerResult(): DownloadsAnalyzer.Result {
    return downloadsAnalyzer?.result ?: DownloadsAnalyzer.AnalyzerIsDisabled
  }
}
