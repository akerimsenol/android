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
package com.android.tools.idea.run.deployment;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.util.ArrayList;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SnapshotActionGroup extends ActionGroup {
  private final @NotNull Device myDevice;
  private final @NotNull DeviceAndSnapshotComboBoxAction myComboBoxAction;

  SnapshotActionGroup(@NotNull Device device, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
    setPopup(true);

    myDevice = device;
    myComboBoxAction = comboBoxAction;
  }

  @Override
  public @NotNull AnAction @NotNull [] getChildren(@Nullable AnActionEvent event) {
    Collection<Snapshot> snapshots = myDevice.getSnapshots();

    Collection<AnAction> children = new ArrayList<>(2 + snapshots.size());
    children.add(SelectTargetAction.newColdBootAction(myDevice, myComboBoxAction));
    children.add(SelectTargetAction.newQuickBootAction(myDevice, myComboBoxAction));

    snapshots.stream()
      .map(snapshot -> SelectTargetAction.newBootWithSnapshotAction(myDevice, myComboBoxAction, snapshot))
      .forEach(children::add);

    return children.toArray(new AnAction[0]);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    presentation.setIcon(myDevice.getIcon());
    presentation.setText(Devices.getText(myDevice.getName(), myDevice.getValidityReason()), false);
  }

  @Override
  public int hashCode() {
    return 31 * myDevice.hashCode() + myComboBoxAction.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SnapshotActionGroup)) {
      return false;
    }

    SnapshotActionGroup group = (SnapshotActionGroup)object;
    return myDevice.equals(group.myDevice) && myComboBoxAction.equals(group.myComboBoxAction);
  }
}
