/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Settings for mirroring of physical Android devices.
 */
@State(name = "DeviceMirroringSettings", storages = [(Storage("device.mirroring.xml"))])
class DeviceMirroringSettings : PersistentStateComponent<DeviceMirroringSettings> {

  private var initialized = false

  var deviceMirroringEnabled: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }


  var activateOnConnection: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  var activateOnAppLaunch: Boolean = true
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  var activateOnTestLaunch: Boolean = true
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  var synchronizeClipboard: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  /** Max length of clipboard text to participate in clipboard synchronization. */
  var maxSyncedClipboardLength: Int = MAX_SYNCED_CLIPBOARD_LENGTH_DEFAULT
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  var turnOffDisplayWhileMirroring: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  /**
   * This property indicates whether the MirroringConfirmationDialog was shown at least once.
   * It is not reflected in DeviceMirroringSettingsUi.
   */
  var confirmationDialogShown: Boolean = false

  override fun getState(): DeviceMirroringSettings = this

  override fun loadState(state: DeviceMirroringSettings) {
    XmlSerializerUtil.copyBean(state, this)

    // Explicitly disable Device Mirroring feature for the IDEs that already have it enabled
    this.deviceMirroringEnabled = false
  }

  override fun initializeComponent() {
    initialized = true
  }

  private fun notifyListeners() {
    // Notify listeners if this is the main DeviceMirroringSettings instance, and it has been already initialized.
    if (initialized && this == getInstance()) {
      ApplicationManager.getApplication().messageBus.syncPublisher(DeviceMirroringSettingsListener.TOPIC).settingsChanged(this)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): DeviceMirroringSettings {
      return ApplicationManager.getApplication().getService(DeviceMirroringSettings::class.java)
    }

    const val MAX_SYNCED_CLIPBOARD_LENGTH_DEFAULT = 5000
  }
}