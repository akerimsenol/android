package com.android.tools.idea.compose.preview

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
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.HEIGHT_PARAMETER
import com.android.tools.idea.compose.preview.util.ParametrizedPreviewElementTemplate
import com.android.tools.idea.compose.preview.util.PreviewConfiguration
import com.android.tools.idea.compose.preview.util.PreviewDisplaySettings
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewParameter
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.android.tools.idea.compose.preview.util.WIDTH_PARAMETER
import com.android.tools.idea.compose.preview.util.toSmartPsiPointer
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.text.nullize
import org.jetbrains.android.compose.COMPOSABLE_FQ_NAMES
import org.jetbrains.android.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import org.jetbrains.android.compose.PREVIEW_ANNOTATION_FQNS
import org.jetbrains.android.compose.PREVIEW_PARAMETER_FQNS
import org.jetbrains.android.compose.findComposeLibraryNamespace
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.kotlin.KotlinUClassLiteralExpression
import org.jetbrains.uast.toUElementOfType

private fun UAnnotation.findAttributeIntValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Int

private fun UAnnotation.findAttributeFloatValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Float

private fun UAnnotation.findClassNameValue(name: String) =
  (findAttributeValue(name) as? KotlinUClassLiteralExpression)?.type?.canonicalText

/**
 * Looks up for annotation element using a set of annotation qualified names.
 *
 * @param fqName the qualified name to search
 * @return the first annotation element with the specified qualified name, or null if there is no annotation with such name.
 */
private fun UAnnotated.findAnnotation(fqName: Set<String>): UAnnotation? = uAnnotations.firstOrNull { fqName.contains(it.qualifiedName) }

/**
 * Reads the `@Preview` annotation parameters and returns a [PreviewConfiguration] containing the values.
 */
private fun attributesToConfiguration(node: UAnnotation): PreviewConfiguration {
  val apiLevel = node.findAttributeIntValue("apiLevel")
  val theme = node.findAttributeValue("theme")?.evaluateString()?.nullize()
  // Both width and height have to support old ("width") and new ("widthDp") conventions
  val width = node.findAttributeIntValue("width") ?: node.findAttributeIntValue(WIDTH_PARAMETER)
  val height = node.findAttributeIntValue("height") ?: node.findAttributeIntValue(HEIGHT_PARAMETER)
  val fontScale = node.findAttributeFloatValue("fontScale")
  val uiMode = node.findAttributeIntValue("uiMode")
  val device = node.findAttributeValue("device")?.evaluateString()?.nullize()

  return PreviewConfiguration.cleanAndGet(apiLevel, theme, width, height, fontScale, uiMode, device)
}

/**
 * Returns a list of [PreviewParameter] for the given [Collection<UParameter>]. If the parameters are annotated with
 * `PreviewParameter`, then they will be returned as part of the collection.
 */
private fun getPreviewParameters(parameters: Collection<UParameter>): Collection<PreviewParameter> =
  parameters.mapIndexedNotNull { index, parameter ->
    val annotation = parameter.findAnnotation(PREVIEW_PARAMETER_FQNS) ?: return@mapIndexedNotNull null
    val providerClassFqn = (annotation.findClassNameValue("provider")) ?: return@mapIndexedNotNull null
    val limit = annotation.findAttributeIntValue("limit") ?: Int.MAX_VALUE
    PreviewParameter(parameter.name, index, providerClassFqn, limit)
  }

/**
 * Converts the given [previewAnnotation] to a [PreviewElement].
 */
