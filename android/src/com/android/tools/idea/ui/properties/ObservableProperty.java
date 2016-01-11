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
package com.android.tools.idea.ui.properties;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A property represents a value which can both be set and queried, a concept which is
 * traditionally implemented in Java via {@code getXXX} and {@code setXXX} methods.
 * <p/>
 * Child classes should implement {@link #get()} and {@link #setDirectly(Object)} methods to
 * support modifying the actual value of this property.
 */
public abstract class ObservableProperty<T> extends AbstractObservableValue<T> implements SettableValue<T> {
  /**
   * Uses reflection to get all {@code ObservableProperty} instances in an Object instance.
   * This method is useful if you want to listen to all properties on a target object, for example calling an refresh method.
   * @return A {@link List<ObservableProperty>} containing all {@link ObservableProperty} found in the object.
   * If none are found an empty list is returned.
   */
  @NotNull
  public static List<ObservableProperty<?>> getAll(Object object) {
    ArrayList<ObservableProperty<?>> properties = new ArrayList<ObservableProperty<?>>();
    for (Field field : object.getClass().getDeclaredFields()) {
      if (ObservableProperty.class.isAssignableFrom(field.getType())) {
        try {
          // We change the access level of this field to avoid getting an IllegalAccessException
          boolean isFieldPrivate = !field.isAccessible();
          field.setAccessible(true);
          properties.add((ObservableProperty)field.get(object));
          if(isFieldPrivate){
            field.setAccessible(false);
          }
        }
        catch (IllegalAccessException ignored) {
        }
      }
    }
    return properties;
  }

  @Override
  public final void set(@NotNull T value) {
    if (!areValuesEqual(get(), value)) {
      setNotificationsEnabled(false);
      setDirectly(value);
      setNotificationsEnabled(true);
      notifyInvalidated();
    }
  }

  public final void set(@NotNull ObservableValue<T> value) {
    set(value.get());
  }

  @Override
  public String toString() {
    return get().toString();
  }

  /**
   * Implemented by child classes to handle setting the value of this property.
   */
  protected abstract void setDirectly(@NotNull T value);
  protected boolean areValuesEqual(@NotNull T value1, @NotNull T value2) {
    return Objects.equal(value1, value2);
  }
}
