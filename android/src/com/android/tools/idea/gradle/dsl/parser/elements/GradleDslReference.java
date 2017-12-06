/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.parser.GradleStringInjection;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Represents a reference expression.
 */
public final class GradleDslReference extends GradleDslExpression {
  public GradleDslReference(@NotNull GradleDslElement parent,
                            @NotNull PsiElement psiElement,
                            @NotNull String name,
                            @NotNull PsiElement reference) {
    super(parent, psiElement, name, reference);
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Nullable
  public String getReferenceText() {
    return myExpression != null ? myExpression.getText() : null;
  }

  @Override
  @Nullable
  public Object getValue() {
    GradleDslLiteral valueLiteral = getValue(GradleDslLiteral.class);
    return valueLiteral != null ? valueLiteral.getValue() : getValue(String.class);
  }

  /**
   * Returns the same as getReferenceText if you need to get the unresolved version of what this
   * reference refers to then use getResolvedVariables and call getUnresolvedValue on the result.
   */
  @Override
  @Nullable
  public Object getUnresolvedValue() {
    return getReferenceText();
  }

  @Override
  @NotNull
  public Collection<GradleStringInjection> getResolvedVariables() {
    String text = getReferenceText();
    if (text == null || myExpression == null) {
      return Collections.emptyList();
    }

    // Resolve our reference
    GradleDslElement element = resolveReference(text);
    if (element == null || !(element instanceof GradleDslExpression)) {
      return Collections.emptyList();
    }

    return ImmutableList.of(new GradleStringInjection((GradleDslExpression)element, myExpression, text));
  }

  /**
   * Returns the value of type {@code clazz} when the reference expression is referring to an element with the value
   * of that type, or {@code null} otherwise.
   */
  @Override
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    String referenceText = getReferenceText();
    if (referenceText == null) {
      return null;
    }
    return resolveReference(referenceText, clazz);
  }

  @Override
  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    Object value = getUnresolvedValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  public void setValue(@NotNull Object value) {
    // TODO: Add support to set a reference value.
  }

  @Override
  protected void apply() {
    // TODO: Add support to update a reference element.
  }

  @Override
  protected void reset() {
    // TODO: Add support to update a reference element.
  }

  @Override
  @Nullable
  public PsiElement create() {
    // TODO: Add support to create a new reference element.
    return getPsiElement();
  }

  @Override
  protected void delete() {
    // TODO: Add support to delete a reference element.
  }
}
