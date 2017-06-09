/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.BuildSystemService;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GradleBuildSystemService}.
 */
public class GradleBuildSystemServiceTest extends IdeaTestCase {
  private GradleSyncInvoker myOriginalGradleSyncInvoker;
  private GradleSyncInvoker mySyncInvoker;

  private BuildSystemService myService;
  private GradleProjectInfo myGradleProjectInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOriginalGradleSyncInvoker = GradleSyncInvoker.getInstance();

    mySyncInvoker = IdeComponents.replaceServiceWithMock(GradleSyncInvoker.class);
    IdeComponents.replaceServiceWithMock(myProject, GradleProjectBuilder.class);
    IdeComponents.replaceServiceWithMock(myProject, GradleDependencyManager.class);

    myGradleProjectInfo = IdeComponents.replaceServiceWithMock(myProject, GradleProjectInfo.class);
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    myService = BuildSystemService.getInstance(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalGradleSyncInvoker != null) {
        IdeComponents.replaceService(GradleSyncInvoker.class, myOriginalGradleSyncInvoker);
      }
    } finally {
      super.tearDown();
    }
  }

  public void testIsGradleBuildSystemService() {
    assertThat(myService).isInstanceOf(GradleBuildSystemService.class);
  }

  public void testIsNotGradleBuildSystemService() {
    when(GradleProjectInfo.getInstance(myProject).isBuildWithGradle()).thenReturn(false);
    assertThat(BuildSystemService.getInstance(myProject)).isNotInstanceOf(GradleBuildSystemService.class);
  }

  public void testSyncProject() {
    myService.syncProject(myProject);
    verify(GradleSyncInvoker.getInstance()).requestProjectSyncAndSourceGeneration(myProject, null, TRIGGER_PROJECT_MODIFIED);
  }

  public void testSyncProjectWithUninitializedProject() {
    Project project = getProject();
    StartupManagerEx startupManager = new StartupManagerImpl(project) {
      @Override
      public boolean startupActivityPassed() {
        return false; // this will make Project.isInitialized return false;
      }

      @Override
      public void runWhenProjectIsInitialized(@NotNull Runnable action) {
        action.run();
      }
    };
    IdeComponents.replaceService(project, StartupManager.class, startupManager);
    // http://b/62543184
    when(myGradleProjectInfo.isNewlyCreatedProject()).thenReturn(true);

    myService.syncProject(myProject);
    verify(mySyncInvoker, never()).requestProjectSyncAndSourceGeneration(same(project), any(), any());
  }

  public void testBuildProject() {
    myService.buildProject(myProject);
    verify(GradleProjectBuilder.getInstance(myProject)).compileJava();
  }

  public void testAddDependency() {
    String artifact = "com.android.foo:bar";
    myService.addDependency(getModule(), artifact);
    Iterable<GradleCoordinate> dependencies = Collections.singletonList(GradleCoordinate.parseCoordinateString(artifact + ":+"));
    verify(GradleDependencyManager.getInstance(myProject)).ensureLibraryIsIncluded(eq(getModule()), eq(dependencies), any());
  }
}
