package org.spockframework.compiler;

import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.spockframework.compiler.model.Block;
import org.spockframework.compiler.model.ThenBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class AbstractDeepBlockRewriter extends StatementReplacingVisitorSupport {
  protected Block block;
  protected Statement currTopLevelStat;
  protected ExpressionStatement currExprStat;
  protected BinaryExpression currBinaryExpr;
  protected MethodCallExpression currMethodCallExpr;
  protected ClosureExpression currClosure;
  protected IBuiltInMethodCall currBuiltInMethodCall = NullObjectMethodCall.INSTANCE;
  protected Statement lastBuiltInStat;

  // following fields are filled in by subclasses
  protected boolean conditionFound;
  protected boolean interactionFound;
  protected MethodCallExpression foundExceptionCondition;
  protected final List<Statement> thenBlockInteractionStats = new ArrayList<Statement>();

  public AbstractDeepBlockRewriter(Block block) {
    this.block = block;
  }

  public boolean isConditionFound() {
    return conditionFound;
  }

  public boolean isExceptionConditionFound() {
    return foundExceptionCondition != null;
  }

  public List<Statement> getThenBlockInteractionStats() {
    return thenBlockInteractionStats;
  }

  public MethodCallExpression getFoundExceptionCondition() {
    return foundExceptionCondition;
  }

  public void visit(Block block) {
    this.block = block;
    ListIterator<Statement> iterator = block.getAst().listIterator();
    while (iterator.hasNext()) {
      Statement next = iterator.next();
      currTopLevelStat = next;
      Statement replaced = replace(next);
      if (interactionFound && block instanceof ThenBlock) {
        iterator.remove();
        thenBlockInteractionStats.add(replaced);
        interactionFound = false;
      } else {
        iterator.set(replaced);
      }
    }
  }

  @Override
  public final void visitExpressionStatement(ExpressionStatement stat) {
    ExpressionStatement oldExpressionStatement = currExprStat;
    currExprStat = stat;
    try {
      doVisitExpressionStatement(stat);
    } finally {
      currExprStat = oldExpressionStatement;
    }
  }

  @Override
  public final void visitBinaryExpression(BinaryExpression expr) {
    BinaryExpression oldBinaryExpression = currBinaryExpr;
    currBinaryExpr = expr;
    try {
      doVisitBinaryExpression(expr);
    } finally {
      currBinaryExpr = oldBinaryExpression;
    }
  }

  @Override
  public final void visitMethodCallExpression(MethodCallExpression expr) {
    MethodCallExpression oldMethodCallExpr = currMethodCallExpr;
    currMethodCallExpr = expr;

    IBuiltInMethodCall oldBuiltInMethodCall = currBuiltInMethodCall;
    IBuiltInMethodCall newBuiltInMethodCall = DefaultBuiltInMethodCall.parse(currMethodCallExpr, currBinaryExpr);
    if (newBuiltInMethodCall != null) {
      currBuiltInMethodCall = newBuiltInMethodCall;
      if (newBuiltInMethodCall.isMatch(currExprStat)) {
        lastBuiltInStat = currExprStat;
      }
    }

    try {
      doVisitMethodCallExpression(expr);
    } finally {
      currMethodCallExpr = oldMethodCallExpr;
      currBuiltInMethodCall = oldBuiltInMethodCall;
    }
  }

  @Override
  public final void visitClosureExpression(ClosureExpression expr) {
    ClosureExpression oldClosure = currClosure;
    currClosure = expr;
    boolean oldConditionFound = conditionFound;
    conditionFound = false; // any closure terminates conditionFound scope
    IBuiltInMethodCall oldBuiltInMethodCall = currBuiltInMethodCall;
    if (!currBuiltInMethodCall.isMatch(expr)) {
      currBuiltInMethodCall = NullObjectMethodCall.INSTANCE; // unrelated closure terminates currBuiltInMethodCall scope
    }
    try {
      doVisitClosureExpression(expr);
    } finally {
      currClosure = oldClosure;
      conditionFound = oldConditionFound;
      currBuiltInMethodCall = oldBuiltInMethodCall;
    }
  }

  protected void doVisitExpressionStatement(ExpressionStatement stat) {
    super.visitExpressionStatement(stat);
  }

  protected void doVisitBinaryExpression(BinaryExpression expr) {
    super.visitBinaryExpression(expr);
  }

  protected void doVisitMethodCallExpression(MethodCallExpression expr) {
    super.visitMethodCallExpression(expr);
  }

  protected void doVisitClosureExpression(ClosureExpression expr) {
    super.visitClosureExpression(expr);
  }
}
