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
package com.android.tools.idea.mlkit.importmodel;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.mlkit.MlUtils;
import com.android.tools.idea.npw.template.components.ModuleTemplateComboProvider;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.mlkit.MlConstants;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.TfliteModelException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import icons.StudioIcons;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard step that allows the user to point to an existing ml model file to import as ml model. Also shows
 * necessary dependencies to use this ml model.
 */
public class ChooseMlModelStep extends ModelWizardStep<MlWizardModel> {

  private final BindingsManager myBindings = new BindingsManager();

  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private final ValidatorPanel myValidatorPanel;

  private JPanel myPanel;
  private TextFieldWithBrowseButton myModelLocation;
  private ComboBox<NamedModuleTemplate> myFlavorBox;
  private JCheckBox myAutoCheckBox;
  private JTextArea myInfoTextArea;
  private HyperlinkLabel myLearnMoreLabel;

  public ChooseMlModelStep(@NotNull MlWizardModel model,
                           @NotNull List<NamedModuleTemplate> moduleTemplates,
                           @NotNull Project project,
                           @NotNull String title) {
    super(model, title);

    myModelLocation.addBrowseFolderListener("Select TensorFlow Lite Model Location",
                                            "Select existing TensorFlow Lite model to import to ml folder",
                                            project,
                                            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor());
    for (NamedModuleTemplate namedModuleTemplate : moduleTemplates) {
      myFlavorBox.addItem(namedModuleTemplate);
    }
    myLearnMoreLabel.setIcon(StudioIcons.Common.INFO);
    myLearnMoreLabel.setHyperlinkText("This will ensure new ML Model Binding feature works correctly ", "Learn more", "");
    myLearnMoreLabel.setHyperlinkTarget("https://developer.android.com/studio/write/mlmodelbinding");

    myBindings.bindTwoWay(new TextProperty(myModelLocation.getTextField()), model.sourceLocation);
    myBindings.bindTwoWay(new SelectedProperty(myAutoCheckBox), model.autoUpdateBuildFile);

    myInfoTextArea.setBackground(null);
    myInfoTextArea.setForeground(JBColor.DARK_GRAY);

    String text = getInformationText();
    myInfoTextArea.setText(text);
    if (text.isEmpty()) {
      myAutoCheckBox.setVisible(false);
      model.autoUpdateBuildFile.set(false);
      myLearnMoreLabel.setVisible(false);
    }

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    Expression<File> locationFile = model.sourceLocation.transform(File::new);
    myValidatorPanel.registerValidator(locationFile, value -> checkPath(value));

    SelectedItemProperty<NamedModuleTemplate> selectedFavor = new SelectedItemProperty<>(myFlavorBox);
    myValidatorPanel.registerValidator(ObjectProperty.wrap(selectedFavor), value -> checkFlavor(value));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }

  @NotNull
  public String getInformationText() {
    StringBuilder stringBuilder = new StringBuilder();
    Module module = getModel().getModule();

    if (!MlUtils.isMlModelBindingBuildFeatureEnabled(module)) {
      stringBuilder.append("buildFeatures {\n" +
                           "  mlModelBinding true\n" +
                           "}\n\n");
    }

    for (GradleCoordinate dep : MlUtils.getMissingDependencies(module)) {
      stringBuilder.append(dep).append("\n");
    }

    return stringBuilder.toString();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    super.onProceeding();
    File mlDirectory = ((NamedModuleTemplate)myFlavorBox.getSelectedItem()).component2().getMlModelsDirectories().get(0);
    getModel().mlDirectory.set(mlDirectory.getAbsolutePath());
  }

  @NotNull
  private Validator.Result checkPath(@NotNull File file) {
    if (!file.isFile()) {
      return new Validator.Result(Validator.Severity.ERROR, "Please select a TensorFlow Lite model file to import.");
    }
    else if (file.length() > MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES) {
      return new Validator.Result(Validator.Severity.ERROR, "This file is over the maximum supported size 200 MB.");
    }
    else if (!isValidTfliteModel(file)) {
      return new Validator.Result(Validator.Severity.ERROR, "This file is not a valid TensorFlow Lite model file.");
    }
    else {
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, false);
      VirtualFile existingFile = findExistingModelFile(file.getName());
      if (existingFile != null && existingFile.exists()) {
        return new Validator.Result(Validator.Severity.WARNING, "File already exists and will be override.");
      }

      if (virtualFile != null && SingleRootFileViewProvider.isTooLargeForContentLoading(virtualFile)) {
        return new Validator.Result(Validator.Severity.WARNING, "This file is larger than 20 MB and may be a performance impact.");
      }
    }
    return Validator.Result.OK;
  }

  @Nullable
  private VirtualFile findExistingModelFile(@NotNull String fileName) {
    VirtualFile directory =
      VfsUtil.findFileByIoFile(((NamedModuleTemplate)myFlavorBox.getSelectedItem()).component2().getMlModelsDirectories().get(0), false);
    if (directory == null || !directory.exists()) {
      return null;
    }

    return directory.findChild(fileName);
  }

  private static boolean isValidTfliteModel(@NotNull File file) {
    if (!file.getName().endsWith(".tflite")) {
      return false;
    }
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, false);
    if (virtualFile == null) {
      return false;
    }

    try {
      byte[] bytes = null;
      try {
        // First try load it using virtual file API to get cache benefit.
        bytes = virtualFile.contentsToByteArray();
      }
      catch (FileTooBigException e) {
        // Otherwise, load it with Java API.
        bytes = Files.readAllBytes(file.toPath());
      }
      finally {
        if (bytes == null) {
          return false;
        }
        ModelInfo.buildFrom(ByteBuffer.wrap(bytes));
      }
    }
    catch (IOException | TfliteModelException | RuntimeException e) {
      Logger.getInstance(ChooseMlModelStep.class).warn("Exception when parsing TensorFlow Lite model: " + file.getName(), e);
      return false;
    }

    return true;
  }

  @NotNull
  Validator.Result checkFlavor(@NotNull NamedModuleTemplate flavor) {
    if (flavor.getPaths().getMlModelsDirectories().isEmpty()) {
      new Validator.Result(Validator.Severity.ERROR, "No valid ml directory in checkFlavor.");
    }

    return Validator.Result.OK;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myModelLocation;
  }

  private void createUIComponents() {
    myFlavorBox = new ModuleTemplateComboProvider(Collections.emptyList()).createComponent();
  }
}
