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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.ShadowedTreeNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.childNodes
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.PsVariable
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat
import java.awt.Color
import java.io.File
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class VariablesTableTest : AndroidGradleTestCase() {

  fun testModuleNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val rootNode = tableModel.root as DefaultMutableTreeNode
    assertThat(rootNode.childCount, equalTo(9))

    val projectNode = rootNode.getChildAt(0) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(projectNode, 0) as String, equalTo("testModuleNodeDisplay"))
    assertThat(tableModel.getValueAt(projectNode, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(projectNode.childCount, not(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(projectNode.path)), equalTo(true))

    val appNode = rootNode.getChildAt(1) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(appNode, 0) as String, equalTo("app"))
    assertThat(tableModel.getValueAt(appNode, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(appNode.childCount, not(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(appNode.path)), equalTo(false))

    val nested2Deep2Node = rootNode.getChildAt(2) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(nested2Deep2Node, 0) as String, equalTo("deep2"))
    assertThat(tableModel.getValueAt(nested2Deep2Node, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(nested2Deep2Node.childCount, equalTo(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(nested2Deep2Node.path)), equalTo(false))

    val javNode = rootNode.getChildAt(3) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(javNode, 0) as String, equalTo("jav"))
    assertThat(tableModel.getValueAt(javNode, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(javNode.childCount, equalTo(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(javNode.path)), equalTo(false))

    val libNode = rootNode.getChildAt(4) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(libNode, 0) as String, equalTo("lib"))
    assertThat(tableModel.getValueAt(libNode, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(libNode.childCount, equalTo(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(libNode.path)), equalTo(false))

    val nested1Node = rootNode.getChildAt(5) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(nested1Node, 0) as String, equalTo("nested1"))
    assertThat(tableModel.getValueAt(nested1Node, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(nested1Node.childCount, equalTo(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(nested1Node.path)), equalTo(false))

    val nested1DeepNode = rootNode.getChildAt(6) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(nested1DeepNode, 0) as String, equalTo("nested1-deep"))
    assertThat(tableModel.getValueAt(nested1DeepNode, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(nested1DeepNode.childCount, equalTo(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(nested1DeepNode.path)), equalTo(false))

    val nested2Node = rootNode.getChildAt(7) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(nested2Node, 0) as String, equalTo("nested2"))
    assertThat(tableModel.getValueAt(nested2Node, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(nested2Node.childCount, equalTo(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(nested2Node.path)), equalTo(false))

    val nested2DeepNode = rootNode.getChildAt(8) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(nested2DeepNode, 0) as String, equalTo("nested2-deep"))
    assertThat(tableModel.getValueAt(nested2DeepNode, 1), equalTo<Any>(ParsedValue.NotSet))
    assertThat(nested2DeepNode.childCount, equalTo(0))
    assertThat(variablesTable.tree.isExpanded(TreePath(nested2DeepNode.path)), equalTo(false))

    val row = variablesTable.tree.getRowForPath(TreePath(appNode.path))
    for (column in 0..1) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat<Color?>(component.background, equalTo(variablesTable.background))
    }
  }

  fun testTreeStructure() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[1]
    assertThat(rootNode.testStructure().toString().trimIndent(), equalTo("""
        app
            myVariable
            variable1
            anotherVariable
            varInt
            varBool
            varRefString
            varProGuardFiles
                0
                1
                (null)
            moreVariable
            mapVariable
                a
                b
                (null)
            """.trimIndent()))
  }

  fun testTreeStructure_addVariable() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val appModuleVariables = psProject.findModuleByName("app")?.variables
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    appModuleVariables?.addNewListVariable("varList")

    val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[1]
    assertThat(rootNode.testStructure().toString().trimIndent(), equalTo("""
        app
            myVariable
            variable1
            anotherVariable
            varInt
            varBool
            varRefString
            varProGuardFiles
                0
                1
                (null)
            moreVariable
            mapVariable
                a
                b
                (null)
            varList
                (null)
            """.trimIndent()))
  }

  fun testTreeStructure_removeListItem() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val appModuleVariables = psProject.findModuleByName("app")?.variables
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    appModuleVariables?.getVariable("varProGuardFiles")?.listItems?.findElement(0)?.delete()

    val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[1]
    assertThat(rootNode.testStructure().toString().trimIndent(), equalTo("""
        app
            myVariable
            variable1
            anotherVariable
            varInt
            varBool
            varRefString
            varProGuardFiles
                0
                (null)
            moreVariable
            mapVariable
                a
                b
                (null)
            """.trimIndent()))
  }

  fun testStringVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    assertThat(variableNode.variable.value, equalTo("3.0.1".asParsed<Any>()))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
    assertThat(tableModel.getValueAt(variableNode, 1), equalTo<Any>("3.0.1".asParsed()))

    val row = variablesTable.tree.getRowForPath(TreePath(variableNode.path))
    for (column in 0..1) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat(component.background, equalTo(variablesTable.background))
    }
  }

  fun testBooleanVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "varBool" == (it as VariableNode).toString() } as VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))

    assertThat(variableNode.variable.value, equalTo(true.asParsed<Any>()))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("varBool"))
    assertThat(tableModel.getValueAt(variableNode, 1), equalTo<Any>(true.asParsed()))
  }

  fun testVariableVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "varRefString" == (it as VariableNode).toString() } as VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))

    assertThat(
      variableNode.variable.value,
      equalTo<ParsedValue<Any>>(ParsedValue.Set.Parsed("1.3", DslText.Reference("variable1"))))
    assertThat(variableNode.variable.value.maybeValue is String, equalTo(true))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("varRefString"))
    assertThat(tableModel.getValueAt(variableNode, 1), equalTo<Any>(("variable1" to "1.3").asParsed()))
  }

  fun testListNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
    assertThat(listNode.variable.isList(), equalTo(true))
    assertThat(listNode.childCount, equalTo(3))
    assertThat(tableModel.getValueAt(listNode, 0) as String, equalTo("varProGuardFiles"))
    assertThat(tableModel.getValueAt(listNode, 1),
               equalTo<Any>(listOf("proguard-rules.txt".asParsed(), "proguard-rules2.txt".asParsed()).asParsed()))

    val firstElementNode = listNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("proguard-rules.txt".asParsed()))

    val secondElementNode = listNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("1"))
    assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo<Any>("proguard-rules2.txt".asParsed()))

    val emptyElement = listNode.getChildAt(2)
    assertThat(tableModel.getValueAt(emptyElement, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(emptyElement, 1), equalTo<Any>(ParsedValue.NotSet))

    val row = variablesTable.tree.getRowForPath(TreePath(listNode.path))
    for (column in 0..1) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat(component.background, equalTo(variablesTable.background))
    }
  }

  fun testMapNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
    assertThat(mapNode.variable.isMap(), equalTo(true))
    assertThat(mapNode.childCount, equalTo(3))
    assertThat(tableModel.getValueAt(mapNode, 0) as String, equalTo("mapVariable"))
    assertThat(tableModel.getValueAt(mapNode, 1),
               equalTo<Any>(mapOf("a" to "\"double\" quotes".asParsed(), "b" to "'single' quotes".asParsed()).asParsed()))

    val firstElementNode = mapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("\"double\" quotes".asParsed()))

    val secondElementNode = mapNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("b"))
    assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo<Any>("'single' quotes".asParsed()))

    val emptyElement = mapNode.getChildAt(2)
    assertThat(tableModel.getValueAt(emptyElement, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(emptyElement, 1), equalTo<Any>(ParsedValue.NotSet))

    val row = variablesTable.tree.getRowForPath(TreePath(mapNode.path))
    for (column in 0..1) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat(component.background, equalTo(variablesTable.background))
    }
  }

  fun testModuleNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
    assertThat(tableModel.isCellEditable(appNode, 0), equalTo(false))
  }

  fun testVariableNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
    assertThat(tableModel.isCellEditable(variableNode, 0), equalTo(true))

    tableModel.setValueAt("renamed", variableNode, 0)
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("renamed"))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val variableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
    assertThat(variableNames, hasItem("renamed"))
    assertThat(variableNames, not(hasItem("anotherVariable")))
  }

  fun testListNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
    assertThat(listNode.variable.isList(), equalTo(true))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0)
    assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(false))
  }

  fun testMapNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
    assertThat(mapNode.variable.isMap(), equalTo(true))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(true))

    tableModel.setValueAt("renamed", firstElementNode, 0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("renamed"))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
    val keyNames = newMapNode.children().asSequence().map { it.toString() }.toList()
    assertThat(keyNames, hasItem("renamed"))
    assertThat(keyNames, not(hasItem("a")))
  }

  fun testModuleNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
    assertThat(tableModel.isCellEditable(appNode, 1), equalTo(false))
  }

  fun testVariableNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))
    assertThat(tableModel.getValueAt(variableNode, 1), equalTo<Any>("3.0.1".asParsed()))
    assertThat(tableModel.isCellEditable(variableNode, 1), equalTo(true))

    tableModel.setValueAt("3.0.1".asParsed().annotated(), variableNode, 1)
    assertThat(variableNode.variable.parent.isModified, equalTo(false))

    tableModel.setValueAt("new value".asParsed().annotated(), variableNode, 1)
    assertThat(tableModel.getValueAt(variableNode, 1), equalTo<Any>("new value".asParsed()))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newVariableNode = newAppNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
    assertThat(newVariableNode.getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
  }

  fun testListNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
    assertThat(listNode.variable.isList(), equalTo(true))
    assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0) as ListItemNode
    assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("proguard-rules.txt".asParsed()))
    assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

    tableModel.setValueAt("proguard-rules.txt".asParsed().annotated(), firstElementNode, 1)
    assertThat(firstElementNode.variable.parent.isModified, equalTo(false))

    tableModel.setValueAt("new value".asParsed().annotated(), firstElementNode, 1)
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("new value".asParsed()))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newListNode = newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
    assertThat((newListNode.getChildAt(0) as ListItemNode).getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
  }

  fun testMapNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
    assertThat(mapNode.variable.isMap(), equalTo(true))
    assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0) as MapItemNode
    assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("\"double\" quotes".asParsed()))
    assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

    tableModel.setValueAt("\"\"double\" quotes\"".asParsed().annotated(), firstElementNode, 1)
    assertThat(firstElementNode.variable.parent.isModified, equalTo(false))

    tableModel.setValueAt("new value".asParsed().annotated(), firstElementNode, 1)
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("new value".asParsed()))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
    assertThat((newMapNode.getChildAt(0) as MapItemNode).getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
  }

  fun testAddSimpleVariable() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVariable")))

    variablesTable.selectNode(appNode)
    variablesTable.addVariable(GradlePropertyModel.ValueType.STRING)
    variablesTable.simulateTextInput("newVariable")

    val variableNode = appNode.children().asSequence().find { "newVariable" == (it as VariableNode).toString() } as VariableNode
    variableNode.setValue("new value".asParsed())

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newVariableNode = newAppNode.children().asSequence().find { "newVariable" == (it as VariableNode).toString() } as VariableNode
    assertThat(newVariableNode.getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
  }

  fun testAddList() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newList")))

    variablesTable.selectNode(appNode)
    variablesTable.addVariable(GradlePropertyModel.ValueType.LIST)
    variablesTable.simulateTextInput("newList")

    val variableNode = appNode.children().asSequence().find { "newList" == (it as VariableNode).toString() } as VariableNode
    assertThat(variableNode.childCount, equalTo(1))
    assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path)), equalTo(true))

    tableModel.setValueAt("list item".asParsed().annotated(), variableNode.getChildAt(0), 1)
    assertThat(variableNode.childCount, equalTo(2))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newListNode = newAppNode.children().asSequence().find { "newList" == (it as VariableNode).toString() } as VariableNode

    val firstElementNode = newListNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("list item".asParsed()))

    val secondElementNode = newListNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo<Any>(ParsedValue.NotSet))
  }

  fun testAddMap() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newMap")))

    variablesTable.selectNode(appNode)
    variablesTable.addVariable(GradlePropertyModel.ValueType.MAP)
    variablesTable.simulateTextInput("newMap")

    val variableNode = appNode.children().asSequence().find { "newMap" == (it as VariableNode).toString() } as VariableNode
    assertThat(variableNode.childCount, equalTo(1))
    assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path)), equalTo(true))

    tableModel.setValueAt("key", variableNode.getChildAt(0), 0)
    tableModel.setValueAt("value".asParsed().annotated(), variableNode.getChildAt(0), 1)
    assertThat(variableNode.childCount, equalTo(2))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newMapNode = newAppNode.children().asSequence().find { "newMap" == (it as VariableNode).toString() } as VariableNode

    val firstElementNode = newMapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("key"))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("value".asParsed()))

    val secondElementNode = newMapNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo<Any>(ParsedValue.NotSet))
  }

  fun testAddEmptyVariable() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    assertThat(appNode.childCount, equalTo(9))

    variablesTable.selectNode(appNode)
    variablesTable.addVariable(GradlePropertyModel.ValueType.STRING)
    assertThat(appNode.childCount, equalTo(10))
    variablesTable.editingStopped(null)
    assertThat(appNode.childCount, equalTo(9))

    variablesTable.selectNode(appNode)
    variablesTable.addVariable(GradlePropertyModel.ValueType.STRING)
    assertThat(appNode.childCount, equalTo(10))
    variablesTable.editingCanceled(null)
    assertThat(appNode.childCount, equalTo(9))
  }

  fun testVariableNodeDelete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val childCount = appNode.childCount
    val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
    variablesTable.selectNode(variableNode)
    variablesTable.deleteSelectedVariables()

    val variableNames = appNode.children().asSequence().map { it.toString() }.toList()
    assertThat(variableNames, not(hasItem("anotherVariable")))
    assertThat(appNode.childCount, equalTo(childCount - 1))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newVariableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
    assertThat(newVariableNames, not(hasItem("anotherVariable")))
    assertThat(newAppNode.childCount, equalTo(childCount - 1))
  }

  fun testListNodeDelete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
    assertThat(listNode.variable.isList(), equalTo(true))
    val childCount = listNode.childCount

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0) as ListItemNode
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("proguard-rules.txt".asParsed()))

    variablesTable.selectNode(firstElementNode)
    variablesTable.deleteSelectedVariables()

    val listNodeFirstChild = listNode.getChildAt(0) as ListItemNode
    assertThat(tableModel.getValueAt(listNodeFirstChild, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(listNodeFirstChild, 1), equalTo<Any>("proguard-rules2.txt".asParsed()))
    assertThat(listNode.childCount, equalTo(childCount - 1))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newListNode = newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
    variablesTable.tree.expandPath(TreePath(listNode.path))
    val newFirstElementNode = newListNode.getChildAt(0) as ListItemNode
    assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(newFirstElementNode, 1), equalTo<Any>("proguard-rules2.txt".asParsed()))
    assertThat(newListNode.childCount, equalTo(childCount - 1))
  }

  fun testMapNodeDelete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val variablesTable = VariablesTable(project, psProject, testRootDisposable)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
    assertThat(mapNode.variable.isMap(), equalTo(true))
    val childCount = mapNode.childCount

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0) as MapItemNode
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo<Any>("\"double\" quotes".asParsed()))

    variablesTable.selectNode(firstElementNode)
    variablesTable.deleteSelectedVariables()

    val mapNodeFirstChild = mapNode.getChildAt(0) as MapItemNode
    assertThat(tableModel.getValueAt(mapNodeFirstChild, 0) as String, equalTo("b"))
    assertThat(tableModel.getValueAt(mapNodeFirstChild, 1), equalTo<Any>("'single' quotes".asParsed()))
    assertThat(mapNode.childCount, equalTo(childCount - 1))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psProject, testRootDisposable).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
    val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
    val newFirstElementNode = mapNode.getChildAt(0) as MapItemNode
    assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String, equalTo("b"))
    assertThat(tableModel.getValueAt(newFirstElementNode, 1), equalTo<Any>("'single' quotes".asParsed()))
    assertThat(newMapNode.childCount, equalTo(childCount - 1))
  }
}

private val DefaultMutableTreeNode.appModuleChild: Any?
  get() = children().asSequence().find { it.toString() == "app" } as ModuleNode

private fun PsProject.applyAllChanges() {
  if (isModified) {
    applyChanges()
  }
  forEachModule(Consumer { module ->
    if (module.isModified) {
      module.applyChanges()
    }
  })
}

private fun VariablesTable.simulateTextInput(input: String) {
  val editorComp = editorComponent as JPanel
  val textBox = editorComp.components.first { it is VariableAwareTextBox } as VariableAwareTextBox
  textBox.text = input
  editingStopped(null)
}

private fun VariablesTable.selectNode(node: VariablesBaseNode) {
  tree.selectionPath = TreePath(node.path)
}

private fun PsVariable.isList() = value.maybeLiteralValue is List<*>
private fun PsVariable.isMap() = value.maybeLiteralValue is Map<*, *>
