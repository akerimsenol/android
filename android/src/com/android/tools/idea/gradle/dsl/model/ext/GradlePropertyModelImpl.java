// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;

public class GradlePropertyModelImpl implements GradlePropertyModel {
  @NotNull private ValueType myValueType;
  @NotNull private PropertyType myType;
  @NotNull private GradleDslElement myElement;

  public GradlePropertyModelImpl(@NotNull GradleDslElement element, @NotNull PropertyType type) {
    myElement = element;
    myType = type;

    myValueType = extractAndGetValueType(myElement);
  }

  @Override
  @NotNull
  public ValueType getValueType() {
    return myValueType;
  }

  @Override
  @NotNull
  public PropertyType getPropertyType() {
    return myType;
  }

  @Override
  @Nullable
  public <T> T getValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, true);
  }

  @Override
  public <T> T getUnresolvedValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, false);
  }

  @NotNull
  private Map<String, GradlePropertyModel> getMap() {
    if (myValueType != MAP || !(myElement instanceof GradleDslExpressionMap)) {
      return ImmutableMap.of();
    }

    GradleDslExpressionMap map = (GradleDslExpressionMap)myElement;
    return map.getPropertyElements().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> new GradlePropertyModelImpl(e.getValue(), PropertyType.DERIVED)));
  }

  @NotNull
  private List<GradlePropertyModel> getList() {
    if (myValueType != LIST || !(myElement instanceof GradleDslExpressionList)) {
      return ImmutableList.of();
    }

    GradleDslExpressionList list = (GradleDslExpressionList)myElement;
    return list.getExpressions().stream().map(e -> new GradlePropertyModelImpl(e, PropertyType.DERIVED)).collect(Collectors.toList());
  }

  @Override
  @NotNull
  public String getName() {
    return myElement.getName();
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getDependencies() {
    return myElement.getResolvedVariables().stream()
      .map(injection -> new GradlePropertyModelImpl(injection.getToBeInjected(), PropertyType.DERIVED)).collect(
        Collectors.toList());
  }

  @Override
  @NotNull
  public String getFullyQualifiedName() {
    return myElement.getQualifiedName();
  }

  @Override
  @NotNull
  public VirtualFile getGradleFile() {
    return myElement.getDslFile().getFile();
  }

  @Override
  public void setValue(@NotNull Object value) {
    if (myValueType == MAP || myValueType == LIST) {
      throw new UnsupportedOperationException("Setting map and list values are not supported!");
    }

    GradleDslExpression expression = (GradleDslExpression)myElement;
    expression.setValue(value);

    // Update the current value type
    myValueType = extractAndGetValueType(myElement);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof GradlePropertyModelImpl)) {
      return false;
    }
    GradlePropertyModelImpl other = (GradlePropertyModelImpl)o;
    return Objects.equals(myElement, other.myElement) &&
           Objects.equals(myValueType, other.myValueType) &&
           Objects.equals(myType, other.myType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myElement, myValueType, myType);
  }

  @Override
  public String toString() {
    return String.format("[Element: %1$s, Type: %2$s, ValueType: %3$s]@%4$s",
                         myElement.toString(), myType.toString(), myValueType.toString(), Integer.toHexString(hashCode()));
  }

  private static ValueType extractAndGetValueType(@NotNull GradleDslElement element) {
    if (element instanceof GradleDslExpressionMap) {
      return MAP;
    }
    else if (element instanceof GradleDslExpressionList) {
      return LIST;
    }
    else if (element instanceof GradleDslExpression) {
      GradleDslExpression expression = (GradleDslExpression)element;
      Object value = expression.getValue();
      if (value instanceof Boolean) {
        return BOOLEAN;
      }
      else if (value instanceof Integer) {
        return INTEGER;
      }
      else if (value instanceof String) {
        return STRING;
      }
      else {
        return UNKNOWN;
      }
    }
    else {
      // We should not be trying to create properties based of other elements.
      throw new IllegalArgumentException("Can't create property model from given GradleDslElement: " + element);
    }
  }

  @Nullable
  private <T> T extractValue(@NotNull TypeReference<T> typeReference, boolean resolved) {
    if (myValueType == MAP) {
      Object value = getMap();
      return typeReference.castTo(value);
    } else if (myValueType == LIST) {
      Object value = getList();
      return typeReference.castTo(value);
    }

    GradleDslExpression expression = (GradleDslExpression)myElement;

    Object value = resolved ? expression.getValue() : expression.getUnresolvedValue();
    if (value == null) {
      return null;
    }

    return typeReference.castTo(value);
  }
}
