/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import icons.StudioIcons

internal class CustomViewPreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {

  private fun findPreviewEditors(): List<CustomViewPreviewManager> = FileEditorManager.getInstance(surface.project)?.let { fileEditorManager ->
    surface.models.flatMap { fileEditorManager.getAllEditors(it.virtualFile).asIterable() }
      .filterIsInstance<SeamlessTextEditorWithPreview<out FileEditor>>()
      .mapNotNull { it.preview.getCustomViewPreviewManager() }
      .distinct()
  } ?: listOf()

  override fun getNorthGroup(): ActionGroup {
    val customViewPreviewActions = DefaultActionGroup()
    val customViews = object : DropDownAction(null, "Custom View for Preview", StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW) {
      override fun update(e: AnActionEvent) {
        super.update(e)
        removeAll()

        // We need just a single previewEditor here (any) to retrieve (read) the states and currently selected state
        findPreviewEditors().firstOrNull()?.let { previewEditor ->
          previewEditor.views.forEach {
            val view = it
            add(object : AnAction(it) {
              override fun actionPerformed(e: AnActionEvent) {
                // Here we iterate over all editors as change in selection (write) should trigger updates in all of them
                findPreviewEditors().forEach { it.currentView = view }
              }
            })
          }
          e.presentation.setText(previewEditor.currentView, false)
        }
      }

      override fun displayTextInToolbar() = true
    }

    val wrapWidth = object : ToggleAction(null, "Set preview width to wrap content", StudioIcons.LayoutEditor.Toolbar.WRAP_WIDTH) {
      override fun isSelected(e: AnActionEvent) = findPreviewEditors().any { it.shrinkWidth }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        findPreviewEditors().forEach { it.shrinkWidth = state }
      }
    }

    val wrapHeight = object : ToggleAction(null, "Set preview height to wrap content", StudioIcons.LayoutEditor.Toolbar.WRAP_HEIGHT) {
      override fun isSelected(e: AnActionEvent) = findPreviewEditors().any { it.shrinkHeight }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        findPreviewEditors().forEach { it.shrinkHeight = state }
      }
    }

    customViewPreviewActions.add(customViews)
    customViewPreviewActions.add(wrapWidth)
    customViewPreviewActions.add(wrapHeight)

    return customViewPreviewActions
  }
}