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
package com.android.tools.idea.sdkv2;

import com.android.repository.api.*;
import com.android.repository.impl.installer.BasicInstaller;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Installer that applies binary diffs.
 * The actual logic for applying the diffs is not here; rather, it is contained in a separate SDK component. This is to allow
 * changes in the diff algorithm or patch format without backward compatibility concerns.
 * Each SDK package that includes diff-type archives must also include a dependency on a patcher component. That component contains
 * the necessary code to apply the diff (this is the same as the code used to update studio itself), as well as UI integration between
 * the patcher and this installer.
 */
public class PatchInstaller extends BasicInstaller {

  /**
   * Repo-style path prefix (including separator) for patcher packages.
   */
  public static final String PATCHER_PATH_PREFIX = "patcher" + RepoPackage.PATH_SEPARATOR;

  /**
   * Name of the patcher jar file from the patcher package.
   */
  private static final String PATCHER_JAR_FN = "patcher.jar";

  /**
   * Runner class we'll invoke from the jar.
   */
  private static final String RUNNER_CLASS_NAME = "com.intellij.updater.Runner";

  /**
   * Interface from the patcher jar for the UI. Needed to get the update method.
   */
  private static final String UPDATER_UI_CLASS_NAME = "com.intellij.updater.UpdaterUI";

  /**
   * Interface for the actual UI class we'll create.
   */
  private static final String REPO_UI_CLASS_NAME = "com.android.tools.idea.updaterui.RepoUpdaterUI";

  /**
   * Subdirectory in the install temp dir where we'll actually prepare the patch.
   */
  private static final String WORK_DIR_FN = "work";

  /**
   * Cache of patcher classes. Key is jar file, subkey is class name.
   */
  private static Map<File, Map<String, Class<?>>> ourPatcherCache = new WeakHashMap<File, Map<String, Class<?>>>();

  public PatchInstaller(RepoPackage p, RepoManager repoManager, FileOp fop) {
    super(p, repoManager, fop);
  }

  /**
   * {@inheritDoc}
   *
   * If the patch fails, fall back to trying a complete install.
   */
  @Override
  protected boolean doPrepareInstall(@NotNull File installTempPath,
                                     @NotNull Downloader downloader,
                                     @Nullable SettingsController settings,
                                     @NotNull ProgressIndicator progress) {
    if (!doPrepareInstallInternal(installTempPath, downloader, settings, progress)) {
      progress.logWarning("Failed to install patch, attempting fresh install...");
      return super.doPrepareInstall(installTempPath, downloader, settings, progress);
    }
    return true;
  }

  /**
   * Actually do the patch install.
   */
  private boolean doPrepareInstallInternal(@NotNull File tempDir,
                                           @NotNull Downloader downloader,
                                           @Nullable SettingsController settings,
                                           @NotNull ProgressIndicator progress) {
    Map<String, ? extends LocalPackage> localPackages = getRepoManager().getPackages().getLocalPackages();
    LocalPackage local = localPackages.get(getPackage().getPath());
    assert local != null;
    RemotePackage p = (RemotePackage)getPackage();
    Archive archive = p.getArchive();
    assert archive != null;
    Archive.PatchType patch = archive.getPatch(local.getVersion());
    assert patch != null;

    File patchFile = getPatchFile(patch, p, tempDir, downloader, settings, progress);
    if (patchFile == null) {
      progress.logWarning("Patch failed to download.");
      return false;
    }

    File patcherJar = getPatcherFile(localPackages, progress);
    if (patcherJar == null) {
      progress.logWarning("Couldn't find patcher jar!");
      return false;
    }

    Map<String, Class<?>> classMap = loadClasses(patcherJar, progress);
    if (classMap == null) {
      return false;
    }

    File workDir = new File(tempDir, WORK_DIR_FN);
    if (mFop.exists(workDir)) {
      mFop.deleteFileOrFolder(workDir);
    }
    try {
      FileOpUtils.recursiveCopy(local.getLocation(), workDir, mFop, progress);
    }
    catch (IOException e) {
      progress.logWarning("Failed to copy package to temporary location", e);
      return false;
    }
    mFop.deleteFileOrFolder(new File(workDir, InstallerUtil.INSTALLER_DIR_FN));
    mFop.delete(new File(workDir, LocalRepoLoader.PACKAGE_XML_FN));

    boolean result = runPatcher(progress, workDir, patchFile, classMap.get(RUNNER_CLASS_NAME), classMap.get(UPDATER_UI_CLASS_NAME),
                                classMap.get(REPO_UI_CLASS_NAME));
    if (!result) {
      return false;
    }
    try {
      InstallerUtil.writePackageXml(p, tempDir, getRepoManager(), mFop, progress);
    }
    catch (IOException e) {
      progress.logWarning("Failed to write new package.xml");
      return false;
    }

    progress.logInfo("Done");
    progress.setFraction(1);
    progress.setIndeterminate(false);

    getRepoManager().markInvalid();
    return true;
  }

  @Override
  protected boolean doCompleteInstall(@NotNull File installTempPath,
                                      @NotNull File destination,
                                      @NotNull ProgressIndicator progress) {

    File workDir = new File(installTempPath, WORK_DIR_FN);
    if (!doCompleteInstallInternal(workDir, destination, mFop, progress)) {
      return super.doCompleteInstall(installTempPath, destination, progress);
    }
    return true;
  }

  private static boolean doCompleteInstallInternal(@NotNull File src, @NotNull File dest, @NotNull FileOp fop, @NotNull ProgressIndicator progress) {
    progress.logInfo("Moving files into place...");
    try {
      FileOpUtils.safeRecursiveOverwrite(src, dest, fop, progress);
    }
    catch (IOException e) {
      progress.logWarning("Failed to move patched files into place", e);
      return false;
    }
    return true;
  }

