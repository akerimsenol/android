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
package com.android.tools.idea.insights

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.events.EnterOfflineMode
import com.android.tools.idea.insights.events.EnterOnlineMode

enum class ConnectionMode {
  ONLINE,
  OFFLINE;

  fun isOfflineMode() = StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get() && this == OFFLINE

  fun toEvent() = if (this == ONLINE) EnterOnlineMode else EnterOfflineMode
}
