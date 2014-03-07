/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.android;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sets up a {@link Module} using the settings from a given {@link IdeaAndroidProject}.
 */
public interface AndroidModuleCustomizer extends ModuleCustomizer<IdeaAndroidProject> {
  /**
   * Customizes the given module (e.g. add facets, SDKs, etc.)
   *
   * @param module             module to customize.
   * @param project            project that owns the module to customize.
   * @param androidProject the imported Android-Gradle project.
   */
  @Override
  void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject androidProject);
}
