/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.dagger.concepts.ComponentAndModuleDaggerConcept.annotationArgumentsByDataType
import com.android.tools.idea.dagger.concepts.ComponentAndModuleDaggerConcept.annotationsByDataType
import com.android.tools.idea.dagger.concepts.DaggerElement.Type
import com.android.tools.idea.dagger.index.DaggerConceptIndexer
import com.android.tools.idea.dagger.index.DaggerConceptIndexers
import com.android.tools.idea.dagger.index.IndexEntries
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexClassWrapper
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import java.io.DataInput
import java.io.DataOutput
import java.util.EnumMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.psi.KtClass

/**
 * Represents a Component, Subcomponent, or Module in Dagger.
 *
 * Example:
 * ```java
 *   @Component(modules = DripCoffeeModule.class)
 *   interface CoffeeShop {
 *     CoffeeMaker maker();
 *   }
 * ```
 *
 * All three recognized items in this [DaggerConcept] are classes with a corresponding annotation.
 * Components, Subcomponents, and Modules can all reference each other via various arguments on
 * those annotations.
 */
object ComponentAndModuleDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(classIndexers = listOf(ComponentIndexer))
  override val indexValueReaders: List<IndexValue.Reader> =
    listOf(
      ClassIndexValue.Reader.ComponentWithModule,
      ClassIndexValue.Reader.ComponentWithDependency,
      ClassIndexValue.Reader.SubcomponentWithModule,
      ClassIndexValue.Reader.ModuleWithInclude,
      ClassIndexValue.Reader.ModuleWithSubcomponent,
    )
  override val daggerElementIdentifiers: DaggerElementIdentifiers = ClassIndexValue.identifiers

  internal val annotationsByDataType =
    EnumMap(
      mapOf(
        IndexValue.DataType.COMPONENT_WITH_MODULE to DaggerAnnotations.COMPONENT,
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY to DaggerAnnotations.COMPONENT,
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE to DaggerAnnotations.SUBCOMPONENT,
        IndexValue.DataType.MODULE_WITH_INCLUDE to DaggerAnnotations.MODULE,
        IndexValue.DataType.MODULE_WITH_SUBCOMPONENT to DaggerAnnotations.MODULE,
      )
    )

  internal val annotationArgumentsByDataType =
    EnumMap(
      mapOf(
        IndexValue.DataType.COMPONENT_WITH_MODULE to "modules",
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY to "dependencies",
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE to "modules",
        IndexValue.DataType.MODULE_WITH_INCLUDE to "includes",
        IndexValue.DataType.MODULE_WITH_SUBCOMPONENT to "subcomponents",
      )
    )
}

private object ComponentIndexer : DaggerConceptIndexer<DaggerIndexClassWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexClassWrapper, indexEntries: IndexEntries) {
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.COMPONENT_WITH_MODULE, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.MODULE_WITH_INCLUDE, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, indexEntries)
  }

  private fun lookForClassesOnAnnotation(
    wrapper: DaggerIndexClassWrapper,
    dataType: IndexValue.DataType,
    indexEntries: IndexEntries
  ) {
    val annotationName = annotationsByDataType[dataType]!!
    val annotationArgumentName = annotationArgumentsByDataType[dataType]!!
    val listedClasses =
      wrapper.getAnnotationsByName(annotationName).flatMap { annotation ->
        annotation.getArgumentClassNames(annotationArgumentName)
      }
    for (className in listedClasses) {
      val indexValue = ClassIndexValue(dataType, wrapper.getFqName())
      val classSimpleName =
        className.substringAfterLast('.', /* missingDelimiterValue = */ className)
      indexEntries.addIndexValue(classSimpleName, indexValue)
    }
  }
}

