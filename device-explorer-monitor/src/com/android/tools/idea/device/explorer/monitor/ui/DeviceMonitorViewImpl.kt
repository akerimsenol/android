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
package com.android.tools.idea.device.explorer.monitor.ui

import com.android.tools.idea.device.explorer.monitor.DeviceMonitorModelListener
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorViewListener
import com.android.tools.idea.device.explorer.monitor.ProcessTreeNode
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.ForceStopMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.KillMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.MenuContext
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.RefreshMenuItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

class DeviceMonitorViewImpl: DeviceMonitorView, DeviceMonitorActionsListener {
  private val panel = DeviceMonitorPanel()
  private val listeners = mutableListOf<DeviceMonitorViewListener>()

  override val modelListener
    get() = ModelListener()

  override val panelComponent: JComponent
    get() = panel.component

  override fun setup() {
    createTreePopupMenu()
    createToolbar()
  }

  override fun addListener(listener: DeviceMonitorViewListener) {
    listeners.add(listener)
  }
  override fun removeListener(listener: DeviceMonitorViewListener) {
    listeners.remove(listener)
  }

  private fun createTreePopupMenu() {
    ComponentPopupMenu(panel.tree).apply {
      addItem(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      addItem(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      install()
    }
  }

  private fun createToolbar() {
    createToolbarSubSection(DefaultActionGroup().apply {
      add(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action)
      add(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action) }, BorderLayout.WEST)

    createToolbarSubSection(DefaultActionGroup().apply {
      add(RefreshMenuItem(this@DeviceMonitorViewImpl).action) }, BorderLayout.EAST)
  }

  private fun createToolbarSubSection(group: DefaultActionGroup, layoutPosition: String) {
    val actionManager = ActionManager.getInstance()
    val actionToolbar = actionManager.createActionToolbar("Device Monitor Toolbar", group, true).apply {
      targetComponent = panel.tree
    }
    panel.toolbar.add(actionToolbar.component, layoutPosition)
  }

  override val selectedNodes: List<ProcessTreeNode>?
    get() {
      val paths = panel.tree.selectionPaths ?: return null
      val nodes = paths.mapNotNull { path -> ProcessTreeNode.fromNode(path.lastPathComponent) }.toList()
      return nodes.ifEmpty { null }
    }

  override fun refreshNodes(treeNodes: List<ProcessTreeNode>) {
    listeners.forEach(Consumer { it.refreshInvoked() })
  }

  override fun killNodes(treeNodes: List<ProcessTreeNode>) {
    listeners.forEach(Consumer { it.killNodesInvoked(treeNodes) })
  }

  override fun forceStopNodes(treeNodes: List<ProcessTreeNode>) {
    listeners.forEach(Consumer { it.forceStopNodesInvoked(treeNodes) })
  }

  inner class ModelListener : DeviceMonitorModelListener {
    override fun treeModelChanged(newTreeModel: DefaultTreeModel?, newTreeSelectionModel: DefaultTreeSelectionModel?) {
      val tree = panel.tree
      tree.model = newTreeModel
      tree.selectionModel = newTreeSelectionModel
      if (newTreeModel != null) {
        val rootNode = ProcessTreeNode.fromNode(newTreeModel.root)
        if (rootNode != null) {
          tree.isRootVisible = false
          tree.expandPath(TreePath(rootNode.path))
        }
        else {
          // Show root, since it contains an error message (ErrorNode)
          tree.isRootVisible = true
        }
      }
    }

  }
}