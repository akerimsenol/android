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
package com.android.tools.idea.ui

import com.android.tools.idea.diagnostics.report.FileInfo
import com.android.tools.idea.util.ZipData
import com.android.tools.idea.util.zipFiles
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode

private const val PRIVACY_TEXT =
  "By creating this report, you acknowledge that Google may use information included in the report and " +
  "other attached files to diagnose technical issues and to improve our products and services, in accordance with our " +
  "<a href=\"http://www.google.com/policies/privacy/\">Privacy Policy</a>, and you agree that we may share it with engineering partners" +
  " potentially impacted by your issue. This report will include personal information logged by your computer, such as file names, " +
  "installed plugins, usage data, system information such as memory and processes, and machine specifications."

class CreateDiagnosticReportDialog(private val project: Project?, files: List<FileInfo>) : DialogWrapper(project) {
  private val fileTree: Tree
  private val grid = JPanel(GridBagLayout())
  private val contents: JBTextArea

  init {
    title = "Create Diagnostic Report"
    isResizable = false
    isModal = true
    myOKAction.putValue(Action.NAME, "Create")

    grid.apply {
      val filesLabel = JLabel().apply {
        text = "Files to include:"
      }

      val constraints = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        anchor = GridBagConstraints.NORTHWEST
        insets = JBUI.insets(10, 20, 0, 0)
      }

      add(filesLabel, constraints)

      fileTree = buildTree(files)
      fileTree.preferredSize = null

      val treeScrollPane = JScrollPane(fileTree).apply {
        preferredSize = Dimension(300, 300)
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
      }

      constraints.apply {
        gridx = 0
        gridy = 1
      }

      add(treeScrollPane, constraints)

      contents = JBTextArea()

      val contentsScrollPane = JScrollPane(contents).apply {
        preferredSize = Dimension(800, 300)
      }

      constraints.apply {
        gridx = 1
        gridy = 1
      }

      add(contentsScrollPane, constraints)

      val privacy = JEditorPane("text/html", PRIVACY_TEXT).apply {
        isEditable = false
        background = JBColor(UIUtil.TRANSPARENT_COLOR, UIUtil.TRANSPARENT_COLOR)
        preferredSize = Dimension(1100, 100)
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)

        addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            if (Desktop.isDesktopSupported()) {
              Desktop.getDesktop().browse(e.url.toURI());
            }
          }
        }
      }

      constraints.apply {
        gridx = 0
        gridy = 2
        gridwidth = 2
      }

      add(privacy, constraints)
    }

    init()
  }

  override fun createCenterPanel(): JComponent = grid

  override fun doOKAction() {
    val descriptor = FileSaverDescriptor("Save Diagnostic Report As", "Choose a location for saving the diagnostic report", "zip")
    val saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

    val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val file = "DiagnosticsReport${dateTime}.zip"

    val saveFileWrapper = saveFileDialog.save(VfsUtil.getUserHomeDir(), file)
    if (saveFileWrapper != null) {
      val list = buildList()
      val zipInfo = list.map { ZipData(it.source.toString(), it.destination.toString()) }.toTypedArray()
      val path = saveFileWrapper.file.toString()
      zipFiles(zipInfo, path)
      super.doOKAction()
    }
  }

  private fun buildTree(list: List<FileInfo>): Tree {
    val root = FileTreeNode("Attached Files")

    for (file in list.sortedBy { it.destination.toString() }) {
      addFilesToTree(root, file)
    }

    return CheckboxTree(FileTreeRenderer(), root).apply {
      preferredSize = Dimension(300, 200)
      addTreeSelectionListener { selectionEvent ->
        val node = selectionEvent.newLeadSelectionPath?.lastPathComponent as? FileTreeNode
        updateContents(node)
      }
    }
  }

  private fun updateContents(node: FileTreeNode?) {
    contents.text = node?.fileInfo?.let {
      try {
        Files.readString(it.source)
      }
      catch (e: IOException) {
        null
      }
    } ?: ""

    contents.select(0, 0)

  }

  private fun addFilesToTree(root: DefaultMutableTreeNode, file: FileInfo) {
    val tokens = file.destination.toString().split('/')
    var current = root
    for (i in 0..tokens.size - 2) {
      val token = tokens[i]
      if (current.childCount > 0) {
        val lastChild = current.lastChild as DefaultMutableTreeNode
        if (lastChild.userObject as String == token) {
          current = lastChild
          continue
        }
      }
      val newNode = FileTreeNode(token)
      current.add(newNode)
      current = newNode
    }

    current.add(FileTreeNode(tokens.last(), file))
  }

  private fun buildList(): List<FileInfo> {
    val root = fileTree.model.root as FileTreeNode
    val list = mutableListOf<FileInfo>()
    addFilesToList(root, list)
    return list
  }

  private fun addFilesToList(node: FileTreeNode, list: MutableList<FileInfo>) {
    if (node.isChecked) {
      node.fileInfo?.let { list.add(it) }
    }

    for (child in node.children()) {
      addFilesToList(child as FileTreeNode, list)
    }
  }

  private class FileTreeRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
    override fun customizeRenderer(tree: JTree?,
                                   value: Any?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean) {
      (value as? DefaultMutableTreeNode)?.let {
        textRenderer.append(it.userObject as String)
      }

      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }

  class FileTreeNode(userObject: String, val fileInfo: FileInfo? = null) : CheckedTreeNode(userObject)
}
