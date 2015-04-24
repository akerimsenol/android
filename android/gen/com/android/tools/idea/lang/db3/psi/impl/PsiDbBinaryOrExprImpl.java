// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.db3.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.db3.psi.DbTokenTypes.*;
import com.android.tools.idea.lang.db3.psi.*;

public class PsiDbBinaryOrExprImpl extends PsiDbExprImpl implements PsiDbBinaryOrExpr {

  public PsiDbBinaryOrExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof PsiDbVisitor) ((PsiDbVisitor)visitor).visitBinaryOrExpr(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<PsiDbExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, PsiDbExpr.class);
  }

}
