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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_REFRESH_DEPENDENCIES;

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class SyncProjectWithExtraCommandLineOptionsHyperlink extends SyncIssueNotificationHyperlink {
  public static final Key<String[]> EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY = Key.create("extra.gradle.command.line.options");

  @NotNull private final String[] myExtraOptions;

  @NotNull
  public static SyncIssueNotificationHyperlink syncProjectRefreshingDependencies() {
    return new SyncProjectWithExtraCommandLineOptionsHyperlink("Re-download dependencies and sync project (requires network)",
                                                               "--refresh-dependencies");
  }

  public SyncProjectWithExtraCommandLineOptionsHyperlink(@NotNull String text, @NotNull String... extraOptions) {
    super("syncProject", text, AndroidStudioEvent.GradleSyncQuickFix.SYNC_PROJECT_WITH_EXTRA_COMMAND_LINE_OPTIONS_HYPERLINK);
    myExtraOptions = extraOptions;
  }

  @Override
  protected void execute(@NotNull Project project) {
    // This is the only way that we can pass extra command line options to the Gradle sync process.
    project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, myExtraOptions);
    HyperlinkUtil.requestProjectSync(project, TRIGGER_QF_REFRESH_DEPENDENCIES);
  }
}
