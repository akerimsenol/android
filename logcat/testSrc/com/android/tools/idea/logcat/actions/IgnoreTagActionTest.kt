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
package com.android.tools.idea.logcat.actions

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.DocumentAppender
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.MessageFormatter
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import java.time.ZoneId

/**
 * Tests for [IgnoreTagAction]
 */
@RunsInEdt
class IgnoreTagActionTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, logcatEditorRule, EdtRule(), disposableRule)

  private val editor get() = logcatEditorRule.editor
  private val logcatSettings = AndroidLogcatSettings()
  // We need to retain a reference to documentAppender so that ranges don't get GC'ed
  private val documentAppender by lazy { DocumentAppender(projectRule.project, editor.document, 1_000_000) }

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(AndroidLogcatSettings::class.java, logcatSettings, disposableRule.disposable)
  }

  @After
  fun tearDown() {
    LogcatToolWindowFactory.logcatPresenters.clear()
  }

  @Test
  fun update_caretOnTag() {
    val event = testActionEvent(editor)
    appendMessage(logcatMessage(tag = "tag"))
    editor.caretModel.moveToOffset(editor.document.text.indexOf("tag") + 1)

    IgnoreTagAction().update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("""Ignore Tag "tag"""")
  }

  @Test
  fun update_caretNotOnTag() {
    val event = testActionEvent(editor)
    appendMessage(logcatMessage(tag = "tag"))
    editor.caretModel.moveToOffset(editor.document.text.indexOf("tag") - 1)

    IgnoreTagAction().update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun actionPerformed() {
    val event = testActionEvent(editor)
    appendMessage(logcatMessage(tag = "tag"))
    editor.caretModel.moveToOffset(editor.document.text.indexOf("tag") + 1)
    val mockLogcatPresenter = mock<LogcatPresenter>()
    LogcatToolWindowFactory.logcatPresenters.add(mockLogcatPresenter)

    IgnoreTagAction().actionPerformed(event)

    assertThat(logcatSettings.ignoredTags).containsExactly("tag")
    verify(mockLogcatPresenter).reloadMessages()
  }

  private fun appendMessage(logcatMessage: LogcatMessage) {
    val messageFormatter = MessageFormatter(LogcatColors(), ZoneId.systemDefault())
    val textAccumulator = TextAccumulator()
    messageFormatter.formatMessages(FormattingOptions(), textAccumulator, listOf(logcatMessage))
    documentAppender.appendToDocument(textAccumulator)
  }
}

private fun testActionEvent(editor: EditorEx): TestActionEvent {
  return TestActionEvent(MapDataContext().apply {
    put(CommonDataKeys.EDITOR, editor)
    put(LOGCAT_PRESENTER_ACTION, FakeLogcatPresenter())
  })
}
