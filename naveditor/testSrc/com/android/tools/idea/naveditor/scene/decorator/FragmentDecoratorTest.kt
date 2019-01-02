/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.idea.common.scene.HitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.draw.DrawFragment
import com.android.tools.idea.naveditor.scene.draw.assertDrawCommandsEqual
import com.intellij.ui.JBColor
import org.mockito.Mockito
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Rectangle2D

private val POSITION = Point(50, 150)
private val SIZE = Dimension(100, 200)
private val RECT = Rectangle2D.Float(419f, 469f, 50f, 100f)
private val COLOR = JBColor(0x1886f7, 0x9ccdff)

class FragmentDecoratorTest : NavTestCase() {
  fun testContent() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("f1")
      }
    }

    val sceneComponent = SceneComponent(model.surface.scene!!, model.find("f1")!!, Mockito.mock(HitProvider::class.java))
    sceneComponent.setPosition(POSITION.x, POSITION.y)
    sceneComponent.setSize(SIZE.width, SIZE.height)

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()
    val context = SceneContext.get(sceneView)

    FragmentDecorator.buildListComponent(displayList, 0, context, sceneComponent)
    assertEquals(1, displayList.commands.size)
    assertDrawCommandsEqual(DrawFragment(RECT, context.scale.toFloat(), null), displayList.commands[0] as DrawFragment)
  }

  fun testHighlightedContent() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("f1")
      }
    }

    val sceneComponent = SceneComponent(model.surface.scene!!, model.find("f1")!!, Mockito.mock(HitProvider::class.java))
    sceneComponent.setPosition(POSITION.x, POSITION.y)
    sceneComponent.setSize(SIZE.width, SIZE.height)
    sceneComponent.drawState = SceneComponent.DrawState.SELECTED

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()
    val context = SceneContext.get(sceneView)

    FragmentDecorator.buildListComponent(displayList, 0, context, sceneComponent)
    assertEquals(1, displayList.commands.size)
    assertDrawCommandsEqual(DrawFragment(RECT, context.scale.toFloat(), COLOR), displayList.commands[0] as DrawFragment)
  }
}