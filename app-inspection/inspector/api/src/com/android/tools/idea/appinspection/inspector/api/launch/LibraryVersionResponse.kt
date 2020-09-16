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
package com.android.tools.idea.appinspection.inspector.api.launch

/**
 * Response from a GetLibraryVersions call.
 *
 * It always contains a reference to the [TargetLibrary] and [status]. [errorMessage] is populated if the check resulted in
 * failure.
 */
data class LibraryVersionResponse(val targetLibrary: TargetLibrary, val status: Status, val errorMessage: String) {
  enum class Status {
    /**
     * The target library is compatible with the inspector.
     */
    COMPATIBLE,

    /**
     * The target library is incompatible or its version cannot be understood.
     */
    INCOMPATIBLE,

    /**
     * The target library is missing from the app.
     */
    LIBRARY_MISSING,

    /**
     * An error was encountered in finding and reading the version of the library.
     */
    ERROR
  }
}