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
package com.android.tools.idea.res.psi

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.compiled.ClsFieldImpl
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper

class ResourceReferencePsiElementTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("res/values/colors.xml",
                               //language=XML
                               """<resources><color name="colorPrimary">#008577</color></resources>""")
  }

  fun testClsFieldImplKotlin() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.kt",
      //language=kotlin
      """
       package p1.p2
       class Foo {
         fun example() {
           android.R.color.b${caret}lack
         }
       }
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ClsFieldImpl::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(
      ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "black"))
  }

  fun testClsFieldImplJava() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.java",
      //language=java
      """
       package p1.p2;
       public class Foo {
         public static void example() {
           int black = android.R.color.bla${caret}ck;
         }
       }
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ClsFieldImpl::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(
      ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "black"))
  }

  fun testAndroidLightFieldKotlin() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.kt",
      //language=kotlin
      """
       package p1.p2
       class Foo {
         fun example() {
           R.color.color${caret}Primary
           android.R.color.black
         }
       }
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(AndroidLightField::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
  }

  fun testAndroidLightFieldJava() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.java",
      //language=java
      """
       package p1.p2;
       public class Foo {
         public static void example() {
           int colorPrimary = R.color.color${caret}Primary;
           int black = android.R.color.black;
         }
       }
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(AndroidLightField::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
  }

  fun testResourceReferencePsiElementDeclaration() {
    myFixture.configureByFile("/res/values/colors.xml")
    myFixture.moveCaret("colorPri|mary")
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
  }

  fun testLazyValueResourceElementWrapperLayoutResAuto() {
    val file = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
         <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:textColor="@color/col${caret}orPrimary"/>
        </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(LazyValueResourceElementWrapper::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
  }

  fun testFileResourceElementWrapperLayoutAndroid() {
    val file = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:textColor="@android:color/secondary${caret}_text_dark"/>
      </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(PsiFile::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(
      ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "secondary_text_dark"))
  }

  fun testXMLAttributeValueIdLayout() {
    val file = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:id="@+id/text${caret}view"
              android:layout_width="match_parent"
              android:layout_height="match_parent"/>
      </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(XmlAttributeValue::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "textview"))
  }

  fun testDrawableFile() {
    val file = myFixture.addFileToProject(
      "res/drawable/test.xml",
      //language=XML
      """<shape xmlns:android="http://schemas.android.com/apk/res/android"
        android:shape="rectangle"
        android:tint="#FF0000">
       </shape>""".trimMargin())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat(file).isInstanceOf(PsiFile::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(file)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "test"))
  }

  fun testStyleItemAndroid() {
    val psiFile = myFixture.addFileToProject(
      "res/values/styles.xml",
      //language=XML
       """
       <resources>
         <style name="TextAppearance.Theme.PlainText">
           <item name="android:textStyle"/>
         </style>
       </resources>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("android:textS|tyle")
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, "textStyle"))
  }

  fun testStyleItemResAuto() {
    myFixture.addFileToProject(
      "res/values/coordinatorlayout_attrs.xml",
      //language=XML
       """
        <resources>
          <declare-styleable name="CoordinatorLayout_Layout">
            <attr name="layout_behavior" format="string" />
          </declare-styleable>
        </resources>
        """.trimIndent())
    val psiFile = myFixture.addFileToProject(
      "res/values/styles.xml",
      //language=XML
       """
       <resources>
         <style name="TextAppearance.Theme.PlainText">
           <item name="layout_behavior"/>
         </style>
       </resources>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("la|yout_behavior")
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "layout_behavior"))
  }

  fun testReferenceStyleAttribute() {
    myFixture.addFileToProject(
      "res/values/attrs.xml",
      //language=XML
       """
        <resources>
          <attr name="button_text" format="string" />
         <style name="TextAppearance.Theme.PlainText">
           <item name="button_text">Example Text</item>
         </style>
        </resources>
        """.trimIndent())
    val file = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:text="?attr/button_text"/>
      </LinearLayout>
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.moveCaret("attr/butto|n_text")
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(LazyValueResourceElementWrapper::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement!!.resourceReference).isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "button_text"))
  }

  fun testElementRepresentationEquivalence() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.java",
      //language=java
      """
       package p1.p2;
       public class Foo {
         public static void example() {
           int colorPrimary = R.color.color${caret}Primary;
           int black = android.R.color.black;
         }
       }
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val androidLightField = myFixture.elementAtCaret
    assertThat(androidLightField).isInstanceOf(AndroidLightField::class.java)

    myFixture.configureByFile("/res/values/colors.xml")
    myFixture.moveCaret("colorPri|mary")
    val resourceReferencePsiElement = myFixture.elementAtCaret
    assertThat(resourceReferencePsiElement).isInstanceOf(ResourceReferencePsiElement::class.java)

    val layoutFile = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:textColor="@color/col${caret}orPrimary"/>
      </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(layoutFile.virtualFile)
    val lazyValueResourceElementWrapper = myFixture.elementAtCaret
    assertThat(lazyValueResourceElementWrapper).isInstanceOf(LazyValueResourceElementWrapper::class.java)

    val listOfElements = listOf(androidLightField, resourceReferencePsiElement, lazyValueResourceElementWrapper)
    for (element in listOfElements) {
      val resourceReferencePsiElement = ResourceReferencePsiElement.create(element)
      for (compareElement in listOfElements) {
        assertThat(resourceReferencePsiElement?.isEquivalentTo(compareElement)).isTrue()
      }
    }
  }
}