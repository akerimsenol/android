/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.proto

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.google.common.annotations.VisibleForTesting

class TaskDataMessageConverter {
  companion object {
    fun transform(taskData: TaskData): BuildAnalysisResultsMessage.TaskData =
      BuildAnalysisResultsMessage.TaskData.newBuilder()
        .setTaskName(taskData.taskName)
        .setOriginPluginId(taskData.originPlugin.idName)
        .setProjectPath(taskData.projectPath)
        .setExecutionStartTime(taskData.executionStartTime)
        .setExecutionEndTime(taskData.executionEndTime)
        .setExecutionMode(transformExecutionMode(taskData.executionMode))
        .addAllExecutionReasons(taskData.executionReasons)
        .build()

    fun construct(taskData: List<BuildAnalysisResultsMessage.TaskData>, plugins: Map<String, PluginData>): List<TaskData> {
      val taskDataList = mutableListOf<TaskData>()
      for (task in taskData) {
        val taskName = task.taskName
        val projectPath = task.projectPath
        val pluginType = plugins[task.originPluginId]?.pluginType
        val originPlugin = pluginType?.let { PluginData(it, task.originPluginId) }
        val executionStartTime = task.executionStartTime
        val executionEndTime = task.executionEndTime
        val executionMode = constructExecutionMode(task.executionMode)
        val executionReasons = task.executionReasonsList
        originPlugin
          ?.let {
            val data = TaskData(taskName, projectPath, it, executionStartTime, executionEndTime, executionMode, executionReasons)
            taskDataList.add(data)
          }
      }
      return taskDataList
    }

    private fun transformExecutionMode(executionMode: TaskData.TaskExecutionMode) : BuildAnalysisResultsMessage.TaskData.TaskExecutionMode {
      return when (executionMode) {
        TaskData.TaskExecutionMode.FROM_CACHE -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FROM_CACHE
        TaskData.TaskExecutionMode.UP_TO_DATE -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UP_TO_DATE
        TaskData.TaskExecutionMode.INCREMENTAL -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.INCREMENTAL
        TaskData.TaskExecutionMode.FULL -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FULL
      }
    }

    @VisibleForTesting
    fun constructExecutionMode(executionMode: BuildAnalysisResultsMessage.TaskData.TaskExecutionMode) =
      when (executionMode) {
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FROM_CACHE -> TaskData.TaskExecutionMode.FROM_CACHE
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UP_TO_DATE -> TaskData.TaskExecutionMode.UP_TO_DATE
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.INCREMENTAL -> TaskData.TaskExecutionMode.INCREMENTAL
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FULL -> TaskData.TaskExecutionMode.FULL
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UNKNOWN -> throw IllegalStateException("Unrecognized task execution mode")
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UNRECOGNIZED -> throw IllegalStateException("Unrecognized task execution mode")
      }
  }
}