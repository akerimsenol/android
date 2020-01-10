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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_SUPPORT
import com.android.tools.idea.databinding.module.ModuleDataBinding
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.renameElementAtCaretUsingAndroidHandler
import com.google.common.collect.Lists
import com.intellij.ide.DataManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test of rename refactoring involving Java language elements generated by Data Binding or methods declared in Java language elements.
 */

@RunWith(Parameterized::class)
class DataBindingRenameTest(private val dataBindingMode: DataBindingMode) {
  companion object {
    @Suppress("unused") // Used by JUnit
    @get:Parameterized.Parameters(name = "{0}")
    @get:JvmStatic
    val parameters: List<DataBindingMode>
      get() = Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  private val myProjectRule = AndroidGradleProjectRule()
  private val myEdtRule = EdtRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(myProjectRule).around(myEdtRule)!!

  @Before
  fun setUp() {
    myProjectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    myProjectRule.load(when (dataBindingMode) {
                         DataBindingMode.SUPPORT -> PROJECT_WITH_DATA_BINDING_SUPPORT
                         else -> PROJECT_WITH_DATA_BINDING_ANDROID_X
                       })

    val syncState = GradleSyncState.getInstance(myProjectRule.project)
    assertFalse(syncState.isSyncNeeded().toBoolean())
    assertEquals(ModuleDataBinding.getInstance(myProjectRule.androidFacet).dataBindingMode,
                 dataBindingMode)

    // Make sure that all file system events up to this point have been processed.
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun checkAndRename(newName: String) {
    val action = RenameElementAction()
    TestActionEvent(DataManager.getInstance().getDataContext(myProjectRule.fixture.editor.component), action).let {
      action.update(it)
      assertTrue(it.presentation.isEnabled && it.presentation.isVisible)
    }

    if (!myProjectRule.fixture.renameElementAtCaretUsingAndroidHandler(newName)) {
      myProjectRule.fixture.renameElementAtCaret(newName)
    }
    // Save the renaming changes to disk.
    saveAllDocuments()
  }

  private fun saveAllDocuments() {
    runWriteAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }

  /**
   * Checks renaming of a resource IDs when a Java field generated from that resource by Data Binding is renamed.
   *
   * @see com.android.tools.idea.databinding.DataBindingRenamer
   */
  @Test
  @RunsInEdt
  fun assertRenameFieldDerivedFromResource() {
    val file = myProjectRule.project.baseDir
      .findFileByRelativePath("app/src/main/java/com/android/example/appwithdatabinding/MainActivity.java")
    myProjectRule.fixture.configureFromExistingVirtualFile(file!!)
    val editor = myProjectRule.fixture.editor
    val text = editor.document.text
    val offset = text.indexOf("regularView")
    assertTrue(offset > 0)
    editor.caretModel.moveToOffset(offset)
    val layoutFile = myProjectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    val layoutText = VfsUtilCore.loadText(layoutFile)
    // Rename regularView to nameAfterRename in MainActivity.java.
    checkAndRename("nameAfterRename")
    // Check results.
    assertEquals(text.replace("regularView", "nameAfterRename"), VfsUtilCore.loadText(file))
    assertEquals(layoutText.replace("regular_view", "name_after_rename"), VfsUtilCore.loadText(layoutFile))
  }

  /**
   * Checks renaming of method referenced from data binding expression.
   */
  @Test
  @RunsInEdt
  fun assertRenameMethod() {
    val file = myProjectRule.project.baseDir
      .findFileByRelativePath("app/src/main/java/com/android/example/appwithdatabinding/DummyVo.java")
    myProjectRule.fixture.configureFromExistingVirtualFile(file!!)
    val editor = myProjectRule.fixture.editor
    val text = editor.document.text
    val offset = text.indexOf("initialString")
    assertTrue(offset > 0)
    editor.caretModel.moveToOffset(offset)
    val layoutFile = myProjectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    val layoutText = VfsUtilCore.loadText(layoutFile)
    // Rename initialString to renamedString in DummyVo.java.
    checkAndRename("renamedString")
    // Check results.
    assertEquals(text.replace("initialString", "renamedString"), VfsUtilCore.loadText(file))
    assertEquals(layoutText.replace("initialString", "renamedString"), VfsUtilCore.loadText(layoutFile))
  }

  /**
   * Checks renaming of field referenced from data binding expression.
   */
  @Test
  @RunsInEdt
  fun assertRenameField() {
    val file = myProjectRule.project.baseDir
      .findFileByRelativePath("app/src/main/java/com/android/example/appwithdatabinding/DummyVo.java")
    myProjectRule.fixture.configureFromExistingVirtualFile(file!!)
    val editor = myProjectRule.fixture.editor
    val text = editor.document.text
    myProjectRule.fixture.moveCaret("unique|Name")
    val layoutFile = myProjectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    val layoutText = VfsUtilCore.loadText(layoutFile)
    // Rename name to newName in DummyVo.java.
    checkAndRename("newName")
    // Check results.
    assertEquals(text.replace("uniqueName", "newName"), VfsUtilCore.loadText(file))
    assertEquals(layoutText.replace("uniqueName", "newName"), VfsUtilCore.loadText(layoutFile))
  }

  /**
   * Checks renaming of field getter referenced from data binding expression.
   */
  @Test
  @RunsInEdt
  fun assertRenameGetter() {
    val file = myProjectRule.project.baseDir
      .findFileByRelativePath("app/src/main/java/com/android/example/appwithdatabinding/DummyVo.java")
    myProjectRule.fixture.configureFromExistingVirtualFile(file!!)
    val editor = myProjectRule.fixture.editor
    val text = editor.document.text
    myProjectRule.fixture.moveCaret("get|UniqueValue")
    val layoutFile = myProjectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    val layoutText = VfsUtilCore.loadText(layoutFile)
    // Rename name to newName in DummyVo.java.
    checkAndRename("getNewValue")
    // Check results.
    assertEquals(text.replace("getUniqueValue", "getNewValue"), VfsUtilCore.loadText(file))
    assertEquals(layoutText.replace("uniqueValue", "newValue"), VfsUtilCore.loadText(layoutFile))
  }

  /**
   * Checks renaming of resources referenced from data binding expression.
   */
  @Test
  @RunsInEdt
  fun assertRenameResource() {
    val file = myProjectRule.project.baseDir
      .findFileByRelativePath("app/src/main/res/values/strings.xml")
    myProjectRule.fixture.configureFromExistingVirtualFile(file!!)
    val text = myProjectRule.fixture.editor.document.text
    myProjectRule.fixture.moveCaret("hello|_world")
    val layoutFile = myProjectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    val layoutText = VfsUtilCore.loadText(layoutFile)
    // Rename name to "hello" in strings.xml.
    checkAndRename("hello")
    // Check results.
    assertEquals(text.replace("hello_world", "hello"), VfsUtilCore.loadText(file))
    assertEquals(layoutText.replace("hello_world", "hello"), VfsUtilCore.loadText(layoutFile))
  }
}
