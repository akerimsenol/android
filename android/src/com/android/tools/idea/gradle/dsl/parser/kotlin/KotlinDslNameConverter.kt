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
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import kotlin.jvm.JvmDefault

import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*

interface KotlinDslNameConverter: GradleDslNameConverter {
  @JvmDefault
  override fun psiToName(element: PsiElement): String {
    return when (element) {
      is KtExpression -> gradleNameFor(element) ?: element.text
      else -> element.text
    }
  }

  @JvmDefault
  override fun convertReferenceText(context: GradleDslElement, referenceText: String): String {
    val referencePsi = KtPsiFactory(context.dslFile.project).createExpression(referenceText)
    return gradleNameFor(referencePsi) ?: referenceText
  }

  @JvmDefault
  override fun externalNameForParent(modelName: String, context: GradleDslElement): Pair<String, Boolean?> {
    val map = context.getExternalToModelMap(this)
    val defaultResult : Pair<String, Boolean?> = modelName to null
    var result : Pair<String, Boolean?>? = null
    for (e in map.entries) {
      if (e.value.first == modelName ) {
        // prefer assignment if possible, or otherwise the first appropriate method we find
        when (e.value.second) {
          VAR, VWO -> return e.key.first to false
          SET, OTHER -> if (result == null) result = e.key.first to true
          else -> Unit
        }
      }
    }
    return result ?: defaultResult
  }

  @JvmDefault
  override fun modelNameForParent(externalName: String, context: GradleDslElement): String {
    val map = context.getExternalToModelMap(this)
    for (e in map.entries) {
      if (e.key.first == externalName) return e.value.first
    }
    return externalName
  }
}