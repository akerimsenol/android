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
package com.android.tools.idea.concurrency

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.ui.ApplicationUtils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class FlowsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @OptIn(ExperimentalTime::class)
  @Test
  fun `test psiFileChangeFlow`() {
    val psiFile = projectRule.fixture.addFileToProject("src/Test.kt", """
      fun test() {
      }
    """.trimIndent())

    runBlocking {
      launch(workerThread) {
        withTimeout(5.seconds) {
          try {
            psiFileChangeFlow(projectRule.project, projectRule.testRootDisposable)
              .take(3)
              .filter { PsiManager.getInstance(projectRule.project).areElementsEquivalent(psiFile, it) }
              .collect()
          } catch (_: TimeoutCancellationException) {
            Assertions.fail("Timeout waiting for the changes")
          }
        }
      }

      ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
        projectRule.fixture.openFileInEditor(psiFile.virtualFile)
      }

      // Make 3 changes that should trigger *at least* 3 flow elements
      WriteCommandAction.runWriteCommandAction(projectRule.project) {
        projectRule.fixture.editor.executeAndSave { insertText(" // Comment\n") }
      }
      WriteCommandAction.runWriteCommandAction(projectRule.project) {
        projectRule.fixture.editor.executeAndSave { insertText("fun test2() {}\n") }
      }
      WriteCommandAction.runWriteCommandAction(projectRule.project) {
        projectRule.fixture.editor.executeAndSave { insertText("Broken") }
      }
    }
  }
}