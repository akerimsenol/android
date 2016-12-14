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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.util.NlTreeDumper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

import static com.android.SdkConstants.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public class HorizontalScrollViewHandlerTest extends LayoutTestCase {

  public void testScrollNothing() throws Exception {
    android.view.View mockScrollView = mock(android.view.View.class);

    surface().screen(createModel(mockScrollView))
      .get("@id/myText1")
      .scroll()
      .scroll(0)
      .release();

    verify(mockScrollView, never()).setScrollX(anyInt());
    verify(mockScrollView, never()).setScrollY(anyInt());
    verify(mockScrollView, never()).scrollBy(anyInt(), anyInt());
    verify(mockScrollView, never()).scrollTo(anyInt(), anyInt());
  }

  public void testCancel() throws Exception {
    AtomicInteger savedValue = new AtomicInteger(0);
    android.view.View mockScrollView = mock(android.view.View.class);
    doAnswer((invocation -> {
      savedValue.set((Integer)invocation.getArguments()[0]);
      return null;
    })).when(mockScrollView).setScrollX(anyInt());
    when(mockScrollView.getScrollX())
      .thenAnswer((invocation) -> savedValue.get());

    surface().screen(createModel(mockScrollView))
      .get("@id/myText1")
      .scroll()
      .scroll(50)
      .cancel();

    // Max scroll size is 30
    verify(mockScrollView, times(1)).setScrollX(30);
    verify(mockScrollView, times(1)).setScrollX(0);
  }

  public void testScroll() throws Exception {
    AtomicInteger savedValue = new AtomicInteger(0);
    android.view.View mockScrollView = mock(android.view.View.class);
    doAnswer((invocation -> {
      savedValue.set((Integer)invocation.getArguments()[0]);
      return null;
    })).when(mockScrollView).setScrollX(anyInt());
    when(mockScrollView.getScrollX())
      .thenAnswer((invocation) -> savedValue.get());

    surface().screen(createModel(mockScrollView))
      .get("@id/myText1")
      .scroll()
      .scroll(50)
      .release();

    // Max scroll size is 30
    verify(mockScrollView, times(1)).setScrollX(30);
    verify(mockScrollView, never()).setScrollX(0);
  }

  @NotNull
  private NlModel createModel(@Nullable android.view.View scrollView) {
    ModelBuilder builder = model("scroll.xml",
                     component(HORIZONTAL_SCROLL_VIEW)
                       .viewObject(scrollView)
                       .withBounds(0, 0, 90, 90)
                       .matchParentWidth()
                       .matchParentHeight()
                       .unboundedChildren(
                         component(LINEAR_LAYOUT)
                          .withBounds(0, 0, 120, 90)
                          .withAttribute("android:orientation", "horizontal")
                          .wrapContentHeight()
                          .wrapContentWidth()
                          .children(component(TEXT_VIEW)
                                     .id("@id/myText1")
                                     .withBounds(0, 0, 40, 40)
                                     .width("40dp")
                                     .height("40dp"),
                                              component(TEXT_VIEW)
                                     .id("@id/myText2")
                                     .withBounds(40, 0, 40, 40)
                                     .width("40dp")
                                     .height("40dp"),
                                              component(TEXT_VIEW)
                                     .id("@id/myText3")
                                     .withBounds(80, 0, 40, 40)
                                     .width("40dp")
                                     .height("40dp")

                     )));
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<HorizontalScrollView>, bounds=[0,0:90x90}\n" +
                 "    NlComponent{tag=<LinearLayout>, bounds=[0,0:120x90}\n" +
                 "        NlComponent{tag=<TextView>, bounds=[0,0:40x40}\n" +
                 "        NlComponent{tag=<TextView>, bounds=[40,0:40x40}\n" +
                 "        NlComponent{tag=<TextView>, bounds=[80,0:40x40}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    format(model.getFile());
    return model;
  }
}