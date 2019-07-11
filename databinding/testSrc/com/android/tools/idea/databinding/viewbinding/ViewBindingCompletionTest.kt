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
package com.android.tools.idea.databinding.viewbinding

import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.isViewBindingEnabled
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ViewBindingCompletionTest {
  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val editorManager
    get() = FileEditorManager.getInstance(projectRule.project)

  private val facet
    get() = projectRule.androidFacet

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.PROJECT_FOR_VIEWBINDING)
    assertThat(facet.isViewBindingEnabled()).isTrue()
  }

  @Test
  fun completeViewBindingClass() {
    val file = fixture.addClass("""
      package com.android.example.viewbinding;
      
      import com.android.example.viewbinding.databinding.ActivityMainBinding;

      public class Model {
        ActivityMainB<caret>
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.containingFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      package com.android.example.viewbinding;
      
      import com.android.example.viewbinding.databinding.ActivityMainBinding;

      public class Model {
        ActivityMainBinding
      }
    """.trimIndent())
  }

  @Test
  fun completeViewBindingField() {
    val file = fixture.addClass("""
      package com.android.example.viewbinding;
      
      import android.app.Activity;
      import android.os.Bundle;
      import com.android.example.viewbinding.databinding.ActivityMainBinding;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
              binding.test<caret>
          }
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.containingFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult("""
      package com.android.example.viewbinding;
      
      import android.app.Activity;
      import android.os.Bundle;
      import com.android.example.viewbinding.databinding.ActivityMainBinding;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
              binding.testId
          }
      }
    """.trimIndent())
  }
}