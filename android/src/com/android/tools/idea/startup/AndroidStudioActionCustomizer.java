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
package com.android.tools.idea.startup;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import org.jetbrains.annotations.NotNull;

/**
 * Startup code to customize IDE actions via ActionManager.
 * This code *only* runs in Android Studio. It does not run when the Android plugin runs inside IntelliJ.
 */
public class AndroidStudioActionCustomizer implements ActionConfigurationCustomizer {

  @Override
  public void customize(@NotNull ActionManager actionManager) {
    setUpNewFilePopupActions(actionManager);
    hideRarelyUsedIntellijActions(actionManager);
    setupResourceManagerActions(actionManager);
  }

  private static void setupResourceManagerActions(ActionManager actionManager) {
    Actions.hideAction(actionManager, "Images.ShowThumbnails");
    // Move the ShowServicesAction to the end of the queue by re-registering it, since it will always consume the shortcut event.
    // TODO(144579193): Remove this workaround when it's no longer necessary.
    //  Eg: When ShowServicesAction can decide whether it's enabled or not.
    AnAction servicesAction = actionManager.getAction("ServiceView.ShowServices");
    Actions.replaceAction(actionManager, "ServiceView.ShowServices", servicesAction);
  }

  // Remove popup actions that we don't use
  private static void setUpNewFilePopupActions(ActionManager actionManager) {
    Actions.hideAction(actionManager, "NewHtmlFile");
    Actions.hideAction(actionManager, "NewPackageInfo");

    // Hide designer actions
    Actions.hideAction(actionManager, "NewForm");
    Actions.hideAction(actionManager, "NewDialog");
    Actions.hideAction(actionManager, "NewFormSnapshot");

    // Hide individual actions that aren't part of a group
    Actions.hideAction(actionManager, "Groovy.NewClass");
    Actions.hideAction(actionManager, "Groovy.NewScript");
  }

  private static void hideRarelyUsedIntellijActions(ActionManager actionManager) {
    // Hide the Save File as Template action due to its rare use in Studio.
    Actions.hideAction(actionManager, "SaveFileAsTemplate");
  }
}
