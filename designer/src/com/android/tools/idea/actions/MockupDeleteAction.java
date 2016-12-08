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
package com.android.tools.idea.actions;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.RenderService.MOCKUP_EDITOR_ENABLED;

/**
 * Delete all the mockup related attributes of the selected component
 */
public class MockupDeleteAction extends AnAction {

  private final static String TITLE = "Delete Mockup";
  private final NlComponent myNlComponent;

  public MockupDeleteAction(@NotNull NlComponent leafComponent) {
    super(TITLE);
    myNlComponent = leafComponent;
    getTemplatePresentation().setEnabledAndVisible(MOCKUP_EDITOR_ENABLED);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
        WriteCommandAction.runWriteCommandAction(
      myNlComponent.getModel().getProject(), "Delete mockup attributes", null,
      () -> {
        myNlComponent.removeAttribute(TOOLS_URI, ATTR_MOCKUP);
        myNlComponent.removeAttribute(TOOLS_URI, ATTR_MOCKUP_CROP);
        myNlComponent.removeAttribute(TOOLS_URI, ATTR_MOCKUP_OPACITY);
      }, myNlComponent.getModel().getFile());
  }
}