private fun previewAnnotationToPreviewElement(previewAnnotation: UAnnotation, annotatedMethod: UMethod): PreviewElement? {
  val uClass: UClass = annotatedMethod.uastParent as UClass
  val composableMethod = "${uClass.qualifiedName}.${annotatedMethod.name}"
  val previewName = previewAnnotation.findDeclaredAttributeValue("name")?.evaluateString() ?: annotatedMethod.name
  val groupName = previewAnnotation.findDeclaredAttributeValue("group")?.evaluateString()
  val showDecorations = previewAnnotation.findDeclaredAttributeValue("showDecoration")?.evaluate() as? Boolean ?: false
  val showBackground = previewAnnotation.findDeclaredAttributeValue("showBackground")?.evaluate() as? Boolean ?: false
  val backgroundColor = previewAnnotation.findDeclaredAttributeValue("backgroundColor")?.evaluate() as? Int

  // If the same composable functions is found multiple times, only keep the first one. This usually will happen during
  // copy & paste and both the compiler and Studio will flag it as an error.
  val displaySettings = PreviewDisplaySettings(previewName,
                                               groupName,
                                               showDecorations,
                                               showBackground,
                                               backgroundColor?.toString(16)?.let { "#$it" })

  val parameters = getPreviewParameters(annotatedMethod.uastParameters)
  val composeLibraryNamespace = previewAnnotation.findComposeLibraryNamespace()
  val basePreviewElement = SinglePreviewElementInstance(composableMethod,
                                                        displaySettings,
                                                        previewAnnotation.toSmartPsiPointer(),
                                                        annotatedMethod.uastBody.toSmartPsiPointer(),
                                                        attributesToConfiguration(previewAnnotation),
                                                        composeLibraryNamespace)
  return if (!parameters.isEmpty()) {
    if (StudioFlags.COMPOSE_PREVIEW_DATA_SOURCES.get()) {
      ParametrizedPreviewElementTemplate(basePreviewElement, parameters)
    }
    else null // No parameters allowed, ignore this annotation
  }
  else {
    return basePreviewElement
  }
}

/**
 * Returns true if the [UAnnotation] is a `@Preview` annotation.
 */
private fun UAnnotation.isPreviewAnnotation() = ReadAction.compute<Boolean, Throwable> {
  PREVIEW_ANNOTATION_FQNS.contains(qualifiedName)
}

/**
 * Converts the [UAnnotation] to a [PreviewElement] if the annotation is a `@Preview` annotation or returns null
 * if it's not.
 */
private fun UAnnotation.toPreviewElement(): PreviewElement? = ReadAction.compute<PreviewElement?, Throwable> {
  if (isPreviewAnnotation()) {
    val uMethod = getContainingUMethod()
    uMethod?.let {
      if (it.uastParameters.isNotEmpty()) {
        // We do not fail here. The ComposeViewAdapter will throw an exception that will be surfaced to the user
        Logger.getInstance(AnnotationFilePreviewElementFinder::class.java).debug("Preview functions must not have any parameters")
      }

      // The method must also be annotated with @Composable
      if (it.uAnnotations.any { annotation -> COMPOSABLE_FQ_NAMES.contains(annotation.qualifiedName) }) {
        return@compute previewAnnotationToPreviewElement(this, it)
      }
    }
  }
  return@compute  null
}

/**
 * [FilePreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  private fun findAllPreviewAnnotations(project: Project, vFile: VirtualFile): Sequence<UAnnotation> {
    if (DumbService.isDumb(project)) {
      Logger.getInstance(AnnotationFilePreviewElementFinder::class.java)
        .debug("findPreviewMethods called while indexing. No annotations will be found")
      return sequenceOf()
    }

    val kotlinAnnotations: Sequence<PsiElement> = ReadAction.compute<Sequence<PsiElement>, Throwable> {
      KotlinAnnotationsIndex.getInstance().get(COMPOSE_PREVIEW_ANNOTATION_NAME, project,
                                               GlobalSearchScope.fileScope(project, vFile)).asSequence()
    }

    return kotlinAnnotations
        .mapNotNull { ReadAction.compute<UAnnotation?, Throwable> { it.toUElementOfType() } }
        .filter { it.isPreviewAnnotation() }
  }

  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean =
    findAllPreviewAnnotations(project, vFile).any()

  /**
   * Returns all the `@Composable` functions in the [vFile] that are also tagged with `@Preview`.
   */
  override fun findPreviewMethods(project: Project, vFile: VirtualFile): Sequence<PreviewElement> {
    return findAllPreviewAnnotations(project, vFile)
      .mapNotNull { it.toPreviewElement() }
      .distinct()
  }
}