  /**
   * Run the patcher by reflection.
   */
  @SuppressWarnings("unchecked")
  @VisibleForTesting
  static boolean runPatcher(@NotNull ProgressIndicator progress,
                            @NotNull File localPackageLocation,
                            @NotNull File patchFile,
                            @NotNull Class runnerClass,
                            @NotNull Class uiBaseClass,
                            @NotNull Class uiClass) {
    Object ui;
    try {
      ui = uiClass.getConstructor(ProgressIndicator.class).newInstance(progress);
    }
    catch (Throwable e) {
      progress.logWarning("Failed to create updater ui!", e);
      return false;
    }

    Method initLogger;
    try {
      initLogger = runnerClass.getMethod("initLogger");
      initLogger.invoke(null);
    }
    catch (Throwable e) {
      progress.logWarning("Failed to initialize logger!", e);
      return false;
    }

    final Method doInstall;
    try {
      doInstall = runnerClass.getMethod("doInstall", String.class, uiBaseClass, String.class);
    }
    catch (Throwable e) {
      progress.logWarning("Failed to find main method in runner!", e);
      return false;
    }

    try {
      progress.logInfo("Running patch installer...");
      if (!(Boolean)doInstall.invoke(null, patchFile.getPath(), ui, localPackageLocation.getPath())) {
        progress.logWarning("Failed to apply patch");
        return false;
      }
      progress.logInfo("Patch applied.");
    }
    catch (Throwable e) {
      progress.logWarning("Failed to run patcher", e);
      return false;
    }

    return true;
  }

  /**
   * Resolves and downloads the patch for the given {@link Archive.PatchType}.
   *
   * @return {@code null} if unsuccessful.
   */
  @Nullable
  @VisibleForTesting
  static File getPatchFile(@NotNull Archive.PatchType patch,
                           @NotNull RemotePackage p,
                           @NotNull File tempDir,
                           @NotNull Downloader downloader,
                           @Nullable SettingsController settings,
                           @NotNull ProgressIndicator progress) {
    URL url = InstallerUtil.resolveUrl(patch.getUrl(), p, progress);
    if (url == null) {
      progress.logWarning("Failed to resolve URL: " + patch.getUrl());
      return null;
    }
    try {
      File patchFile = new File(tempDir, "patch.jar");
      downloader.downloadFully(url, settings, patchFile, progress);
      return patchFile;
    }
    catch (IOException e) {
      progress.logWarning("Error during downloading", e);
      return null;
    }
  }

  /**
   * Loads the patcher classes needed to apply the patches for the given {@link RemotePackage}.
   *
   * @return Map of class names to {@link Class} objects for the classes need to run the patcher.
   */
  @Nullable
  private Map<String, Class<?>> loadClasses(@NotNull File patcherJar,
                                            @NotNull ProgressIndicator progress) {
    Map<String, Class<?>> result = ourPatcherCache.get(patcherJar);
    if (result != null) {
      return result;
    }

    ClassLoader loader = getClassLoader(progress, patcherJar);
    if (loader == null) {
      return null;
    }

    result = loadClasses(progress, loader);
    if (result == null) {
      return null;
    }
    ourPatcherCache.put(patcherJar, result);
    return result;
  }

  /**
   * Loads the required classes (or returns cached versions if available).
   *
   * @return A map from classname to class instance, or null if the required classes were not found.
   */
  @Nullable
  private static Map<String, Class<?>> loadClasses(@NotNull ProgressIndicator progress, @NotNull ClassLoader loader) {
    Map<String, Class<?>> result;
    result = Maps.newHashMap();
    try {
      result.put(RUNNER_CLASS_NAME, Class.forName(RUNNER_CLASS_NAME, true, loader));
      result.put(UPDATER_UI_CLASS_NAME, Class.forName(UPDATER_UI_CLASS_NAME, true, loader));
      result.put(REPO_UI_CLASS_NAME, Class.forName(REPO_UI_CLASS_NAME, true, loader));
    }
    catch (Throwable e) {
      progress.logWarning("Failed to find installer classes", e);
      return null;
    }
    return result;
  }

  /**
   * Gets a class loader for the given jar.
   */
  @Nullable
  private ClassLoader getClassLoader(@NotNull ProgressIndicator progress, @NotNull File patcherJar) {
    ClassLoader loader;
    try {
      loader = UrlClassLoader.build().urls(patcherJar.toURI().toURL()).parent(getClass().getClassLoader()).get();
    }
    catch (MalformedURLException e) {
      // Shouldn't happen
      progress.logError("Failed to create URL from file: " + patcherJar, e);
      return null;
    }
    return loader;
  }

  /**
   * Gets the patcher jar required by our package.
   *
   * @return The location of the patcher.jar, or null if it was not found.
   */
  @Nullable
  @VisibleForTesting
  File getPatcherFile(@NotNull Map<String, ? extends LocalPackage> localPackages,
                      @NotNull ProgressIndicator progress) {
    File patcherJar = null;
    for (Dependency d : getPackage().getAllDependencies()) {
      if (d.getPath().startsWith(PATCHER_PATH_PREFIX)) {
        LocalPackage patcher = localPackages.get(d.getPath());
        if (patcher != null) {
          patcherJar = new File(patcher.getLocation(), PATCHER_JAR_FN);
          break;
        }
      }
    }
    if (patcherJar == null || !mFop.isFile(patcherJar)) {
      progress.logWarning("Failed to find patcher!");
      return null;
    }
    return patcherJar;
  }
}
