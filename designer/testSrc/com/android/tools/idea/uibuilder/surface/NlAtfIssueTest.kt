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
package com.android.tools.idea.uibuilder.surface

import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.validator.ValidatorData
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.containers.isEmpty

class NlAtfIssueTest : LayoutTestCase() {

  fun testCreate() {
    val category = "category"
    val msg = "msg"
    val srcClass = "srcClass"
    val type = ValidatorData.Type.ACCESSIBILITY
    val level = ValidatorData.Level.ERROR

    val result = ValidatorData.Issue.IssueBuilder()
      .setCategory(category)
      .setType(type)
      .setMsg(msg)
      .setLevel(level)
      .setSourceClass(srcClass)
      .build()
    val issueSource = IssueSource.NONE

    val atfIssue = NlAtfIssue(result, issueSource)

    assertEquals("Accessibility Issue", atfIssue.summary)
    assertEquals(msg, atfIssue.description)
    assertEquals(HighlightSeverity.ERROR, atfIssue.severity)
    assertTrue(atfIssue.fixes.isEmpty())
    assertNull(atfIssue.hyperlinkListener)
    assertEquals(issueSource, atfIssue.source)
    assertEquals(srcClass, atfIssue.srcClass)
  }

  fun testHyperlinkListener() {
    val helpfulLink = "www.google.com"
    val msg = "msg"

    val result = ValidatorData.Issue.IssueBuilder()
      .setCategory("category")
      .setType(ValidatorData.Type.ACCESSIBILITY)
      .setMsg(msg)
      .setLevel(ValidatorData.Level.ERROR)
      .setSourceClass("srcClass")
      .setHelpfulUrl(helpfulLink)
      .build()

    val atfIssue = NlAtfIssue(result, IssueSource.NONE)

    assertTrue(atfIssue.description.contains(msg))
    assertTrue(atfIssue.description.contains("""<a href="$helpfulLink">"""))
    assertNotNull(atfIssue.hyperlinkListener)
  }

  fun testIgnoreButton() {
    val result = ScannerTestHelper.createTestIssueBuilder().build()

    val atfIssue = NlAtfIssue(result, TestSource())

    assertEquals(1, atfIssue.fixes.count())
    atfIssue.fixes.forEach {  ignore ->
      assertEquals("Ignore", ignore.buttonText)
    }
  }

  fun testIgnoreClicked() {
    val testSrc = TestSource()
    val srcClass = "SrcClass"
    val result = ScannerTestHelper.createTestIssueBuilder()
      .setSourceClass(srcClass)
      .build()
    val atfIssue = NlAtfIssue(result, testSrc)

    assertEquals(1, atfIssue.fixes.count())
    atfIssue.fixes.forEach {  ignore ->
      // Simulate ignore button click
      ignore.runnable.run()

      assertEquals(TOOLS_URI, testSrc.setAttrArgCaptor.namespace)
      assertEquals(ATTR_IGNORE, testSrc.setAttrArgCaptor.attribute)
      assertEquals(srcClass, testSrc.setAttrArgCaptor.value)
    }
  }

  fun testIgnoreClickedIgnoreAlreadyExist() {
    val testSrc = TestSource()
    val getAttrResult = "hardcodedText,someOtherLintToIgnore,test"
    val srcClass = "SrcClass"

    testSrc.getAttrResult = getAttrResult
    val result = ScannerTestHelper.createTestIssueBuilder()
      .setSourceClass(srcClass)
      .build()
    val atfIssue = NlAtfIssue(result, testSrc)

    assertEquals(1, atfIssue.fixes.count())
    atfIssue.fixes.forEach {  ignore ->
      // Simulate ignore button click
      ignore.runnable.run()

      assertEquals(TOOLS_URI, testSrc.setAttrArgCaptor.namespace)
      assertEquals(ATTR_IGNORE, testSrc.setAttrArgCaptor.attribute)
      val expected = "$getAttrResult,$srcClass"
      assertEquals(expected, testSrc.setAttrArgCaptor.value)
    }
  }

  class TestSource : IssueSource, NlAttributesHolder {
    override val displayText: String = "displayText"
    override val onIssueSelected: (DesignSurface) -> Unit
      get() = TODO("Not yet implemented")

    var getAttrResult = ""
    override fun getAttribute(namespace: String?, attribute: String): String? {
      return getAttrResult
    }

    data class SetAttributeArgumentCaptor(var namespace: String? = null, var attribute: String = "", var value: String? = null)
    val setAttrArgCaptor = SetAttributeArgumentCaptor()
    override fun setAttribute(namespace: String?, attribute: String, value: String?) {
      setAttrArgCaptor.namespace = namespace
      setAttrArgCaptor.attribute = attribute
      setAttrArgCaptor.value = value
    }
  }
}