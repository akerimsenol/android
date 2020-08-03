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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Interface to be used by components that can switch [SurfaceLayoutManager]s.
 */
interface LayoutManagerSwitcher {
  /**
   * Returns true if the current selected [SurfaceLayoutManager] is [layoutManager].
   */
  fun isLayoutManagerSelected(layoutManager: SurfaceLayoutManager): Boolean

  /**
   * Sets a new [SurfaceLayoutManager].
   */
  fun setLayoutManager(layoutManager: SurfaceLayoutManager)
}

/**
 * Wrapper class to define the options available for [SwitchSurfaceLayoutManagerAction].
 * @param displayName Name to be shown for this option.
 * @param layoutManager [SurfaceLayoutManager] to switch to when this option is selected.
 */
data class SurfaceLayoutManagerOption(val displayName: String, val layoutManager: SurfaceLayoutManager)

/**
 * [DropDownAction] that allows switching the layout manager in the surface.
 */
class SwitchSurfaceLayoutManagerAction(private val layoutManagerSwitcher: LayoutManagerSwitcher,
                                       layoutManagers: List<SurfaceLayoutManagerOption>) : DropDownAction(
  "Switch Layout",
  "Changes the layout of the preview elements.",
  AllIcons.Debugger.RestoreLayout) {

  inner class SetSurfaceLayoutManagerAction(private val option: SurfaceLayoutManagerOption) : ToggleAction(option.displayName) {
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      layoutManagerSwitcher.setLayoutManager(option.layoutManager)
    }

    override fun isSelected(e: AnActionEvent): Boolean = layoutManagerSwitcher.isLayoutManagerSelected(option.layoutManager)
  }

  override fun hideIfNoVisibleChildren(): Boolean = true

  init {
    // We will only add the actions and be visible if there are more than one option
    if (layoutManagers.size > 1) {
      layoutManagers.forEach { add(SetSurfaceLayoutManagerAction(it)) }
    }
  }
}