@VisibleForTesting
internal data class ClassIndexValue(
  override val dataType: DataType,
  private val classFqName: String
) : IndexValue() {

  override fun save(output: DataOutput) {
    output.writeString(classFqName)
  }

  class Reader
  private constructor(override val supportedType: DataType, val factory: (String) -> IndexValue) :
    IndexValue.Reader {
    override fun read(input: DataInput) = factory.invoke(input.readString())

    companion object {
      val ComponentWithModule =
        Reader(DataType.COMPONENT_WITH_MODULE) { classFqName ->
          ClassIndexValue(DataType.COMPONENT_WITH_MODULE, classFqName)
        }
      val ComponentWithDependency =
        Reader(DataType.COMPONENT_WITH_DEPENDENCY) { classFqName ->
          ClassIndexValue(DataType.COMPONENT_WITH_DEPENDENCY, classFqName)
        }
      val SubcomponentWithModule =
        Reader(DataType.SUBCOMPONENT_WITH_MODULE) { classFqName ->
          ClassIndexValue(DataType.SUBCOMPONENT_WITH_MODULE, classFqName)
        }
      val ModuleWithInclude =
        Reader(DataType.MODULE_WITH_INCLUDE) { classFqName ->
          ClassIndexValue(DataType.MODULE_WITH_INCLUDE, classFqName)
        }
      val ModuleWithSubcomponent =
        Reader(DataType.MODULE_WITH_SUBCOMPONENT) { classFqName ->
          ClassIndexValue(DataType.MODULE_WITH_SUBCOMPONENT, classFqName)
        }
    }
  }

  companion object {
    private val identifyClassKotlin =
      DaggerElementIdentifier<KtClass> {
        when {
          it.hasAnnotation(DaggerAnnotations.COMPONENT) -> ClassDaggerElement(it, Type.COMPONENT)
          it.hasAnnotation(DaggerAnnotations.SUBCOMPONENT) ->
            ClassDaggerElement(it, Type.SUBCOMPONENT)
          it.hasAnnotation(DaggerAnnotations.MODULE) -> ClassDaggerElement(it, Type.MODULE)
          else -> null
        }
      }

    private val identifyClassJava =
      DaggerElementIdentifier<PsiClass> {
        when {
          it.hasAnnotation(DaggerAnnotations.COMPONENT) -> ClassDaggerElement(it, Type.COMPONENT)
          it.hasAnnotation(DaggerAnnotations.SUBCOMPONENT) ->
            ClassDaggerElement(it, Type.SUBCOMPONENT)
          it.hasAnnotation(DaggerAnnotations.MODULE) -> ClassDaggerElement(it, Type.MODULE)
          else -> null
        }
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktClassIdentifiers = listOf(identifyClassKotlin),
        psiClassIdentifiers = listOf(identifyClassJava),
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    return JavaPsiFacade.getInstance(project).findClass(classFqName, scope)?.let { listOf(it) }
      ?: emptyList()
  }

  override fun getMatchingIndexKeyPsiTypes(resolveCandidate: PsiElement): Set<PsiType> {
    // The resolve candidate is something like the `CoffeeShop` class, and the related type would be
    // `DripCoffeeModule`:
    //   @Component(modules = DripCoffeeModule.class)
    //   interface CoffeeShop {}
    // This method looks on the candidate for the appropriate annotation and annotation argument,
    // and then gets the PsiTypes corresponding to the classes listed in that argument.
    val annotationArgument =
      (resolveCandidate as? PsiClass)
        ?.getAnnotation(annotationsByDataType[dataType]!!)
        ?.findAttributeValue(annotationArgumentsByDataType[dataType]!!)
        ?: return emptySet()

    // In Java, the annotation's array argument may be specified without the array syntax if there's
    // only a single value. Look for both variations. (In Kotlin, the list form is always used.)
    return when (annotationArgument) {
      is PsiClassObjectAccessExpression -> setOf(annotationArgument.operand.type)
      is PsiArrayInitializerMemberValue ->
        annotationArgument.initializers
          .filterIsInstance<PsiClassObjectAccessExpression>()
          .map { it.operand.type }
          .toSet()
      else -> return emptySet()
    }
  }

  override val daggerElementIdentifiers = identifiers
}

internal class ClassDaggerElement(psiElement: PsiElement, daggerType: Type) :
  DaggerElement(psiElement, daggerType) {
  override fun getRelatedDaggerElements(): List<DaggerElement> =
    getRelatedDaggerElementsFromIndex(setOf(Type.COMPONENT, Type.MODULE, Type.SUBCOMPONENT))
}
