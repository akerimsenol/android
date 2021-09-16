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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.getTypedArgument
import com.android.tools.idea.concurrency.AndroidExecutors
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val LOGCAT_FORMAT = "08-17 11:35:01.797   439  2734 I EmulatorActivity:[EMULATOR_PAIRING:%s]\n"

class NonInteractivePairingTest : LightPlatform4TestCase() {
  private lateinit var device: IDevice
  private var keepAliveSemaphore = Semaphore(1, 1)
  private var outputReceiver: IShellOutputReceiver? = null

  override fun setUp() {
    super.setUp()

    outputReceiver = null
    device = MockitoKt.mock()
    Mockito.doAnswer { invocation: InvocationOnMock ->
      outputReceiver = invocation.getTypedArgument(1)

      runBlocking {
        // We need to keep this job alive until we're done with the log reader.
        keepAliveSemaphore.acquire()
      }
      null
    }.`when`(device).executeShellCommand(ArgumentMatchers.anyString(),
                                         ArgumentMatchers.any(), ArgumentMatchers.anyLong(), ArgumentMatchers.any())
    val executors: AndroidExecutors = MockitoKt.mock()
    Mockito.`when`(executors.ioThreadExecutor).thenReturn(Executors.newSingleThreadExecutor())
    project.registerServiceInstance(AndroidExecutors::class.java, executors)
  }

  @Test
  fun afterClose_receiverIsCancelled() {
    NonInteractivePairing.startPairing(device, "", "", "").use {
      com.android.tools.idea.concurrency.waitForCondition(10, TimeUnit.SECONDS) { outputReceiver != null }
      assertThat(it.pairingState.value).isEqualTo(NonInteractivePairing.PairingState.UNKNOWN)

      sendLogCat(LOGCAT_FORMAT.format(NonInteractivePairing.PairingState.STARTED))
      assertThat(it.pairingState.value).isEqualTo(NonInteractivePairing.PairingState.STARTED)
    }
    keepAliveSemaphore.release()
    assertThat(outputReceiver?.isCancelled).isTrue()
  }

  @Test
  fun onMultipleLogEntries_stateRepresentsTheMostRecentOne() {
    NonInteractivePairing.startPairing(device, "", "", "").use {
      com.android.tools.idea.concurrency.waitForCondition(10, TimeUnit.SECONDS) { outputReceiver != null }
      assertThat(it.pairingState.value).isEqualTo(NonInteractivePairing.PairingState.UNKNOWN)

      sendLogCat(LOGCAT_FORMAT.format(NonInteractivePairing.PairingState.STARTED))
      sendLogCat(LOGCAT_FORMAT.format(NonInteractivePairing.PairingState.CONSENT))
      sendLogCat(LOGCAT_FORMAT.format(NonInteractivePairing.PairingState.PAIRING))
      sendLogCat(LOGCAT_FORMAT.format(NonInteractivePairing.PairingState.SUCCESS))
      assertThat(it.pairingState.value).isEqualTo(NonInteractivePairing.PairingState.SUCCESS)
    }
    keepAliveSemaphore.release()
    assertThat(outputReceiver?.isCancelled).isTrue()
  }

  @Test
  fun onUnknownStatusLog_stateIsSetToUnknown() {
    NonInteractivePairing.startPairing(device, "", "", "").use {
      com.android.tools.idea.concurrency.waitForCondition(10, TimeUnit.SECONDS) { outputReceiver != null }
      assertThat(it.pairingState.value).isEqualTo(NonInteractivePairing.PairingState.UNKNOWN)

      sendLogCat(LOGCAT_FORMAT.format(NonInteractivePairing.PairingState.STARTED))
      sendLogCat(LOGCAT_FORMAT.format("AN_UNKNOWN_STATE"))
      assertThat(it.pairingState.value).isEqualTo(NonInteractivePairing.PairingState.UNKNOWN)

      sendLogCat(LOGCAT_FORMAT.format(NonInteractivePairing.PairingState.SUCCESS))
      assertThat(it.pairingState.value).isEqualTo(NonInteractivePairing.PairingState.SUCCESS)
    }
    keepAliveSemaphore.release()
    assertThat(outputReceiver?.isCancelled).isTrue()
  }

  private fun sendLogCat(log: String) {
    log.toByteArray().let {
      outputReceiver?.addOutput(it, 0, it.size)
    }
  }
}