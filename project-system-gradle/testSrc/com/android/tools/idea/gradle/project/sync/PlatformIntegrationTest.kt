/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.prepareTestProject
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.nameToPath
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.task.ProjectTaskManager
import org.jetbrains.annotations.SystemIndependent
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PlatformIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testModelBuildServiceInCompositeBuilds() {
    val compositeBuildRoot = prepareGradleProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD, "project")
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(projectRule.fixture.testRootDisposable)
    openPreparedProject("project") { project ->
      for (module in ModuleManager.getInstance(project).modules) {
        if (ExternalSystemApiUtil.getExternalModuleType(module) == "sourceSet") continue

        val gradleTestModel: TestGradleModel? = CapturePlatformModelsProjectResolverExtension.getTestGradleModel(module)
        expect.that(gradleTestModel).named("TestGradleModel($module)").isNotNull()

        val gradleParameterizedTestModel: TestParameterizedGradleModel? =
          CapturePlatformModelsProjectResolverExtension.getTestParameterizedGradleModel(module)
        // TODO(b/202448739): Remove `if` when support for parameterized models in included builds is fixed in the IntelliJ platform.
        if (module.getGradleProjectPath()?.buildRoot == toSystemIndependentName(compositeBuildRoot.absolutePath)) {
          expect.that(gradleParameterizedTestModel).named("TestParameterizedGradleModel($module)").isNotNull()
          if (gradleParameterizedTestModel != null) {
            expect.that(gradleParameterizedTestModel.message).named("TestParameterizedGradleModel($module).message")
              .isEqualTo("Parameter: EHLO BuildDir: ${ExternalSystemApiUtil.getExternalProjectPath(module)}/build")
          }
        }
      }
    }
  }

  @Test
  fun `importing an already built project does not add all files to the VFS - existing idea project`() {
    val root = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    openPreparedProject("project") { project ->
      expect.that(root.resolve("app/build").exists())
      expect.that(VfsUtil.findFileByIoFile(root.resolve("app/build"), false)).isNull()
      ProjectTaskManager.getInstance(project).rebuildAllModules().blockingGet(1, TimeUnit.MINUTES)
      expect.that(root.resolve("app/build/intermediates/dex/debug").exists())
      expect.that(VfsUtil.findFileByIoFile(root.resolve("app/build"), false)).isNull()
      expect.that(VfsUtil.findFileByIoFile(root.resolve("app/build/intermediates/dex/debug"), false)).isNull()
    }

    val copy = root.parentFile.resolve("copy")
    FileUtil.copyDir(root, copy)
    openPreparedProject("copy") { project ->
      expect.that(copy.resolve("app/build/intermediates/dex/debug").exists())
      // TODO(b/200820556): Most file sin `build` dir should not be added to the VFS.
      // expect.that(VfsUtil.findFileByIoFile(copy.resolve("app/build/intermediates/dex/debug"), false)).isNull()
    }
  }

  @Test
  fun `importing an already built project does not add all files to the VFS - new idea project`() {
    val root = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    openPreparedProject("project") { project ->
      expect.that(root.resolve("app/build").exists())
      expect.that(VfsUtil.findFileByIoFile(root.resolve("app/build"), false)).isNull()
      ProjectTaskManager.getInstance(project).rebuildAllModules().blockingGet(1, TimeUnit.MINUTES)
      expect.that(root.resolve("app/build/intermediates/dex/debug").exists())
      expect.that(VfsUtil.findFileByIoFile(root.resolve("app/build"), false)).isNull()
      expect.that(VfsUtil.findFileByIoFile(root.resolve("app/build/intermediates/dex/debug"), false)).isNull()
    }

    val copy = root.parentFile.resolve("copy")
    FileUtil.copyDir(root, copy)
    FileUtil.delete(copy.resolve(".idea"))
    openPreparedProject("copy") { project ->
      expect.that(copy.resolve("app/build/intermediates/dex/debug").exists())
      // TODO(b/200820556): Most file sin `build` dir should not be added to the VFS.
      // expect.that(VfsUtil.findFileByIoFile(copy.resolve("app/build/intermediates/dex/debug"), false)).isNull()
    }
  }

  @Test
  fun testBuildOutputFoldersAreRefreshed() {
    val root = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") {project ->
      val expectedOutputDir = root.resolve("app/build/intermediates/javac/debug")
      assertThat(expectedOutputDir.exists()).isFalse()  // Verify test assumptions.
      ProjectTaskManager.getInstance(project).buildAllModules().blockingGet(1, TimeUnit.MINUTES)
      assertThat(expectedOutputDir.exists()).isTrue()  // Verify test assumptions.

      // TODO(b/241686649): Remove the following assertion, which is wrong and simply illustrates the problem.
      assertThat(VfsUtil.findFileByIoFile(expectedOutputDir, false)).isNull()
      // TODO(b/241686649): assertThat(VfsUtil.findFileByIoFile(expectedOutputDir, false)).isNotNull()
      // TODO(b/241686649): assertThat(VfsUtil.findFileByIoFile(expectedOutputDir, false)?.isValid).isTrue()
    }
  }

  @Test
  fun testCorrectSyncEventsPublished_successfulSync() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    val log = openProjectWithEventLogging("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    expect.that(log).isEqualTo("""
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      """.trimMargin())
  }

  @Test
  fun testCorrectSyncEventsPublished_reopen() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    openPreparedProject("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }
    val log = openProjectWithEventLogging("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
    }

    expect.that(log).isEqualTo(
      """
      |skipped
      |ended: SKIPPED
      """.trimMargin()
    )
  }

  @Test
  fun testCorrectSyncEventsPublished_badConfig() {
    val path = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    path.resolve("settings.gradle").writeText("***BAD FILE***")

    val log = openProjectWithEventLogging("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    expect.that(log).startsWith(
      """
      |started(.)
      |failed(.):
      """.trimMargin()
    )
    expect.that(log).contains("***BAD FILE***")
    expect.that(log).endsWith(
      """
      |ended: FAILURE
      """.trimMargin()
    )
  }

  class FailingService: AbstractModuleDataService<ModuleData>() {
    override fun getTargetDataKey(): Key<ModuleData> = ProjectKeys.MODULE
    override fun importData(
      toImport: MutableCollection<out DataNode<ModuleData>>,
      projectData: ProjectData?,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ): Nothing {
      error("Failed!")
    }
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_dataImporterCrashes() {
    (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint(ProjectDataService.EP_NAME)
      .registerExtension(FailingService(), projectRule.testRootDisposable)
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    val log = openProjectWithEventLogging("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    assertThat(log).isEqualTo(
      """
      |started(.)
      |failed(.): Failed to import project structure
      |ended: FAILURE
      """.trimMargin()
    )
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_dataImporterCrashesAfterSuccessfulOpen() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    val log = openProjectWithEventLogging("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)

      (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint(ProjectDataService.EP_NAME)
        .registerExtension(FailingService(), projectRule.testRootDisposable)
      project.requestSyncAndWait()

      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isTrue()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    assertThat(log).isEqualTo(
      """
     |started(.)
     |succeeded(.)
     |ended: SUCCESS
     |started(.)
     |failed(.): Failed to import project structure
     |ended: FAILURE
      """.trimMargin()
    )
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_gradleCancelled() {
    val path = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    path.resolve("settings.gradle").writeText("Thread.sleep(200); println('waiting!'); Thread.sleep(30_000)")

    val log = openProjectWithEventLogging("project", outputHandler = { output ->
      if (output.contains("waiting!")) {
        CoreProgressManager.getCurrentIndicators()
          .single { it.text.contains("Gradle:") }
          .cancel()
      }
    }) { project ->
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isTrue()
      // Cancelling initial sync results in FAILURE to avoid blocking the UI waiting for UNKNOWN state to be gone.
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    expect.that(log).startsWith(
      """
      |started(.)
      |cancelled(.)
      """.trimMargin()
    )
    expect.that(log).endsWith(
      """
      |ended: FAILURE
      """.trimMargin()
    )
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_gradleCancelledAfterSuccessfulOpen() {
    val path = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    val log = openProjectWithEventLogging("project", outputHandler = { output ->
      if (output.contains("waiting!")) {
        CoreProgressManager.getCurrentIndicators()
          .single { it.text.contains("Gradle:") }
          .cancel()
      }
    }) { project ->

      path.resolve("settings.gradle").writeText("Thread.sleep(200); println('waiting!'); Thread.sleep(30_000)")
      project.requestSyncAndWait()

      // Cancelling sync does not change the current state.
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isFalse()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    expect.that(log).startsWith(
      """
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      |started(.)
      |cancelled(.)
      |ended: SUCCESS
      """.trimMargin()
    )
  }

  /**
   * A data service which simulates cancellation of import at data services phase.
   *
   * Note, that it needs to run first to avoid `ProcessCancelledException` related memory leaks, which are later caught by the testing
   * infrastructure. For example, see https://youtrack.jetbrains.com/issue/IDEA-298437.
   */
  @Order(BUILTIN_MODULE_DATA_SERVICE_ORDER - 1)
  class CancellingService : AbstractModuleDataService<ModuleData>() {
    override fun getTargetDataKey(): Key<ModuleData> = ProjectKeys.MODULE
    override fun importData(
      toImport: MutableCollection<out DataNode<ModuleData>>,
      projectData: ProjectData?,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ) {
      ProgressManager.getInstance().progressIndicator.cancel()
      ProgressManager.checkCanceled()
    }

  }

  @Suppress("UnstableApiUsage")
  @Test
  fun testCorrectSyncEventsPublished_dataImporterCancelled() {
    (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint(ProjectDataService.EP_NAME)
      .registerExtension(CancellingService(), projectRule.testRootDisposable)
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    val log = openProjectWithEventLogging("project") { project ->
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isTrue()
      // Cancelling initial sync results in FAILURE to avoid blocking the UI waiting for UNKNOWN state to be gone.
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    assertThat(log).isEqualTo(
      """
      |started(.)
      |cancelled(.)
      |ended: FAILURE
      """.trimMargin()
    )
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun testCorrectSyncEventsPublished_dataImporterCancelledAfterSuccessfulOpen() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    val log = openProjectWithEventLogging("project") { project ->
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isFalse()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)

      (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint(ProjectDataService.EP_NAME)
        .registerExtension(CancellingService(), projectRule.testRootDisposable)

      project.requestSyncAndWait()

      // Cancelling sync does not change the current state.
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isFalse()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    assertThat(log).isEqualTo(
      """
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      |started(.)
      |cancelled(.)
      |ended: SUCCESS
      """.trimMargin()
    )
  }

  @Test
  fun testSimpleApplicationNotAtRoot() {
    val preparedProject = prepareTestProject(TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT)
    val log = openProjectWithEventLogging(preparedProject) {project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    expect.that(log).isEqualTo("""
      |started(gradle_project)
      |succeeded(gradle_project)
      |ended: SUCCESS
      """.trimMargin())
  }

  private fun openProjectWithEventLogging(
    name: String,
    outputHandler: (Project.(String) -> Unit)? = null,
    body: (Project) -> Unit = {}
  ): String {
    return openProjectWithEventLogging(
      object: PreparedTestProject{
        override val root: File = nameToPath(name)

        override fun <T> open(options: OpenPreparedProjectOptions, body: (Project) -> T): T {
          return openPreparedProject(name, options, body)
        }

      },
      outputHandler,
      body
    )
  }

  private fun openProjectWithEventLogging(
    preparedProject: PreparedTestProject,
    outputHandler: (Project.(String) -> Unit)? = null,
    body: (Project) -> Unit = {}
  ): String {
    val root = preparedProject.root

    fun String.toLocalPath(): String = File(this).relativeToOrSelf(root).path.takeUnless { it.isEmpty() } ?: "."

    val completedChanged = CountDownLatch(1)
    val log = buildString {
      preparedProject.open(
        options = OpenPreparedProjectOptions(
          verifyOpened = { /* do nothing */ },
          outputHandler = outputHandler,
          subscribe = {
            it.subscribe(GRADLE_SYNC_TOPIC, object : GradleSyncListenerWithRoot {
              override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
                appendLine("started(${rootProjectPath.toLocalPath()})")
              }

              override fun syncFailed(project: Project, errorMessage: String, rootProjectPath: @SystemIndependent String) {
                appendLine("failed(${rootProjectPath.toLocalPath()}): $errorMessage")
              }

              override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
                appendLine("succeeded(${rootProjectPath.toLocalPath()})")
              }

              override fun syncSkipped(project: Project) {
                appendLine("skipped")
              }

              override fun syncCancelled(project: Project, rootProjectPath: @SystemIndependent String) {
                appendLine("cancelled(${rootProjectPath.toLocalPath()})")
              }
            })
            it.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object: ProjectSystemSyncManager.SyncResultListener {
              override fun syncEnded(result: SyncResult) {
                appendLine("ended: $result")
                completedChanged.countDown()
              }
            })
          })
      ) { project ->
        // When sync is cancelled, and it is detected by handling `FinishBuildEvent` with `SuccessResult` the `syncCancelled` event might be
        // delivered after we reach this point.
        completedChanged.awaitSecondsOrThrow(10)
        expect.that(GradleSyncState.getInstance(project).isSyncInProgress).isFalse()
        body(project)
      }
    }.trim()
    return log
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> = emptyList()
}

fun CountDownLatch.awaitSecondsOrThrow(seconds: Long): Boolean {
  return await(seconds, TimeUnit.SECONDS)
    .also { if (!it) error("Timeout waiting for $this") }
}
