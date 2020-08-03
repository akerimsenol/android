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
package com.android.tools.idea.gradle.project.sync.precheck;

import com.android.tools.idea.IdeInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.SUCCESS;

abstract class AndroidStudioSyncCheck extends SyncCheck {
  @Override
  @NotNull
  PreSyncCheckResult checkCanSyncAndTryToFix(@NotNull Project project) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      return doCheckCanSyncAndTryToFix(project);
    }
    return SUCCESS;
  }

  @VisibleForTesting
  @NotNull
  abstract PreSyncCheckResult doCheckCanSyncAndTryToFix(@NotNull Project project);
}