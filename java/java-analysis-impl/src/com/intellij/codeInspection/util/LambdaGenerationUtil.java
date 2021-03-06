/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Utility methods which are helpful to generate new lambda expressions in quick-fixes
 *
 * @author Tagir Valeev
 */
public class LambdaGenerationUtil {
  /**
   * Tests the element (expression or statement) whether it could be converted to the body
   * of lambda expression mapped to functional interface which SAM does not declare any
   * checked exceptions. The following things are checked:
   *
   * <p>1. The expression should not throw checked exceptions</p>
   * <p>2. The expression should not refer any variables which are not effectively final</p>
   * <p>3. No control flow instructions inside which may jump out of the supplied lambdaCandidate</p>
   *
   * @param lambdaCandidate an expression or statement to test
   * @return true if this expression or statement can be converted to lambda
   */
  @Contract("null -> false")
  public static boolean canBeUncheckedLambda(@Nullable PsiElement lambdaCandidate) {
    return canBeUncheckedLambda(lambdaCandidate, var -> false);
  }

  /**
   * Tests the element (expression or statement) whether it could be converted to the body
   * of lambda expression mapped to functional interface which SAM does not declare any
   * checked exceptions. The following things are checked:
   *
   * <p>1. The expression should not throw checked exceptions</p>
   * <p>2. The expression should not refer any variables which are not effectively final
   *       and not allowed by specified predicate</p>
   * <p>3. No control flow instructions inside which may jump out of the supplied lambdaCandidate</p>
   *
   * @param lambdaCandidate an expression or statement to test
   * @param variableAllowedPredicate a predicate which returns true if the variable is allowed to be present inside {@code lambdaCandidate}
   *                even if it's not effectively final (e.g. it will be replaced by something else when moved to lambda)
   * @return true if this expression or statement can be converted to lambda
   */
  @Contract("null, _ -> false")
  public static boolean canBeUncheckedLambda(@Nullable PsiElement lambdaCandidate, @NotNull Predicate<PsiVariable> variableAllowedPredicate) {
    if(!(lambdaCandidate instanceof PsiExpression) && !(lambdaCandidate instanceof PsiStatement)) return false;
    if(!ExceptionUtil.getThrownCheckedExceptions(lambdaCandidate).isEmpty()) return false;
    CanBeLambdaBodyVisitor visitor = new CanBeLambdaBodyVisitor(lambdaCandidate, variableAllowedPredicate);
    lambdaCandidate.accept(visitor);
    return visitor.canBeLambdaBody();
  }

  private static class CanBeLambdaBodyVisitor extends JavaRecursiveElementWalkingVisitor {
    // Throws is not handled here: it's usually not a problem to move "throws <UncheckedException>" inside lambda.
    private boolean myCanBeLambdaBody = true;
    private final PsiElement myRoot;
    private final Predicate<PsiVariable> myVariableAllowedPredicate;

    CanBeLambdaBodyVisitor(PsiElement root, Predicate<PsiVariable> variableAllowedPredicate) {
      myRoot = root;
      myVariableAllowedPredicate = variableAllowedPredicate;
    }

    @Override
    public void visitElement(PsiElement element) {
      if(!myCanBeLambdaBody) return;
      super.visitElement(element);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // do not go down the local/anonymous classes
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      // do not go down the nested lambda expressions
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if(!myCanBeLambdaBody) return;
      super.visitReferenceExpression(expression);
      PsiElement element = expression.resolve();
      if (element instanceof PsiVariable &&
          !(element instanceof PsiField) &&
          !myVariableAllowedPredicate.test((PsiVariable)element) &&
          !PsiTreeUtil.isAncestor(myRoot, element, true) &&
          !HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)element, myRoot, null)) {
        myCanBeLambdaBody = false;
      }
    }

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      PsiStatement exitedStatement = statement.findExitedStatement();
      if(exitedStatement == null || !PsiTreeUtil.isAncestor(myRoot, exitedStatement, false)) {
        myCanBeLambdaBody = false;
      }
      super.visitBreakStatement(statement);
    }

    @Override
    public void visitContinueStatement(PsiContinueStatement statement) {
      PsiStatement continuedStatement = statement.findContinuedStatement();
      if(continuedStatement == null || !PsiTreeUtil.isAncestor(myRoot, continuedStatement, false)) {
        myCanBeLambdaBody = false;
      }
      super.visitContinueStatement(statement);
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      myCanBeLambdaBody = false;
    }

    public boolean canBeLambdaBody() {
      return myCanBeLambdaBody;
    }
  }
}
