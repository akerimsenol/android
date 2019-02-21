/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.stats.RunStats;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRunState implements RunProfileState {
  @NotNull private final ExecutionEnvironment myEnv;
  @NotNull private final String myLaunchConfigName;
  @NotNull private final Module myModule;
  @NotNull private final ApplicationIdProvider myApplicationIdProvider;
  @NotNull private final ConsoleProvider myConsoleProvider;
  @NotNull private final DeviceFutures myDeviceFutures;
  @NotNull private final LaunchTasksProvider myLaunchTasksProvider;
  @Nullable private final ProcessHandler myPreviousSessionProcessHandler;

  public AndroidRunState(@NotNull ExecutionEnvironment env,
                         @NotNull String launchConfigName,
                         @NotNull Module module,
                         @NotNull ApplicationIdProvider applicationIdProvider,
                         @NotNull ConsoleProvider consoleProvider,
                         @NotNull DeviceFutures deviceFutures,
                         @NotNull AndroidLaunchTasksProvider launchTasksProvider) {
    myEnv = env;
    myLaunchConfigName = launchConfigName;
    myModule = module;
    myApplicationIdProvider = applicationIdProvider;
    myConsoleProvider = consoleProvider;
    myDeviceFutures = deviceFutures;
    myLaunchTasksProvider = launchTasksProvider;

    AndroidSessionInfo existingSessionInfo = AndroidSessionInfo.findOldSession(
      env.getProject(), null, ((AndroidRunConfigurationBase)env.getRunProfile()).getUniqueID(), env.getExecutionTarget());
    myPreviousSessionProcessHandler =
      (existingSessionInfo != null && existingSessionInfo.getExecutorId().equals(env.getExecutor().getId()))
      ? existingSessionInfo.getProcessHandler()
      : null;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ProcessHandler processHandler;
    ConsoleView console;
    RunStats stats = RunStats.from(myEnv);

    String applicationId;
    try {
      applicationId = myApplicationIdProvider.getPackageName();
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id", e);
    }

    stats.setPackage(applicationId);

    if (myLaunchTasksProvider.createsNewProcess()) {
      // In the case of cold swap, there is an existing process that is connected, but we are going to launch a new one.
      // Detach the previous process handler so that we don't end up with 2 run tabs for the same launch (the existing one
      // and the new one).
      if (myPreviousSessionProcessHandler != null) {
        myPreviousSessionProcessHandler.detachProcess();
      }

      processHandler = new AndroidProcessHandler.Builder(myEnv.getProject())
        .setApplicationId(applicationId)
        .monitorRemoteProcesses(myLaunchTasksProvider.monitorRemoteProcess())
        .build();
      console = attachConsole(processHandler, executor);
    } else {
      assert myPreviousSessionProcessHandler != null : "No process handler from previous session, yet current tasks don't create one";
      processHandler = myPreviousSessionProcessHandler;
      console = null;
    }

    LaunchInfo launchInfo = new LaunchInfo(executor, runner, myEnv, myConsoleProvider);
    LaunchTaskRunner task = new LaunchTaskRunner(myModule.getProject(),
                                                 myLaunchConfigName,
                                                 myEnv.getExecutionTarget().getDisplayName(),
                                                 launchInfo,
                                                 processHandler,
                                                 myDeviceFutures,
                                                 myLaunchTasksProvider,
                                                 stats);
    ProgressManager.getInstance().run(task);
    return console == null ? null : new DefaultExecutionResult(console, processHandler);
  }

  @NotNull
  public ConsoleView attachConsole(@NotNull ProcessHandler processHandler, @NotNull Executor executor) throws ExecutionException {
    return myConsoleProvider.createAndAttach(myModule.getProject(), processHandler, executor);
  }
}
