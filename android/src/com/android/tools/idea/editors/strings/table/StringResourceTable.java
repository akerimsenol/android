/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.editors.strings.StringResourceData;
import com.android.tools.idea.editors.strings.StringsWriteUtils;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.ui.TableUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.*;

public final class StringResourceTable extends JBTable implements DataProvider, PasteProvider {
  @NotNull
  private final AndroidFacet myFacet;

  @Nullable
  private StringResourceTableColumnFilter myColumnFilter;

  private boolean myColumnPreferredWidthsSet;

  public StringResourceTable(@NotNull AndroidFacet facet) {
    super(new StringResourceTableModel());

    CellEditorListener editorListener = new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent event) {
        refilter();
      }

      @Override
      public void editingCanceled(ChangeEvent event) {
      }
    };

    getDefaultEditor(Boolean.class).addCellEditorListener(editorListener);

    TableCellEditor editor = new StringsCellEditor();
    editor.addCellEditorListener(editorListener);

    InputMap inputMap = getInputMap(WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");

    setAutoResizeMode(AUTO_RESIZE_OFF);
    setCellSelectionEnabled(true);
    setDefaultEditor(String.class, editor);
    setDefaultRenderer(String.class, new StringsCellRenderer());
    setRowSorter(new ThreeStateTableRowSorter<>(getModel()));
    new TableSpeedSearch(this);

    myFacet = facet;
  }

  @NotNull
  @Override
  protected JTableHeader createDefaultTableHeader() {
    JTableHeader header = new JBTableHeader();

    header.setName("tableHeader");
    header.setReorderingAllowed(false);

    header.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        openPopup(e);
      }

      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        openPopup(e);
      }

      private void openPopup(@NotNull MouseEvent event) {
        if (!event.isPopupTrigger()) {
          return;
        }

        JTableHeader header = (JTableHeader)event.getSource();
        Point point = event.getPoint();
        int column = header.columnAtPoint(point);

        if (column == -1) {
          return;
        }

        Locale locale = getModel().getLocale(column);

        if (locale == null) {
          return;
        }

        showRemoveLocalePopupMenu(locale, header, point);
      }
    });

    return header;
  }

  private void showRemoveLocalePopupMenu(@NotNull Locale locale, @NotNull Component invoker, @NotNull Point point) {
    JMenuItem item = new JBMenuItem("Remove Locale");

    item.setName("removeLocaleMenuItem");
    item.addActionListener(event -> StringsWriteUtils.removeLocale(locale, myFacet, this));

    JPopupMenu menu = new JBPopupMenu();

    menu.add(item);
    menu.show(invoker, point.x, point.y);
  }

  @Nullable
  public StringResourceData getData() {
    return getModel().getData();
  }

  public void refilter() {
    getRowSorter().sort();
  }

  @Nullable
  public StringResourceTableRowFilter getRowFilter() {
    return (StringResourceTableRowFilter)getRowSorter().getRowFilter();
  }

  public void setRowFilter(@Nullable StringResourceTableRowFilter filter) {
    getRowSorter().setRowFilter(filter);
  }

  @Nullable
  public StringResourceTableColumnFilter getColumnFilter() {
    return myColumnFilter;
  }

  public void setColumnFilter(@Nullable StringResourceTableColumnFilter filter) {
    myColumnFilter = filter;
    createDefaultColumnsFromModel();
  }

  @Override
  public void createDefaultColumnsFromModel() {
    addColumns(removeAllColumns());
  }

  @NotNull
  private Map<String, TableColumn> removeAllColumns() {
    Map<String, TableColumn> map = Maps.newHashMapWithExpectedSize(columnModel.getColumnCount());

    while (columnModel.getColumnCount() != 0) {
      TableColumn column = columnModel.getColumn(0);

      columnModel.removeColumn(column);
      map.put((String)column.getHeaderValue(), column);
    }

    return map;
  }

  private void addColumns(@NotNull Map<String, TableColumn> map) {
    StringResourceTableModel model = getModel();
    TableCellRenderer renderer = tableHeader == null ? null : new LocaleRenderer(tableHeader.getDefaultRenderer(), model);

    IntStream.range(0, model.getColumnCount())
      .filter(this::includeColumn)
      .mapToObj(column -> getColumn(map, column, renderer))
      .forEach(this::addColumn);
  }

  private boolean includeColumn(int column) {
    if (column < FIXED_COLUMN_COUNT) {
      return true;
    }

    if (myColumnFilter == null) {
      return true;
    }

    Locale locale = getModel().getLocale(column);
    assert locale != null;

    return myColumnFilter.include(locale);
  }

  @NotNull
  private TableColumn getColumn(@NotNull Map<String, TableColumn> map, int column, @Nullable TableCellRenderer renderer) {
    TableColumn tableColumn = map.get(dataModel.getColumnName(column));

    if (tableColumn == null) {
      tableColumn = new TableColumn(column);

      if (column >= FIXED_COLUMN_COUNT && renderer != null) {
        tableColumn.setHeaderRenderer(renderer);
      }
    }
    else {
      tableColumn.setModelIndex(column);
    }

    return tableColumn;
  }

  public int getSelectedRowModelIndex() {
    return convertRowIndexToModel(getSelectedRow());
  }

  @NotNull
  public int[] getSelectedRowModelIndices() {
    return Arrays.stream(getSelectedRows())
      .map(this::convertRowIndexToModel)
      .toArray();
  }

  public int getSelectedColumnModelIndex() {
    return convertColumnIndexToModel(getSelectedColumn());
  }

  public int[] getSelectedColumnModelIndices() {
    return Arrays.stream(getSelectedColumns())
      .map(this::convertColumnIndexToModel)
      .toArray();
  }

  @Override
  public TableRowSorter<StringResourceTableModel> getRowSorter() {
    //noinspection unchecked
    return (TableRowSorter<StringResourceTableModel>)super.getRowSorter();
  }

  @Override
  public StringResourceTableModel getModel() {
    return (StringResourceTableModel)super.getModel();
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);
    TableRowSorter<StringResourceTableModel> sorter = getRowSorter();
    if (sorter != null) { // can be null when called from constructor
      sorter.setModel(getModel());
    }

    if (tableHeader == null) {
      return;
    }

    if (myColumnPreferredWidthsSet) {
      return;
    }

    OptionalInt optionalWidth = getKeyColumnPreferredWidth();

    if (optionalWidth.isPresent()) {
      columnModel.getColumn(KEY_COLUMN).setPreferredWidth(optionalWidth.getAsInt());
    }

    optionalWidth = getDefaultValueAndLocaleColumnPreferredWidths();

    if (optionalWidth.isPresent()) {
      int width = optionalWidth.getAsInt();

      IntStream.range(DEFAULT_VALUE_COLUMN, getColumnCount())
        .mapToObj(columnModel::getColumn)
        .forEach(column -> column.setPreferredWidth(width));
    }

    myColumnPreferredWidthsSet = true;
  }

  @NotNull
  @VisibleForTesting
  public OptionalInt getKeyColumnPreferredWidth() {
    return IntStream.range(0, getRowCount())
      .map(row -> getPreferredWidth(getCellRenderer(row, KEY_COLUMN), getValueAt(row, KEY_COLUMN), row, KEY_COLUMN))
      .max();
  }

  @NotNull
  @VisibleForTesting
  public OptionalInt getDefaultValueAndLocaleColumnPreferredWidths() {
    return IntStream.range(DEFAULT_VALUE_COLUMN, getColumnCount())
      .map(column -> getPreferredWidth(getHeaderRenderer(column), getColumnName(column), -1, column))
      .max();
  }

  @NotNull
  private TableCellRenderer getHeaderRenderer(int column) {
    TableCellRenderer renderer = columnModel.getColumn(column).getHeaderRenderer();
    return renderer == null ? tableHeader.getDefaultRenderer() : renderer;
  }

  private int getPreferredWidth(@NotNull TableCellRenderer renderer, @NotNull Object value, int row, int column) {
    return renderer.getTableCellRendererComponent(this, value, false, false, row, column).getPreferredSize().width + 2;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return dataId.equals(PlatformDataKeys.PASTE_PROVIDER.getName()) ? this : null;
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    if (getSelectedRowCount() != 1 || getSelectedColumnCount() != 1) {
      return false;
    }
    else {
      int column = getSelectedColumn();
      return column != KEY_COLUMN && column != UNTRANSLATABLE_COLUMN;
    }
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return isPastePossible(dataContext);
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    Transferable transferable = CopyPasteManager.getInstance().getContents();

    if (transferable != null) {
      TableUtils.paste(this, transferable);
    }
  }

  static class ThreeStateTableRowSorter<M extends TableModel> extends TableRowSorter<M> {
    public ThreeStateTableRowSorter(M model) {
      super(model);
    }

    @Override
    public void toggleSortOrder(int column) {
      List<? extends SortKey> sortKeys = getSortKeys();
      if (!sortKeys.isEmpty() && sortKeys.get(0).getSortOrder() == SortOrder.DESCENDING) {
        setSortKeys(null);
        return;
      }
      super.toggleSortOrder(column);
    }

    @Override
    public void modelStructureChanged() {
      List<? extends SortKey> sortKeys = getSortKeys();
      super.modelStructureChanged();
      setSortKeys(sortKeys);
    }
  }
}
