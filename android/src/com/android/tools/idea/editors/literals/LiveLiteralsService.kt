package com.android.tools.idea.editors.literals

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDeploymentReportService
import com.android.tools.idea.editors.literals.ui.LiveLiteralsAvailableIndicatorFactory
import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.classloading.ProjectConstantRemapper
import com.android.tools.idea.util.ListenerCollection
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.annotations.TestOnly
import java.awt.GraphicsEnvironment
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

internal val LITERAL_TEXT_ATTRIBUTE_KEY = TextAttributesKey.createTextAttributesKey("LiveLiteralsHighlightAttribute")

/**
 * Time used to coalesce multiple changes without triggering onLiteralsHaveChanged calls.
 */
private val DOCUMENT_CHANGE_COALESCE_TIME_MS = StudioFlags.COMPOSE_LIVE_LITERALS_UPDATE_RATE

/**
 * Interface implementing by services handling live literals.
 */
interface LiveLiteralsMonitorHandler {
  /**
   * Type of device being used.
   */
  enum class DeviceType {
    UNKNOWN,

    /** Not a real device. Studio Compose Preview. */
    PREVIEW,
    /** An emulator. */
    EMULATOR,
    /** A device connected to Studio. */
    PHYSICAL
  }

  /**
   * Describes a problem found during deployment.
   * @param severity Severity of the problem.
   * @param content Description of the problem.
   */
  data class Problem(val severity: Severity, val content: String) {
    enum class Severity {
      INFO,
      WARNING,
      ERROR
    }

    companion object {
      fun info(content: String) = Problem(Severity.INFO, content)
      fun warn(content: String) = Problem(Severity.WARNING, content)
      fun error(content: String) = Problem(Severity.ERROR, content)
    }
  }

  /**
   * Call this method when the deployment for [deviceId] has started. This will clear all current registered
   * [Problem]s for that device.
   */
  fun liveLiteralsMonitorStarted(deviceId: String, deviceType: DeviceType)

  /**
   * Call this method when the monitoring for [deviceId] has stopped. For example, if the application has stopped.
   */
  fun liveLiteralsMonitorStopped(deviceId: String)

  /**
   * Call this method when the deployment of live literals has started. The pushId allows to correlate the start with the end of a push.
   */
  fun liveLiteralPushStarted(deviceId: String, pushId: String)

  /**
   * Call this method when the deployment for [deviceId] has finished. [problems] includes a list
   * of the problems found while deploying literals. The pushId allows to correlate the start with the end of a push.
   */
  fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<Problem> = listOf())
}

/**
 * Project service to track live literals. The service, when [isAvailable] is true, will listen for changes of constants
 * and will notify listeners.
 *
 * @param project the project this service is attached to.
 * @param availableListener listener to be called when the service becomes available.
 * @param listenerExecutor executor to run the listener calls on.
 */
