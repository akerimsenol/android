/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Map;

public interface GradleVersionCatalogView {

  /**
   * We do minimal settings file parsing for producing CatalogView
   */
  static GradleVersionCatalogView get(Project project) {
    return GradleModelProvider.getInstance().getVersionCatalogView(project);
  }

  /**
   * Returns result of parsing settings files with name to file catalog mapping.
   * Mappings with not existing files are filtered out.
   */
  Map<String, VirtualFile> getCatalogToFileMap();
}