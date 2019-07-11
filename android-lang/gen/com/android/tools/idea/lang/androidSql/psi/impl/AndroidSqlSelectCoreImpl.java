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

// ATTENTION: This file has been automatically generated from roomSql.bnf. Do not edit it manually.

package com.android.tools.idea.lang.androidSql.psi.impl;

import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.lang.androidSql.psi.*;

public class AndroidSqlSelectCoreImpl extends ASTWrapperPsiElement implements AndroidSqlSelectCore {

  public AndroidSqlSelectCoreImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AndroidSqlVisitor visitor) {
    visitor.visitSelectCore(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AndroidSqlVisitor) accept((AndroidSqlVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AndroidSqlSelectCoreSelect getSelectCoreSelect() {
    return findChildByClass(AndroidSqlSelectCoreSelect.class);
  }

  @Override
  @Nullable
  public AndroidSqlSelectCoreValues getSelectCoreValues() {
    return findChildByClass(AndroidSqlSelectCoreValues.class);
  }

}
