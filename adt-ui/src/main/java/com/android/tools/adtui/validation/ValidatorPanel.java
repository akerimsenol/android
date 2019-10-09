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
package com.android.tools.adtui.validation;

import static com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE;
import static com.intellij.xml.util.XmlStringUtil.escapeString;
import static com.intellij.xml.util.XmlStringUtil.isWrappedInHtml;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;

import com.android.tools.adtui.validation.validators.TrueValidator;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.SwingHelper;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * A panel that wraps some inner content and allows registering {@link Validator}s, which, if any
 * of them are invalid, causes a warning or error message to display across the bottom of the
 * panel.
 */
public final class ValidatorPanel extends JPanel implements Disposable {
  /** Used to set empty validation text. If completely empty, the height calculations are off. */
  private static final String BLANK_HTML = "<html></html>";

  private final ListenerManager myListeners = new ListenerManager();
  private final List<Validator.Result> myResults = new ArrayList<>();
  private final BoolProperty myHasErrors = new BoolValueProperty();
  private final BoolProperty myIsOK = new BoolValueProperty();

  private JPanel myRootPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel mySouthPanel;
  private JBLabel mySeverityIcon;
  private JEditorPane myValidationText;
  private ErrorDetailDialog myErrorDetailDialog;
  private Validator.Result myValidationResult = Validator.Result.OK;

  /**
   * Initializes the validator panel.
   *
   * @param parentDisposable the disposable parent
   * @param innerPanel the panel that will be wrapped by the validator panel
   * @param errorDetailDialogTitle the title for the error detail dialog
   * @param errorDetailHeader the header label for the error detail dialog
   */
  public ValidatorPanel(@NotNull Disposable parentDisposable, @NotNull JComponent innerPanel,
                        @NotNull String errorDetailDialogTitle, @NotNull String errorDetailHeader) {
    super(new BorderLayout());

    add(myRootPanel);
    myRootPanel.add(innerPanel);

    myValidationText.setName("ValidationText");
    myValidationText.setText(BLANK_HTML);

    myValidationText.addHyperlinkListener(event -> {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        String detailedMessage = myValidationResult.getDetailedMessage();
        if (myErrorDetailDialog == null) {
          myErrorDetailDialog = new ErrorDetailDialog(errorDetailDialogTitle, errorDetailHeader, detailedMessage);
          // Remove reference to the error detail dialog when it is closed.
          Disposer.register(myErrorDetailDialog.getDisposable(), () -> myErrorDetailDialog = null);
          // Close the error detail dialog when the ValidatorPanel is disposed.
          Disposer.register(this, () -> { if (myErrorDetailDialog != null) myErrorDetailDialog.close(CANCEL_EXIT_CODE); });
          myErrorDetailDialog.show();
        } else {
          myErrorDetailDialog.setText(detailedMessage);
        }
      }
    });

    Disposer.register(parentDisposable, this);
  }

  public ValidatorPanel(@NotNull Disposable parentDisposable, @NotNull JComponent innerPanel) {
    this(parentDisposable, innerPanel, "Errors", "Error Details:");
  }

  /**
   * Register a {@link Validator} linked to a target property. Whenever the target property
   * changes, the validator will be tested with its value.
   * <p>
   * If multiple validators produce non-OK results, the maximum severity wins. If there are
   * multiple messages with the same severity, the message produced by the validator that
   * was registered first wins.
   * <p>
   * See also {@link #hasErrors()}, which will be true if any validator has returned an
   * {@link Validator.Severity#ERROR} result.
   */
  public <T> void registerValidator(@NotNull ObservableValue<T> value, @NotNull Validator<? super T> validator) {
    int index = myResults.size();
    myResults.add(Validator.Result.OK);
    myListeners.listenAndFire(value, () -> {
      Validator.Result oldValue = myResults.get(index);
      Validator.Result newValue = validator.validate(value.get());
      if (!newValue.equals(oldValue)) {
        myResults.set(index, newValue);
        updateValidationText();
      }
    });
  }

  public void validateAll() {
    myListeners.fireAll();
  }

  /**
   * Registers a target observable boolean as a simple test which, if {@code false}, means
   * the {@code message} should be shown with the specified {@code severity}.
   */
  public void registerTest(@NotNull ObservableValue<Boolean> value, @NotNull Validator.Severity severity, @NotNull String message) {
    registerValidator(value, new TrueValidator(severity, message));
  }

  /**
   * Calls {@link #registerTest(ObservableValue, Validator.Severity, String)} with an error severity.
   */
  public void registerTest(@NotNull ObservableValue<Boolean> value, @NotNull String message) {
    registerTest(value, Validator.Severity.ERROR, message);
  }

  /**
   * Registers a target observable string which represents an invalidation message. If the string
   * is set to some value, this panel will display it with the specified {@code severity}.
   **/
  public void registerMessageSource(@NotNull ObservableValue<String> message, @NotNull Validator.Severity severity) {
    registerValidator(message, value -> {
      if (value.isEmpty()) {
        return Validator.Result.OK;
      }
      return new Validator.Result(severity, value);
    });
  }

  /**
   * Calls {@link #registerMessageSource(ObservableValue, Validator.Severity)} with an error severity.
   */
  public void registerMessageSource(@NotNull ObservableValue<String> message) {
    registerMessageSource(message, Validator.Severity.ERROR);
  }

  /**
   * Returns a property which indicates if any of the components in this panel are invalid.
   * <p>
   * This is a useful property for UIs to listen to, as they can bind various components (such as
   * a Next button) as appropriate, disabling functionality until all errors are resolved.
   */
  @NotNull
  public ObservableBool hasErrors() {
    return myHasErrors;
  }

  /**
   * Returns a property which indicates if none of the components in this panel are invalid or has warnings.
   */
  @NotNull
  public ObservableBool isOK() {
    return myIsOK;
  }

  private void updateValidationText() {
    myValidationResult = Validator.Result.OK;
    for (Validator.Result result : myResults) {
      if (result.getSeverity().compareTo(myValidationResult.getSeverity()) > 0) {
        myValidationResult = result;
        if (myValidationResult.getSeverity() == Validator.Severity.ERROR) {
          break;
        }
      }
    }

    if (myValidationResult.getSeverity() == Validator.Severity.OK) {
      mySeverityIcon.setIcon(null);
      myValidationText.setText(BLANK_HTML);
    }
    else {
      mySeverityIcon.setIcon(myValidationResult.getSeverity().getIcon());
      String message = myValidationResult.getMessage().trim();
      // A message has to be wrapped in HTML to be displayed properly by JEditorPane.
      if (!isWrappedInHtml(message)) {
        if (!StringUtil.isEmpty(myValidationResult.getDetailedMessage())) {
          message = wrapInHtml("<a href=\"details\">" + convertToHtml(message) + "</a>"); // Make the whole message a hyperlink.
        }
        else {
          message = wrapInHtml(convertToHtml(message));
        }
      }
      myValidationText.setText(message);
    }

    myHasErrors.set(myValidationResult.getSeverity() == Validator.Severity.ERROR);
    myIsOK.set(myValidationResult.getSeverity() == Validator.Severity.INFO || myValidationResult.getSeverity() == Validator.Severity.OK);
  }

  @NotNull
  private static String convertToHtml(String message) {
    return StringUtil.replace(escapeString(message), "\n", "<br>");
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }

  @TestOnly
  @NotNull
  public JEditorPane getValidationText() {
    return myValidationText;
  }

  private void createUIComponents() {
    myValidationText = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
    myValidationText.setOpaque(false);
    myValidationText.setFocusable(false);
    myValidationText.addHyperlinkListener(event -> {});
  }
}
