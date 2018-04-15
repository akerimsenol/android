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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profilers.stacktrace.StackTraceGroup;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class EnergyCallstackView extends JPanel {

  @NotNull private final EnergyProfilerStageView myStageView;

  public EnergyCallstackView(@NotNull EnergyProfilerStageView stageView) {
    super(new VerticalFlowLayout());
    myStageView = stageView;
  }

  /**
   * Set the details view for all callstacks of a duration, if given {@code duration} is {@code null}, this clears the view.
   */
  public void setDuration(@Nullable EnergyDuration duration) {
    removeAll();
    if (duration == null) {
      return;
    }

    List<HideablePanel> callstackList = new ArrayList<>();
    StackTraceGroup stackTraceGroup = myStageView.getIdeComponents().createStackGroup();
    long startTimeNs = myStageView.getStage().getStudioProfilers().getSession().getStartTimestamp();
    for (EnergyProfiler.EnergyEvent event : duration.getEventList()) {
      if (event.getTraceId().isEmpty() || EnergyDuration.getMetadataName(event.getMetadataCase()).isEmpty()) {
        continue;
      }

      String callstackString = myStageView.getStage().requestBytes(event.getTraceId()).toStringUtf8();
      StackTraceModel model = new StackTraceModel(myStageView.getStage().getStudioProfilers().getIdeServices().getCodeNavigator());
      StackTraceView stackTraceView = stackTraceGroup.createStackView(model);
      stackTraceView.getModel().setStackFrames(callstackString);
      JComponent traceComponent = stackTraceView.getComponent();
      // Sets a border on the ListView so the horizontal scroll bar doesn't hide the bottom of the content. Also the ListView cannot resize
      // properly when the scroll pane resize, wrap it in a JPanel. So move the list view out of the original scroll pane.
      if (traceComponent instanceof JScrollPane) {
        traceComponent = (JComponent) ((JScrollPane)traceComponent).getViewport().getComponent(0);
        traceComponent.setBorder(new JBEmptyBorder(0, 0, 10, 0));
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(traceComponent, BorderLayout.CENTER);
        wrapperPanel.setBackground(traceComponent.getBackground());
        traceComponent = new JBScrollPane(wrapperPanel,
                                          ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      }

      String time = TimeAxisFormatter.DEFAULT.getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp() - startTimeNs));
      String description = time + "&nbsp;&nbsp;" + EnergyDuration.getMetadataName(event.getMetadataCase());
      HideablePanel hideablePanel = new HideablePanel.Builder(description, traceComponent)
        .setContentBorder(new JBEmptyBorder(5, 0, 0, 0))
        .setPanelBorder(new JBEmptyBorder(0, 0, 0, 0))
        .setTitleRightPadding(0)
        .build();
      // Make the call stack hideable panel use the parent component's background.
      hideablePanel.setBackground(null);
      callstackList.add(hideablePanel);
    }
    if (callstackList.size() > 2) {
      callstackList.forEach(c -> c.setExpanded(false));
    }

    JLabel label = new JLabel("<html><b>Callstacks</b>: " + callstackList.size() + "</html>");
    label.setBorder(new JBEmptyBorder(0, 0, 5, 0));
    add(label);
    callstackList.forEach(c -> add(c));
  }
}
