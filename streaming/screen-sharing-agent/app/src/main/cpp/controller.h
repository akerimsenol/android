/*
 * Copyright (C) 2021 The Android Open Source Project
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

#pragma once

#include <atomic>

#include "accessors/clipboard_manager.h"
#include "accessors/key_character_map.h"
#include "accessors/pointer_helper.h"
#include "base128_input_stream.h"
#include "common.h"
#include "control_messages.h"
#include "geom.h"
#include "jvm.h"

namespace screensharing {

// Processes control socket commands.
class Controller {
public:
  // The controller takes ownership of the socket file descriptor and closes it when destroyed.
  Controller(int socket_fd);
  ~Controller();

  void Run();
  void Shutdown();

private:
  struct ClipboardListener : public ClipboardManager::ClipboardListener {
    ClipboardListener(Controller* controller)
        : controller_(controller) {
    }
    virtual ~ClipboardListener();

    virtual void OnPrimaryClipChanged() override;

    Controller* controller_;
  };

  void Initialize();
  void ProcessMessage(const ControlMessage& message);
  void ProcessMotionEvent(const MotionEventMessage& message);
  void ProcessKeyboardEvent(const KeyEventMessage& message) {
    ProcessKeyboardEvent(jni_, message);
  }
  static void ProcessKeyboardEvent(Jni jni, const KeyEventMessage& message);
  void ProcessTextInput(const TextInputMessage& message);
  static void ProcessSetDeviceOrientation(const SetDeviceOrientationMessage& message);
  static void ProcessSetMaxVideoResolution(const SetMaxVideoResolutionMessage& message);
  static void StopVideoStream();
  void StartVideoStream();
  void StartClipboardSync(const StartClipboardSyncMessage& message);
  void StopClipboardSync();
  void OnPrimaryClipChanged();
  void ProcessClipboardChange();
  static void WakeUpDevice();

  Jni jni_ = nullptr;
  int socket_fd_;  // Owned.
  Base128InputStream input_stream_;
  Base128OutputStream output_stream_;
  PointerHelper* pointer_helper_;  // Owned.
  JObjectArray pointer_properties_;  // MotionEvent.PointerProperties[]
  JObjectArray pointer_coordinates_;  // MotionEvent.PointerCoords[]
  int64_t motion_event_start_time_;
  KeyCharacterMap* key_character_map_;  // Owned.

  ClipboardListener clipboard_listener_;
  int max_synced_clipboard_length_;
  std::string last_clipboard_text_;
  std::atomic_bool clipboard_changed_;

  DISALLOW_COPY_AND_ASSIGN(Controller);
};

}  // namespace screensharing
