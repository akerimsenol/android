/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeSceneUpdateListener
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RealTimeSessionClock
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.EditorNotifications
import com.intellij.ui.FilledRoundedBorder
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.AdjustmentEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout

private const val SPLITTER_DIVIDER_WIDTH_PX = 3

/**
 * Interface that isolates the view of the Compose view so it can be replaced for testing.
 */
interface ComposePreviewView {
  val pinnedSurface: NlDesignSurface
  val mainSurface: NlDesignSurface

  /**
   * Returns the [JComponent] containing this [ComposePreviewView] that can be used to embed it other panels.
   */
  val component: JComponent

  /**
   * Allows replacing the bottom panel in the [ComposePreviewView]. Used to display the animations component.
   */
  var bottomPanel: JComponent?

  /**
   * Sets whether the scene overlay is visible. If true, the outline for the components will be visible and selectable.
   */
  var hasComponentsOverlay: Boolean

  /**
   * Sets whether the panel is in "interactive" mode. If it's in interactive mode, the clicks will be forwarded to the
   * code being previewed using the [LayoutlibInteractionHandler].
   */
  var isInteractive: Boolean

  /**
   * Method called to force an update on the notifications for the given [FileEditor].
   */
  fun updateNotifications(parentEditor: FileEditor)

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  fun updateVisibilityAndNotifications()

  /**
   * Hides the content if visible and displays the given [message].
   */
  fun showModalErrorMessage(message: String)

  /**
   * If the content is not already visible, shows a "Building" message.
   */
  fun showBuildingMessage()

  /**
   * If called the pinned previews will be shown/hidden at the top.
   */
  fun setPinnedSurfaceVisibility(visible: Boolean)

  /**
   * Sets an optional label on top of the pinned surface. If empty, the label will not be displayed.
   */
  var pinnedLabel: String
}

fun interface ComposePreviewViewProvider {
  fun invoke(project: Project,
             psiFilePointer: SmartPsiElementPointer<PsiFile>,
             projectBuildStatusManager: ProjectBuildStatusManager,
             navigationHandler: PreviewNavigationHandler,
             dataProvider: DataProvider,
             parentDisposable: Disposable): ComposePreviewView
}

/**
 * Creates a [JPanel] using an [OverlayLayout] containing all the given [JComponent]s.
 */
private fun createOverlayPanel(vararg components: JComponent): JPanel =
  object : JPanel() {
    // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
    override fun isOptimizedDrawingEnabled(): Boolean = false
  }.apply<JPanel> {
    layout = OverlayLayout(this)
    components.forEach {
      add(it)
    }
  }

private class PinnedLabelPanel: JPanel(BorderLayout()) {
  private val pinnedLabelBackground = UIUtil.toAlpha(UIUtil.getPanelBackground().darker(), 0x20)
  private val pinnedPanelLabel = JLabel("").apply {
    font = UIUtil.getLabelFont()
  }

  var text: String
    get() = pinnedPanelLabel.text
    set(value) { pinnedPanelLabel.text = value }

  init {
    border = JBUI.Borders.empty(25, 20, 20, 20)
    isOpaque = false

    add(JPanel(VerticalFlowLayout()).apply {
      isOpaque = false
      add(JPanel().apply {
        border = BorderFactory.createCompoundBorder(FilledRoundedBorder(pinnedLabelBackground, 10, 4),
                                                    JBUI.Borders.empty(0, 20))
        add(pinnedPanelLabel)
      })
    }, BorderLayout.LINE_START)
  }
}

/**
 * [WorkBench] panel used to contain all the compose preview elements.
 *
 * @param project the current open project
 * @param psiFilePointer an [SmartPsiElementPointer] pointing to the file being rendered within this panel. Used to handle
 *   which notifications should be displayed.
 * @param projectBuildStatusManager [ProjectBuildStatusManager] used to detect the current build status and show/hide the correct loading
 *   message.
 * @param navigationHandler the [PreviewNavigationHandler] used to handle the source code navigation when user clicks a component.
 * @param dataProvider the [DataProvider] to be used by this panel.
 * @param parentDisposable the [Disposable] to use as parent disposable for this panel.
 */
