/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A dumb way of laying out screens in the navigation editor.
 *
 * TODO: implement a better way
 */
public class DummyAlgorithm implements NavSceneLayoutAlgorithm {
  @AndroidCoordinate private static final int WIDTH = 800;
  @AndroidCoordinate private static final int INITIAL_OFFSET = 40;
  @AndroidCoordinate private static final int INTERVAL = 120;

  private final NavigationSchema mySchema;

  public DummyAlgorithm(NavigationSchema schema) {
    mySchema = schema;
  }

  @Override
  public void layout(@NotNull SceneComponent component) {
    NavigationSchema.DestinationType type = mySchema.getDestinationType(component.getNlComponent().getTagName());
    if (type == NavigationSchema.DestinationType.NAVIGATION && component.getParent() == null) {
      return;
    }
    SceneComponent root = component.getScene().getRoot();

    Map<SceneComponent, Rectangle> bounds = root == null
                                            ? new HashMap<>()
                                            : root.flatten()
                                              .filter(c -> c.getParent() != null)
                                              .collect(Collectors.toMap(c -> c, c -> c.fillDrawRect(0, null)));

    NlModel model = component.getNlComponent().getModel();

    @AndroidDpCoordinate int width = Coordinates.pxToDp(model, WIDTH);
    @AndroidDpCoordinate int initialOffset = Coordinates.pxToDp(model, INITIAL_OFFSET);
    @AndroidDpCoordinate int interval = Coordinates.pxToDp(model, INTERVAL);

    @AndroidDpCoordinate int xOffset = initialOffset;
    @AndroidDpCoordinate int yOffset = initialOffset;

    while (true) {
      component.setPosition(xOffset, yOffset);
      Rectangle newBounds = component.fillDrawRect(0, null);
      bounds.put(component, newBounds);
      xOffset += interval;
      if (xOffset + component.getDrawWidth() > width) {
        yOffset += interval;
        xOffset = initialOffset;
      }
      if (checkOverlaps(bounds, component)) {
        break;
      }
    }
  }

  private static boolean checkOverlaps(@NotNull Map<SceneComponent, Rectangle> bounds, @NotNull SceneComponent component) {
    Rectangle componentBounds = component.fillDrawRect(0, null);
    for (Map.Entry<SceneComponent, Rectangle> existing : bounds.entrySet()) {
      if (componentBounds.intersects(existing.getValue()) && existing.getKey() != component) {
        return false;
      }
    }
    return true;
  }
}