@Service
class LiveLiteralsService private constructor(private val project: Project,
                                              private val availableListener: LiteralsAvailableListener,
                                              listenerExecutor: Executor,
                                              private val deploymentReportService: LiveLiteralsDeploymentReportService) : LiveLiteralsMonitorHandler, Disposable {
  /**
   * Interface for listeners that want to be notified when Live Literals becomes available. For example
   * when the preview or the emulator find that the current project supports them.
   */
  interface LiteralsAvailableListener {
    /**
     * Called when Live Literals becomes available. This might be called multiple times.
     */
    fun onAvailable()
  }

  init {
    deploymentReportService.subscribe(this@LiveLiteralsService, object : LiveLiteralsDeploymentReportService.Listener {
      override fun onMonitorStarted(deviceId: String) {
        if (deploymentReportService.hasActiveDevices) {
          activateTracking()
        }
      }

      override fun onMonitorStopped(deviceId: String) {
        if (!deploymentReportService.hasActiveDevices) {
          deactivateTracking()
        }
      }

      override fun onLiveLiteralsPushed(deviceId: String) {}
    })
  }

  constructor(project: Project) : this(project, object : LiteralsAvailableListener {
    /**
     * If true, the first time literals are available for this project, a popup will show explaining the basics to the user.
     */
    private var showIsAvailablePopup = LiveLiteralsApplicationConfiguration.getInstance().showAvailablePopup

    override fun onAvailable() {
      if (showIsAvailablePopup) {
        // Once shown, do not show in this session for now.
        showIsAvailablePopup = false
        LiveLiteralsAvailableIndicatorFactory.showIsAvailablePopup(project)
      }
    }
  }, AppExecutorUtil.createBoundedApplicationPoolExecutor("Document changed listeners executor", 1),
                                       LiveLiteralsDeploymentReportService.getInstance(project))

  /**
   * Class that groups all the highlighters for a given file/editor combination. This allows enabling/disabling them.
   */
  @UiThread
  private inner class HighlightTracker(
    file: PsiFile,
    private val editor: Editor,
    private val fileSnapshot: LiteralReferenceSnapshot) : Disposable {
    private val project = file.project
    private var showingHighlights = false
    private val outHighlighters = mutableSetOf<RangeHighlighter>()

    @Suppress("IncorrectParentDisposable")
    private fun clearAll() {
      if (Disposer.isDisposed(project)) return
      val highlightManager = HighlightManager.getInstance(project)
      outHighlighters.forEach { highlightManager.removeSegmentHighlighter(editor, it) }
      outHighlighters.clear()
    }

    fun showHighlights() {
      if (showingHighlights) return
      showingHighlights = true

      // Take a snapshot
      if (log.isDebugEnabled) {
        fileSnapshot.all.forEach {
          val elementPathString = it.usages.joinToString("\n") { element -> element.toString() }
          log.debug("[${it.uniqueId}] Found constant ${it.text} \n$elementPathString\n\n")
        }
      }

      if (fileSnapshot.all.isNotEmpty()) {
        fileSnapshot.highlightSnapshotInEditor(project, editor, LITERAL_TEXT_ATTRIBUTE_KEY, outHighlighters)

        if (outHighlighters.isNotEmpty()) {
          // Remove the highlights if the manager is deactivated
          Disposer.register(this, this::hideHighlights)
        }
      }
    }

    fun hideHighlights() {
      clearAll()
      showingHighlights = false
    }

    override fun dispose() {
      hideHighlights()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LiveLiteralsService = project.getService(LiveLiteralsService::class.java)

    object NopListener : LiteralsAvailableListener {
      override fun onAvailable() {}
    }

    @TestOnly
    fun getInstanceForTest(project: Project,
                           parentDisposable: Disposable,
                           availableListener: LiteralsAvailableListener = NopListener,
                           listenerExecutor: Executor = MoreExecutors.directExecutor()): LiveLiteralsService =
      LiveLiteralsService(project, availableListener, listenerExecutor,
                          LiveLiteralsDeploymentReportService.getInstanceForTesting(project, listenerExecutor)).also {
        Disposer.register(parentDisposable, it)
      }
  }

  private val log = Logger.getInstance(LiveLiteralsService::class.java)

  /**
   * If true, the highlights will be shown. This must be only changed from the UI thread.
   */
  @set:UiThread
  @get:UiThread
  var showLiveLiteralsHighlights = false
    set(value) {
      field = value
      refreshHighlightTrackersVisibility()
    }

  /**
   * Link to all instantiated [HighlightTracker]s. This allows to switch them on/off via the [ToggleLiveLiteralsHighlightAction].
   * This is a [WeakList] since the trackers will be mainly held by the mouse listener created in [addDocumentTracking].
   */
  private val trackers = WeakList<HighlightTracker>()

  /**
   * [ListenerCollection] for all the listeners that need to be notified when any live literal has changed value.
   */
  private val onLiteralsChangedListeners = ListenerCollection.createWithExecutor<(List<LiteralReference>) -> Unit>(listenerExecutor)

  private val literalsManager = LiteralsManager()
  private val documentSnapshots = mutableMapOf<Document, LiteralReferenceSnapshot>()

  /**
   * [Disposable] that tracks the current activation. If the service is deactivated, this [Disposable] will be disposed.
   * It can be used to register anything that should be disposed when the service is not running.
   */
  private var activationDisposable: Disposable? = null

  private val updateMergingQueue = MergingUpdateQueue("Live literals change queue",
                                                      DOCUMENT_CHANGE_COALESCE_TIME_MS.get(),
                                                      true,
                                                      null,
                                                      this,
                                                      null,
                                                      false).setRestartTimerOnAdd(true)

  /**
   * True if Live Literals should be enabled for this project.
   */
  val isEnabled
    get() = LiveLiteralsApplicationConfiguration.getInstance().isEnabled

  /**
   * Controls when the live literals tracking is available for the current project. The feature might be enable but not available if the
   * current project has not any Live Literals yet.
   */
  val isAvailable: Boolean
    get() = deploymentReportService.hasActiveDevices

  @TestOnly
  fun allConstants(): Collection<LiteralReference> = documentSnapshots.flatMap { (_, snapshot) -> snapshot.all }

  /**
   * Method called to notify the listeners than a constant has changed.
   */
  private fun fireOnLiteralsChanged(changed: List<LiteralReference>) = onLiteralsChangedListeners.forEach {
    it(changed)
  }

  /**
   * Adds a new listener to be notified when the literals change.
   *
   * @param parentDisposable [Disposable] to control the lifespan of the listener. If the parentDisposable is disposed
   *  the listener will automatically be unregistered.
   * @param listener the code to be called when the literals change. This will run in a background thread.
   */
  fun addOnLiteralsChangedListener(parentDisposable: Disposable, listener: (List<LiteralReference>) -> Unit) {
    onLiteralsChangedListeners.add(listener = listener)
    val listenerWeakRef = WeakReference(listener)
    Disposer.register(parentDisposable) {
      onLiteralsChangedListeners.remove(listenerWeakRef.get() ?: return@register)
    }
  }

  @Synchronized
  private fun onDocumentsUpdated(document: Collection<Document>, @Suppress("UNUSED_PARAMETER") lastUpdateNanos: Long) {
    val updateList = ArrayList<LiteralReference>()
    document.flatMap {
      documentSnapshots[it]?.modified ?: emptyList()
    }.forEach {
      val constantValue = it.constantValue ?: return@forEach
      it.usages.forEach { elementPath ->
        val constantModified = ProjectConstantRemapper.getInstance(project).addConstant(
          null, elementPath, it.initialConstantValue, constantValue)
        log.debug("[${it.uniqueId}] Constant updated to ${it.text} path=${elementPath}")
        if (constantModified) {
          updateList.add(it)
        }
      }
    }

    if (!updateList.isEmpty()) {
      fireOnLiteralsChanged(updateList)
    }
  }

  /**
   * Adds a new document to the tracking. The document will be observed for changes.
   */
  private fun addDocumentTracking(parentDisposable: Disposable, editor: Editor, document: Document) {
    val file = AndroidPsiUtils.getPsiFileSafely(project, document) ?: return
    val fileSnapshot = literalsManager.findLiterals(file)

    if (fileSnapshot.all.isNotEmpty()) {
      availableListener.onAvailable()

      documentSnapshots[document] = fileSnapshot

      Disposer.register(parentDisposable) {
        documentSnapshots.remove(document)
      }
    }

    val tracker = HighlightTracker(file, editor, fileSnapshot)
    trackers.add(tracker)
    Disposer.register(parentDisposable, tracker)
    editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mouseEntered(event: EditorMouseEvent) {
        if (showLiveLiteralsHighlights) {
          tracker.showHighlights()
        }
      }

      override fun mouseExited(event: EditorMouseEvent) {
        tracker.hideHighlights()
      }
    }, parentDisposable)

    // If the mouse is already within the editor hover area, activate the highlights
    if (GraphicsEnvironment.isHeadless() || editor.component.mousePosition != null) {
      UIUtil.invokeLaterIfNeeded {
        if (showLiveLiteralsHighlights) {
          tracker.showHighlights()
        }
      }
    }
  }

  private fun refreshHighlightTrackersVisibility() = UIUtil.invokeLaterIfNeeded {
    trackers.forEach {
      if (showLiveLiteralsHighlights) {
        it.showHighlights()
      }
      else {
        it.hideHighlights()
      }
    }
  }

  @Synchronized
  private fun activateTracking() {
    if (Disposer.isDisposed(this)) return
    log.debug("activateTracking")

    val newActivationDisposable = Disposer.newDisposable()

    // Find all the active editors
    EditorFactory.getInstance().allEditors.forEach {
      if (it.project == project) addDocumentTracking(newActivationDisposable, it, it.document)
    }

    // Listen for all new editors opening
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        if (event.editor.project == project) addDocumentTracking(newActivationDisposable, event.editor, event.editor.document)
      }

      override fun editorReleased(event: EditorFactoryEvent) {
        documentSnapshots.remove(event.editor.document)
      }
    }, newActivationDisposable)


    setupChangeListener(project, ::onDocumentsUpdated, newActivationDisposable, updateMergingQueue)
    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        // The project has built successfully so we can drop the constants that we were keeping.
        ProjectConstantRemapper.getInstance(project).clearConstants(null)
      }

      override fun buildFailed() {
      }

      override fun buildStarted() {
        // Stop the literals listening while the build happens
        deactivateTracking()
      }
    }, newActivationDisposable)

    if (Disposer.tryRegister(this, newActivationDisposable)) {
      activationDisposable = newActivationDisposable
    }
    else {
      Disposer.dispose(newActivationDisposable)
    }

    LiveLiteralsAvailableIndicatorFactory.updateWidget(project)
  }

  @Synchronized
  private fun deactivateTracking() {
    log.debug("deactivateTracking")
    trackers.clear()
    activationDisposable?.let {
      Disposer.dispose(it)
    }
    activationDisposable = null
    LiveLiteralsAvailableIndicatorFactory.updateWidget(project)
  }

  override fun liveLiteralsMonitorStarted(deviceId: String, deviceType: LiveLiteralsMonitorHandler.DeviceType) =
    deploymentReportService.liveLiteralsMonitorStarted(deviceId, deviceType)

  override fun liveLiteralsMonitorStopped(deviceId: String) =
    deploymentReportService.liveLiteralsMonitorStopped(deviceId)

  override fun liveLiteralPushStarted(deviceId: String, pushId: String) =
    deploymentReportService.liveLiteralPushStarted(deviceId, pushId)

  override fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<LiveLiteralsMonitorHandler.Problem>) =
    deploymentReportService.liveLiteralPushed(deviceId, pushId, problems)

  override fun dispose() {
    deactivateTracking()
  }
}