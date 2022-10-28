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
package com.android.tools.idea.device.explorer.monitor.mocks

import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorModelListener
import com.android.tools.idea.device.explorer.monitor.processes.Device
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel

class MockModelListener : DeviceMonitorModelListener {
  val deviceAddedTracker: FutureValuesTracker<Device> = FutureValuesTracker<Device>()
  val deviceRemovedTracker: FutureValuesTracker<Device> = FutureValuesTracker<Device>()

  override fun allDevicesRemoved() {}

  override fun deviceAdded(device: Device) {
    deviceAddedTracker.produce(device)
  }

  override fun deviceRemoved(device: Device) {
    deviceRemovedTracker.produce(device)
  }

  override fun deviceUpdated(device: Device) {}

  override fun activeDeviceChanged(newActiveDevice: Device?) {}

  override fun treeModelChanged(newTreeModel: DefaultTreeModel?, newTreeSelectionModel: DefaultTreeSelectionModel?) {}
}