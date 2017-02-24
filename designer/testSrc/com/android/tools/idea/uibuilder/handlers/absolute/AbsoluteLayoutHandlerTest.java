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
package com.android.tools.idea.uibuilder.handlers.absolute;

import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneTest;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.google.common.truth.Truth.assertThat;

public class AbsoluteLayoutHandlerTest extends SceneTest {

  public void testDragBottomRight() throws Exception {
    myInteraction.select("myText", true);
    myInteraction.mouseDown("myText", ResizeBaseTarget.Type.RIGHT_BOTTOM);
    myInteraction.mouseRelease(220, 230);
    myScreen.get("@id/myText")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/myText\"\n" +
                 "    android:layout_width=\"120dp\"\n" +
                 "    android:layout_height=\"130dp\"\n" +
                 "    android:layout_x=\"100dp\"\n" +
                 "    android:layout_y=\"100dp\"/>");
  }

  public void testResizeTopLeft() throws Exception {
    myInteraction.select("myText", true);
    myInteraction.mouseDown("myText", ResizeBaseTarget.Type.LEFT_TOP);
    myInteraction.mouseRelease(80, 70);
    myScreen.get("@id/myText")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/myText\"\n" +
                 "    android:layout_width=\"120dp\"\n" +
                 "    android:layout_height=\"130dp\"\n" +
                 "    android:layout_x=\"80dp\"\n" +
                 "    android:layout_y=\"70dp\"/>");
  }

  public void testResizeInsideOutFromTopLeft() throws Exception {
    myInteraction.select("myText", true);
    myInteraction.mouseDown("myText", ResizeBaseTarget.Type.LEFT_TOP);
    myInteraction.mouseRelease(400, 300);
    myScreen.get("@id/myText")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/myText\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    android:layout_x=\"200dp\"\n" +
                 "    android:layout_y=\"200dp\"/>");
  }

  public void testResizeInsideOutFromTop() throws Exception {
    myInteraction.select("myText", true);
    myInteraction.mouseDown("myText", ResizeBaseTarget.Type.TOP);
    myInteraction.mouseRelease(400, 350);
    myScreen.get("@id/myText")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/myText\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"150dp\"\n" +
                 "    android:layout_x=\"100dp\"\n" +
                 "    android:layout_y=\"200dp\"/>");
  }

  public void testResizeSnapToMatchParent() throws Exception {
    myInteraction.select("myButton", true);
    myInteraction.mouseDown("myButton", ResizeBaseTarget.Type.RIGHT);
    myInteraction.mouseRelease(995, 150);
    myScreen.get("@id/myButton")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/myButton\"\n" +
                 "    android:text=\"Button\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    android:layout_x=\"0dp\"\n" +
                 "    android:layout_y=\"300dp\"/>");
  }

  public void testResizeSnapToWrapContent() throws Exception {
    SceneComponent component = myScene.getSceneComponent("myButton");
    assertThat(component).isNotNull();
    Dimension wrapSize = myScene.measureWrapSize(component);
    assertThat(wrapSize).isNotNull();

    myInteraction.select("myButton", true);
    myInteraction.mouseDown("myButton", ResizeBaseTarget.Type.RIGHT_BOTTOM);
    myInteraction.mouseRelease(wrapSize.width + 4, 300 + wrapSize.height - 3);
    myScreen.get("@id/myButton")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/myButton\"\n" +
                 "    android:text=\"Button\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:layout_x=\"0dp\"\n" +
                 "    android:layout_y=\"300dp\"/>");
  }

  public void testResizeInsideOutSnapToWrapContent() throws Exception {
    SceneComponent component = myScene.getSceneComponent("myButton");
    assertThat(component).isNotNull();
    Dimension wrapSize = myScene.measureWrapSize(component);
    assertThat(wrapSize).isNotNull();

    myInteraction.select("myButton", true);
    myInteraction.mouseDown("myButton", ResizeBaseTarget.Type.LEFT_TOP);
    myInteraction.mouseRelease(100 + wrapSize.width + 3, 400 + wrapSize.height);
    myScreen.get("@id/myButton")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/myButton\"\n" +
                 "    android:text=\"Button\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:layout_x=\"100dp\"\n" +
                 "    android:layout_y=\"400dp\"/>");
  }

  public void testDrag() throws Exception {
    myInteraction.select("myText", true);
    myInteraction.mouseDown("myText");
    myInteraction.mouseRelease(60, 80);
    myScreen.get("@id/myText")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/myText\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    android:layout_x=\"10dp\"\n" +
                 "    android:layout_y=\"30dp\"/>");
  }

  public void testDragWithoutSelecting() throws Exception {
    myInteraction.mouseDown("myText");
    myInteraction.mouseRelease(175, 175);
    myScreen.get("@id/myText")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/myText\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    android:layout_x=\"125dp\"\n" +
                 "    android:layout_y=\"125dp\"/>");
  }

  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("absolute.xml",
                 component(ABSOLUTE_LAYOUT)
                   .withBounds(0, 0, 2000, 2000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(200, 200, 200, 200)
                       .id("@id/myText")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_x", "100dp")
                       .withAttribute("android:layout_y", "100dp"),
                     component(BUTTON)
                       .withBounds(0, 600, 200, 200)
                       .id("@id/myButton")
                       .text("Button")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_x", "0dp")
                       .withAttribute("android:layout_y", "300dp")
                   ));
  }
}