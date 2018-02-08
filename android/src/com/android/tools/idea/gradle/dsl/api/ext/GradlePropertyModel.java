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
package com.android.tools.idea.gradle.dsl.api.ext;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * This class represents a property or variable declared or referenced by the ExtraPropertiesExtension
 * of the projects Gradle build. It allows access to the properties name, values and dependencies.
 */
public interface GradlePropertyModel {
  @NotNull
  String DOUBLE_QUOTES = "\"";

  /**
   * Converts a string to one that can be used to set interpolated strings using {@link #setValue(Object)}
   * This type of string will perform string injections, e.g For Gradle file:
   *
   * ext {
   *   prop1 = 'Hello'
   * }
   *
   * property.setValue(iStr("$prop1"))
   * property.getValue(STRING_TYPE) // This will return the string "Hello".
   */
  @NotNull
  static String iStr(@NotNull String in) {
    return DOUBLE_QUOTES + in.replace("\"", "\\\"") + DOUBLE_QUOTES;
  }

  // The following are TypeReferences used in calls to getValue and getRawValue.
  TypeReference<String> STRING_TYPE = new TypeReference<String>() {};
  TypeReference<Integer> INTEGER_TYPE = new TypeReference<Integer>() {};
  TypeReference<Boolean> BOOLEAN_TYPE = new TypeReference<Boolean>() {};
  TypeReference<List<GradlePropertyModel>> LIST_TYPE = new TypeReference<List<GradlePropertyModel>>() {};
  TypeReference<Map<String, GradlePropertyModel>> MAP_TYPE = new TypeReference<Map<String, GradlePropertyModel>>() {};
  TypeReference<Object> OBJECT_TYPE = new TypeReference<Object>() {};

  /**
   * Represents the type of the value stored by this property, or when a type can't be found
   * {@code UNKNOWN}. These value types provide a guarantee about the type of value
   * that the property contains:
   * <ul>
   *   <li>{@code STRING} - Pass {@link #STRING_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code INTEGER} - Pass {@link #INTEGER_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code BOOLEAN} - Pass {@link #BOOLEAN_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code MAP} - Pass {@link #MAP_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code LIST} - Pass {@link #LIST_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code REFERENCE} - Pass {@link #STRING_TYPE} to {@link #getValue(TypeReference)} to get the name of the
   *                           property or variable refereed to. Use {@link #getDependencies()} to get the value.</li>
   *   <li>{@code NONE} - This property currently has no value, any call to {@link #getValue(TypeReference)} will return null.</>
   *   <li>{@code UNKNOWN} - No guarantees about the type of this element can be made}</li>
   * </ul>
   */
  enum ValueType {
    STRING,
    INTEGER,
    BOOLEAN,
    MAP,
    LIST,
    REFERENCE,
    NONE,
    UNKNOWN,
  }

  /**
   * @return the {@link ValueType} of the property. For references, this method returns the type of the referred to
   * property.
   */
  @NotNull
  ValueType getValueType();

  /**
   * @return the {@link PropertyType} of the property.
   */
  @NotNull
  PropertyType getPropertyType();

  /**
   * @return the value that is held be this element, if it can be assigned from the given {@code TypeReference}. Otherwise
   * this method returns null.
   */
  @Nullable
  <T> T getValue(@NotNull TypeReference<T> typeReference);

  /**
   * Gets the value of the unresolved property, this returns the value without attempting to resolve string injections
   * or references. For example in:
   * <pre>
   * <code>ext {
   *   prop1 = 'val'
   *   prop2 = prop1
   *   prop3 = "Hello ${prop1}"
   * }
   * </code>
   * </pre>
   * Getting the unresolved value of "prop2" will return "prop1" and for "prop3" it will return "Hello ${prop1}".
   * Otherwise if the property has no string injections or is not a reference this method will return the same value
   * as {@link #getValue(TypeReference)}.
   */
  @Nullable
  <T> T getRawValue(@NotNull TypeReference<T> typeReference);

  /**
   * Returns a list of all immediate dependencies for the property. This includes references and string injections within
   * values, lists and map values.
   */
  @NotNull
  List<GradlePropertyModel> getDependencies();

  /**
   * Returns the name of the property.
   */
  @NotNull
  String getName();

  /**
   * Returns the name of the property including any enclosing blocks, e.g "ext.deps.prop1".
   */
  @NotNull
  String getFullyQualifiedName();

  /**
   * Returns the Gradle file where this gradle property lives.
   */
  @NotNull
  VirtualFile getGradleFile();

  /**
   * Sets the value on this property to the given {@code value}.
   * Note: Does not work for Maps, Lists and References. TODO: Fix this
   */
  void setValue(@NotNull Object value);

  /**
   * Converts this property to an empty map. Any values stored inside the property will be removed.
   * This can be called on a property with any {@link ValueType} but will always result in {@link ValueType#MAP}.
   * This method returns itself for in order to chain operations e.g propertyModel.convertToEmptyMap().getMapValue()
   */
  GradlePropertyModel convertToEmptyMap();

  /**
   * Gets the model in the map represented by this property, that corresponds to the given key. If the key does not exist
   * the model representing an empty value is returned by this method, and can then be used to create new value.
   * Unless this resulting model has its value set, nothing will be created.
   * This should only be used for properties for which {@link #getValueType()} returns {@link ValueType#MAP}.
   */
  GradlePropertyModel getMapValue(@NotNull String key);

  /**
   * Converts this property to an empty list. Any values stored inside the property will be removed.
   * This can be called on a property with any {@link ValueType} but will always result in {@link ValueType#LIST}
   * This method returns itself for in order to chain operations e.g propertyModel.convertToEmptyList().addListValue()
   */
  GradlePropertyModel convertToEmptyList();

  /**
   * Create a new value at the end of this list property. The model representing the new value is returned by this method.
   * Unless the resulting value is set, nothing will be added.
   * Note: The returned {@link GradlePropertyModel} should not be set to a map or list. Setting these inside lists will throw an exception.
   * This should only be used for properties for which {@link #getValueType()} return {@link ValueType#LIST}
   */
  GradlePropertyModel addListValue();

  /**
   * Same as {@link #addListValue()} but instead of the value being added at the end, it will be added at the given index.
   * @throw {@link IndexOutOfBoundsException} if the index is outside the range of the list. Indexing starts from 0.
   */
  GradlePropertyModel addListValueAt(int index);

  /**
   * Marks this property for deletion, which when {@link GradleBuildModel#applyChanges()} is called, removes it and its value
   * from the file. Any call to {@link #setValue(Object)} will recreate the property and add it back to the file.
   */
  void delete();

  /**
   * @return a resolved model representing this property.
   */
  ResolvedPropertyModel resolve();

  /**
   * @return the {@link PsiElement} that this property originated from.
   */
  @Nullable
  PsiElement getPsiElement();

  /**
   * @return the value of the property as a String or null if no value exists or is of an incorrect type.
   */
  @Nullable
  String toString();

  /**
   * @return the value of the property as an Integer or null if no value exists or is of an incorrect type.
   */
  @Nullable
  Integer toInt();

  /**
   * @return the value of the property as a Boolean or null if no value exists or is of an incorrect type.
   */
  @Nullable
  Boolean toBoolean();

  /**
   * @return the value of the property as a List or null if no value exists or is of an incorrect type.
   */
  @Nullable
  List<GradlePropertyModel> toList();

  /**
   * @return the value of the property as a Map or null if no value exists or is of an incorrect type.
   */
  @Nullable
  Map<String, GradlePropertyModel> toMap();
}
