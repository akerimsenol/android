/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class CreateSettingsActivityTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: fb8a268b-b089-409b-b6d9-fd02d3dbf2f9
   * <pre>
   *   Verifies verify that all login activity template can be added to a project.
   *   Test Steps
   *   1. On the Welcome Screen, select "Start a new Android Studio Project"
   *   2. Configure a project name,company domain and location and click Next
   *   3. Click Next
   *   4. Select "Settings Activity" and click Next
   *   5. Configure the activity details and click Finish (Verify 1)
   *   Verification
   *   1. An activity template that presents alternative layouts on handsets
   *      and tablet-sized screens is added to the project
   * </pre>
   */
  @RunIn(TestGroup.FAT)
  @Test
  public void activityTemplate() {
    // Create a new project with Settings Activity.
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame().createNewProject();

    newProjectWizard.getConfigureAndroidProjectStep()
                    .setCppSupport(false)
                    .enterApplicationName("SettingsActApp")
                    .enterCompanyDomain("android.devtools")
                    .enterPackageName("dev.tools");
    newProjectWizard.clickNext();

    newProjectWizard.clickNext(); // Skip "Select minimum SDK Api" step.
    newProjectWizard.chooseActivity("Settings Activity");
    newProjectWizard.clickNext(); // Use default activity name.
    newProjectWizard.clickFinish();

    guiTest.testSystem().waitForProjectSyncToFinish(guiTest.ideFrame());

    // Verification.
    EditorFixture editorFixture = guiTest.ideFrame().getEditor();
    String content = editorFixture.getCurrentFileContents();

    String settingClassDef = "public class SettingsActivity extends AppCompatPreferenceActivity";
    String tabletSizedScreen = "private static boolean isXLargeTablet(Context context)";
    String layout = "context.getResources().getConfiguration().screenLayout";

    assertThat(content.contains(settingClassDef)).isTrue();
    assertThat(content.contains(tabletSizedScreen)).isTrue();
    assertThat(content.contains(layout)).isTrue();
  }
}
