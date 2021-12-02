/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.ddmlib.Log
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KVALUE
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.or
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.VisibleForTesting

private const val PACKAGE_KEY = "package"
private val PACKAGE_KEYS = PACKAGE_KEY.getKeyVariants().toSet()

private const val TAG_KEY = "tag"
private val TAG_KEYS = TAG_KEY.getKeyVariants().toSet()

private val STRING_KEYS = listOf(
  "line",
  "message",
  PACKAGE_KEY,
  TAG_KEY,
)

private val LEVEL_KEYS = listOf(
  "fromLevel:",
  "level:",
  "toLevel:",
)

private const val AGE_KEY = "age:"

private const val PROJECT_APP = "app! "

private val KEYS = STRING_KEYS.map(String::getKeyVariants).flatten() + LEVEL_KEYS + AGE_KEY + PROJECT_APP

private val KEYS_LOOKUP_BUILDERS = KEYS.map(String::toLookupElement)

private val LEVEL_LOOKUPS = Log.LogLevel.values().map { it.name.toLookupElement(suffix = " ") }

/**
 * A [CompletionContributor] for the Logcat Filter Language.
 */
internal class LogcatFilterCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.VALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               result.addAllElements(KEYS_LOOKUP_BUILDERS)
             }
           })
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.KVALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               if (parameters.findPreviousText() in LEVEL_KEYS) {
                 result.addAllElements(LEVEL_LOOKUPS)
               }
             }
           })
    extend(CompletionType.BASIC, or(psiElement(STRING_KVALUE), psiElement(REGEX_KVALUE)),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               when (parameters.findPreviousText()) {
                 in PACKAGE_KEYS -> result.addAllElements(parameters.getPackageNames().map { it.toLookupElement(suffix = " ") })
                 in TAG_KEYS -> result.addAllElements(
                   parameters.getTags().filter { it.isNotBlank() }.map { it.toLookupElement(suffix = " ") })
               }
             }
           })
  }
}

@VisibleForTesting
internal fun String.getKeyVariants() = listOf("$this:", "-$this:", "$this~:", "-$this~:")

private fun String.toLookupElement(suffix: String = "") = LookupElementBuilder.create("$this$suffix")

private fun CompletionParameters.findPreviousText() = PsiTreeUtil.skipWhitespacesBackward(position)?.text

private fun CompletionParameters.getTags() =
  editor.getUserData(TAGS_PROVIDER_KEY)?.getTags() ?: throw IllegalStateException("Missing PackageNamesProvider")

private fun CompletionParameters.getPackageNames() =
  editor.getUserData(PACKAGE_NAMES_PROVIDER_KEY)?.getPackageNames() ?: throw IllegalStateException("Missing PackageNamesProvider")
