/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class FontInspectorProvider implements InspectorProvider {
  @Override
  public boolean isApplicable(@NonNull NlComponent component, @NonNull Map<String, NlProperty> properties) {
    return properties.keySet().containsAll(FontInspectorComponent.TEXT_PROPERTIES);
  }

  @NonNull
  @Override
  public InspectorComponent createCustomInspector(@Nullable NlComponent component,
                                                  @NonNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    return new FontInspectorComponent(properties, propertiesManager);
  }
}