internal class ComposePreviewViewImpl(private val project: Project,
                                      private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
                                      private val projectBuildStatusManager: ProjectBuildStatusManager,
                                      navigationHandler: PreviewNavigationHandler,
                                      dataProvider: DataProvider,
                                      parentDisposable: Disposable) :
  WorkBench<DesignSurface>(project, "Compose Preview", null, parentDisposable), ComposePreviewView {
  private val log = Logger.getInstance(ComposePreviewViewImpl::class.java)

  private val sceneComponentProvider = ComposeSceneComponentProvider()
  private val delegateInteractionHandler = DelegateInteractionHandler()

  override val pinnedSurface by lazy {
    createPreviewDesignSurface(
      project, navigationHandler, delegateInteractionHandler, dataProvider, parentDisposable, DesignSurface.ZoomControlsPolicy.HIDDEN,
      GridSurfaceLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y, NlConstants.SCREEN_DELTA,
                               NlConstants.SCREEN_DELTA)) { surface, model ->
      LayoutlibSceneManager(model, surface, sceneComponentProvider, ComposeSceneUpdateListener(), { RealTimeSessionClock() })
    }
  }

  override val mainSurface = createPreviewDesignSurface(
    project, navigationHandler, delegateInteractionHandler, dataProvider, parentDisposable,
    DesignSurface.ZoomControlsPolicy.VISIBLE) { surface, model ->
    LayoutlibSceneManager(model, surface, sceneComponentProvider, ComposeSceneUpdateListener(), { RealTimeSessionClock() })
  }

  override val component: JComponent = this

  private val pinnedPanelLabel by lazy {
    PinnedLabelPanel()
  }
  private val pinnedPanel by lazy {
    createOverlayPanel(pinnedPanelLabel, pinnedSurface)
  }

  private val staticPreviewInteractionHandler = NlInteractionHandler(mainSurface)
  private val interactiveInteractionHandler by lazy { LayoutlibInteractionHandler(mainSurface) }

  /**
   * Vertical splitter where the top part is a surface containing the pinned elements and the bottom the main design surface.
   */
  private val surfaceSplitter = JBSplitter(true, 0.25f, 0f, 0.5f).apply {
    dividerWidth = SPLITTER_DIVIDER_WIDTH_PX
  }

  /**
   * Vertical splitter where the top component is the [surfaceSplitter] and the bottom component, when visible, is an auxiliary
   * panel associated with the preview. For example, it can be an animation inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter = JBSplitter(true, 0.7f).apply {
    dividerWidth = SPLITTER_DIVIDER_WIDTH_PX
  }

  private val notificationPanel = NotificationPanel(
    ExtensionPointName.create("com.android.tools.idea.compose.preview.composeEditorNotificationProvider"))

  override var pinnedLabel: String
    get() = pinnedPanelLabel.text
    set(value) {
      pinnedPanelLabel.text = value
      pinnedPanelLabel.isVisible = value.isNotBlank()
    }

  init {
    // Start handling events for the static preview.
    delegateInteractionHandler.delegate = staticPreviewInteractionHandler

    mainSurface.addPanZoomListener(object: PanZoomListener {
      override fun zoomChanged(previousScale: Double, newScale: Double) {
        // Always keep the ratio from the fit scale from the pinned and main surfaces. This allows to zoom in/out in a
        // synchronized way without them ever going out of sync because of hitting the max/min limit.
        val zoomToFitDelta = pinnedSurface.getFitScale(false) - mainSurface.getFitScale(false)

        pinnedSurface.setScale(newScale + zoomToFitDelta)
      }

      override fun panningChanged(adjustmentEvent: AdjustmentEvent?) {}
    })

    val contentPanel = JPanel(BorderLayout()).apply {
      val actionsToolbar = ActionsToolbar(parentDisposable, mainSurface)
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

      // surfaceSplitter.firstComponent will contain the pinned surface elements
      surfaceSplitter.secondComponent = mainSurface

      mainPanelSplitter.firstComponent = createOverlayPanel(notificationPanel, surfaceSplitter)
      add(mainPanelSplitter, BorderLayout.CENTER)
    }

    val issueErrorSplitter = IssuePanelSplitter(mainSurface, contentPanel)

    init(issueErrorSplitter, mainSurface, listOf(), false)
    showLoading(message("panel.building"))
  }

  override fun showBuildingMessage() = UIUtil.invokeLaterIfNeeded {
    if (isMessageVisible) {
      showLoading(message("panel.building"))
      hideContent()
    }
  }

  override fun setPinnedSurfaceVisibility(visible: Boolean) = UIUtil.invokeLaterIfNeeded {
    if (StudioFlags.COMPOSE_PIN_PREVIEW.get() && visible) {
      surfaceSplitter.firstComponent = pinnedPanel
    }
    else {
      surfaceSplitter.firstComponent = null
    }
  }

  override fun showModalErrorMessage(message: String) = UIUtil.invokeLaterIfNeeded {
    log.debug("showModelErrorMessage: $message")
    loadingStopped(message)
  }

  override fun updateNotifications(parentEditor: FileEditor) = UIUtil.invokeLaterIfNeeded {
    if (Disposer.isDisposed(this) || project.isDisposed || !parentEditor.isValid) return@invokeLaterIfNeeded

    notificationPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
  }

  /**
   * Method called to ask all notifications to update.
   */
  private fun updateNotifications() = UIUtil.invokeLaterIfNeeded {
    // Make sure all notifications are cleared-up
    if (!project.isDisposed) {
      EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
    }
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  override fun updateVisibilityAndNotifications() = UIUtil.invokeLaterIfNeeded {
    if (isMessageVisible && projectBuildStatusManager.status == NeedsBuild) {
      log.debug("Needs successful build")
      showModalErrorMessage(message("panel.needs.build"))
    }
    else {
      log.debug("Show content")
      hideLoading()
      showContent()
    }

    updateNotifications()
  }

  override var hasComponentsOverlay: Boolean
    get() = sceneComponentProvider.enabled
    set(value) {
      sceneComponentProvider.enabled = value
    }

  override var isInteractive: Boolean = false
    set(value) {
      field = value
      if (value) {
        delegateInteractionHandler.delegate = interactiveInteractionHandler
      }
      else {
        delegateInteractionHandler.delegate = staticPreviewInteractionHandler
      }
    }

  override var bottomPanel: JComponent?
    set(value) {
      mainPanelSplitter.secondComponent = value
    }
    get() = mainPanelSplitter.secondComponent
}