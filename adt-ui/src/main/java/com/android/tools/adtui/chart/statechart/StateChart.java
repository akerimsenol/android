/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.adtui.chart.statechart;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.MouseAdapterComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StateChartModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A chart component that renders series of state change events as rectangles.
 */
public final class StateChart<T> extends MouseAdapterComponent<T> {

  public enum RenderMode {
    BAR,  // Each state is rendered as a filled rectangle until the next state changed.
    TEXT  // Each state is marked with a vertical line and and corresponding state text/label at the beginning.
  }

  private static final int TEXT_PADDING = 3;
  private static final int HIGHLIGHT_WIDTH = 2;

  private StateChartModel<T> myModel;

  /**
   * A function that maps between a type T, and a color to be used in the StateChart, all values of T should return a valid color.
   */
  @NotNull
  private Function<T, Color> myColorMapper;

  /**
   * A color used to highlight every state for BAR render mode only. If the highlight color is not null, there is a highlight of
   * {@link HIGHLIGHT_WIDTH} at the beginning of every state start time point. The highlight color and {@link myColorMapper} are different,
   * even if the mapped color is transparent, the highlight color may not be transparent.
   */
  @Nullable
  private Color myBarHighlightColor;

  private float myHeightGap;

  @NotNull
  private RenderMode myRenderMode;

  @NotNull
  private StateChartConfig<T> myConfig;

  private boolean myRender;

  @NotNull
  private final StateChartTextConverter<T> myTextConverter;

  /**
   * @param colors map of a state to corresponding color
   */
  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model, @NotNull Map<T, Color> colors) {
    this(model, colors::get);
  }

  public StateChart(@NotNull StateChartModel<T> model, @NotNull Function<T, Color> colorMapping) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, (val) -> val.toString());
  }

  public StateChart(@NotNull StateChartModel<T> model, @NotNull Function<T, Color> colorMapping, StateChartTextConverter<T> textConverter) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, textConverter);
  }

  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model, @NotNull Map<T, Color> colors, @NotNull StateChartConfig<T> config) {
    this(model, config, (val) -> colors.get(val), (val) -> val.toString());
  }

  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartConfig<T> config,
                    @NotNull Function<T, Color> colorMapping,
                    @NotNull StateChartTextConverter<T> textConverter) {
    super(config.getRectangleHeightRatio(), config.getRectangleMouseOverHeightRatio());
    myColorMapper = colorMapping;
    myRenderMode = RenderMode.BAR;
    myConfig = config;
    myRender = true;
    myTextConverter = textConverter;
    setFont(AdtUiUtils.DEFAULT_FONT);
    setModel(model);
    setHeightGap(myConfig.getHeightGap());
  }

  public void setModel(@NotNull StateChartModel<T> model) {
    if (myModel != null) {
      myModel.removeDependencies(myAspectObserver);
    }
    myModel = model;
    myModel.addDependency(myAspectObserver).onChange(StateChartModel.Aspect.STATE_CHART, this::modelChanged);
    modelChanged();
  }

  private void modelChanged() {
    myRender = true;
    opaqueRepaint();
  }

  public void setRenderMode(@NotNull RenderMode mode) {
    myRenderMode = mode;
  }

  /**
   * Sets the gap between multiple data series.
   *
   * @param gap The gap value as a percentage {0...1} of the height given to each data series
   */
  public void setHeightGap(float gap) {
    myHeightGap = gap;
  }

  /**
   * Set a highlight color which will be used to emphasize when a state change happens. If set to {@code null}, the state change highlight
   * is cleared. This color only affects state charts that are rendered in {@link RenderMode.BAR} mode.
   */
  public void setBarHighlightColor(@Nullable Color barHighlightColor) {
    myBarHighlightColor = barHighlightColor;
  }

  @NotNull
  public Color getColor(T value) {
    return myColorMapper.apply(value);
  }

  protected void render() {
    long renderTime = System.nanoTime();

    List<RangedSeries<T>> series = myModel.getSeries();
    int seriesSize = series.size();
    if (seriesSize == 0) {
      return;
    }

    // TODO support adding series on the fly and interpolation.
    float height = 1f / seriesSize;
    float gap = height * myHeightGap;
    setHeightFactor(height);

    int seriesIndex = 0;
    clearRectangles();
    for (RangedSeries<T> data : series) {
      double min = data.getXRange().getMin();
      double max = data.getXRange().getMax();
      float startHeight = 1 - (height * (seriesIndex + 1));

      // Construct rectangles.
      long previousX = -1;
      T previousValue = null;
      for (SeriesData<T> seriesData : data.getSeries()) {
        long x = seriesData.x;
        T value = seriesData.value;

        if (value.equals(previousValue)) {
          // Ignore repeated values
          continue;
        }

        // Don't draw if this block doesn't intersect with [min..max]
        if (previousValue != null && x >= min) {
          // Draw the previous block.
          setRectangleData(previousValue,
                           Math.max(min, previousX),
                           Math.min(max, x),
                           min,
                           max,
                           startHeight + gap * 0.5f,
                           gap);
        }

        // Start a new block.
        previousValue = value;
        previousX = x;

        if (previousX >= max) {
          // Drawn past max range, stop.
          break;
        }
      }
      // The last data point continues till max
      if (previousX < max && previousValue != null) {
        setRectangleData(previousValue,
                         Math.max(min, previousX),
                         max,
                         min,
                         max,
                         startHeight + gap * 0.5f,
                         gap);
      }
      seriesIndex++;
    }

    addDebugInfo("Render time: %.2fms", (System.nanoTime() - renderTime) / 1000000.f);
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    if (myRender) {
      render();
      myRender = false;
    }
    long drawTime = System.nanoTime();

    g2d.setFont(getFont());
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    List<Shape> transformedShapes = new ArrayList<>(getRectangleCount());
    List<T> transformedValues = new ArrayList<>(getRectangleCount());
    AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());
    for (Rectangle2D.Float rectangle : getRectangles()) {
      transformedShapes.add(scale.createTransformedShape(rectangle));
      transformedValues.add(getRectangleValue(rectangle));
    }
    myConfig.getReducer().reduce(transformedShapes, transformedValues);
    assert transformedShapes.size() == transformedValues.size();

    for (int i = 0; i < transformedShapes.size(); i++) {
      Shape shape = transformedShapes.get(i);
      T value = transformedValues.get(i);
      g2d.setColor(getColor(value));
      g2d.fill(shape);
      if (myRenderMode == RenderMode.TEXT) {
        String valueText = myTextConverter.convertToString(value);
        Rectangle2D rect = shape.getBounds2D();
        String text = AdtUiUtils.shrinkToFit(valueText, mDefaultFontMetrics, (float)rect.getWidth() - TEXT_PADDING * 2);
        if (!text.isEmpty()) {
          g2d.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
          g2d.drawString(text, (float)(rect.getX() + TEXT_PADDING), (float)(rect.getY() + rect.getHeight() - TEXT_PADDING));
        }
      }

      if (myBarHighlightColor != null && myRenderMode == RenderMode.BAR) {
        g2d.setColor(myBarHighlightColor);
        Rectangle2D rect = shape.getBounds2D();
        g2d.fill(new Rectangle2D.Double(rect.getX(), rect.getY(), Math.min(HIGHLIGHT_WIDTH, rect.getWidth()), rect.getHeight()));
      }
    }

    addDebugInfo("Draw time: %.2fms", (System.nanoTime() - drawTime) / 1000000.f);
    addDebugInfo("# of drawn rects: %d", transformedShapes.size());
  }
}

