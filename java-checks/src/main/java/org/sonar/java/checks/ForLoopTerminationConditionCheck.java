/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.BooleanUtils;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.ForStatementTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.StatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.plugins.java.api.tree.UnaryExpressionTree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.List;

@Rule(
  key = "S888",
  priority = Priority.CRITICAL,
  tags = {"bug", "cert", "cwe"})
@BelongsToProfile(title = "Sonar way", priority = Priority.CRITICAL)
public class ForLoopTerminationConditionCheck extends SubscriptionBaseVisitor {

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.FOR_STATEMENT);
  }

  @Override
  public void visitNode(Tree tree) {
    ForStatementTree forStatement = (ForStatementTree) tree;
    ExpressionTree condition = forStatement.condition();
    if (condition == null || !condition.is(Tree.Kind.NOT_EQUAL_TO)) {
      return;
    }
    BinaryExpressionTree inequalityCondition = (BinaryExpressionTree) condition;
    IntAndIdentifierExpression loopVarAndTerminalValue = IntAndIdentifierExpression.of(inequalityCondition);
    if (loopVarAndTerminalValue != null) {
      IdentifierTree loopIdentifier = loopVarAndTerminalValue.identifier;
      int terminationValue = loopVarAndTerminalValue.value;
      Integer initialValue = initialValue(loopIdentifier, forStatement);
      if (initialValue != null && initialValue != terminationValue) {
        checkIncrement(forStatement, loopIdentifier, initialValue < terminationValue);
      }
    }
  }

  private void checkIncrement(ForStatementTree forStatement, IdentifierTree loopIdentifier, boolean positiveIncrement) {
    List<StatementTree> updates = forStatement.update();
    if (updates.isEmpty()) {
      addIssue(forStatement);
    } else if (updates.size() == 1) {
      ExpressionStatementTree statement = (ExpressionStatementTree) updates.get(0);
      StatementTree body = forStatement.statement();
      Boolean isIncrementByOne = isIncrementByOne(statement.expression(), loopIdentifier, positiveIncrement);
      if (BooleanUtils.isFalse(isIncrementByOne) || forBodyUpdatesLoopIdentifier(body, loopIdentifier)) {
        addIssue(forStatement);
      }
    }
  }

  private Boolean isIncrementByOne(ExpressionTree expression, IdentifierTree loopIdentifier, boolean positiveIncrement) {
    Boolean result = false;
    if (expression.is(Tree.Kind.POSTFIX_INCREMENT, Tree.Kind.PREFIX_INCREMENT)) {
      result = isValidIncrementUpdate(loopIdentifier, (UnaryExpressionTree) expression, positiveIncrement);
    } else if (expression.is(Tree.Kind.POSTFIX_DECREMENT, Tree.Kind.PREFIX_DECREMENT)) {
      result = isValidIncrementUpdate(loopIdentifier, (UnaryExpressionTree) expression, !positiveIncrement);
    } else if (expression.is(Tree.Kind.PLUS_ASSIGNMENT)) {
      result = isValidAssignmentUpdate(loopIdentifier, (AssignmentExpressionTree) expression, positiveIncrement);
    } else if (expression.is(Tree.Kind.MINUS_ASSIGNMENT)) {
      result = isValidAssignmentUpdate(loopIdentifier, (AssignmentExpressionTree) expression, !positiveIncrement);
    } else if (expression.is(Tree.Kind.ASSIGNMENT)) {
      result = isValidSimpleAssignmentUpdate(loopIdentifier, (AssignmentExpressionTree) expression, positiveIncrement);
    }
    return result;
  }

  private Boolean isValidSimpleAssignmentUpdate(IdentifierTree loopIdentifier, AssignmentExpressionTree assignmentExpression, boolean positiveIncrement) {
    if (isSameIdentifier(assignmentExpression.variable(), loopIdentifier)) {
      ExpressionTree expression = assignmentExpression.expression();
      if (expression.is(Tree.Kind.PLUS, Tree.Kind.MINUS)) {
        Integer otherOperandValue = otherOperandValue((BinaryExpressionTree) expression, loopIdentifier);
        if (otherOperandValue == null) {
          return null;
        }
        boolean isPlus = expression.is(Tree.Kind.PLUS);
        return (positiveIncrement ? isPlus : !isPlus) && otherOperandValue == 1;
      }
    }
    return false;
  }

  private Boolean isValidAssignmentUpdate(IdentifierTree loopIdentifier, AssignmentExpressionTree assignmentExpression, boolean positiveIncrement) {
    ExpressionTree assignedValue = assignmentExpression.expression();
    if (isSameIdentifier(assignmentExpression.variable(), loopIdentifier)) {
      Integer intAssignedValue = intLiteralValue(assignedValue);
      if (intAssignedValue != null) {
        return intAssignedValue == (positiveIncrement ? 1 : -1);
      }
      return null;
    }
    return false;
  }

  private Boolean isValidIncrementUpdate(IdentifierTree loopIdentifier, UnaryExpressionTree unaryExp, boolean isExpectedIncrement) {
    if (isSameIdentifier(unaryExp.expression(), loopIdentifier)) {
      return isExpectedIncrement;
    }
    return false;
  }

  private boolean isSameIdentifier(ExpressionTree expression, IdentifierTree identifier) {
    return expression.is(Tree.Kind.IDENTIFIER) && ((IdentifierTree) expression).name().equals(identifier.name());
  }

  private void addIssue(Tree tree) {
    addIssue(tree, "Replace '!=' operator with one of '<=', '>=', '<', or '>' comparison operators.");
  }

  private boolean forBodyUpdatesLoopIdentifier(StatementTree body, IdentifierTree loopIdentifier) {
    LoopVariableAssignmentVisitor visitor = new LoopVariableAssignmentVisitor(loopIdentifier);
    body.accept(visitor);
    return visitor.foundAssignment;
  }

  private class LoopVariableAssignmentVisitor extends BaseTreeVisitor {

    private final IdentifierTree loopIdentifier;
    private boolean foundAssignment = false;

    public LoopVariableAssignmentVisitor(IdentifierTree loopIdentifier) {
      this.loopIdentifier = loopIdentifier;
    }

    @Override
    public void visitUnaryExpression(UnaryExpressionTree unaryExp) {
      if (isSameIdentifier(unaryExp.expression(), loopIdentifier)
        && unaryExp.is(Tree.Kind.POSTFIX_INCREMENT, Tree.Kind.POSTFIX_DECREMENT, Tree.Kind.PREFIX_INCREMENT, Tree.Kind.PREFIX_DECREMENT)) {
        foundAssignment = true;
      }
      super.visitUnaryExpression(unaryExp);
    }

    @Override
    public void visitAssignmentExpression(AssignmentExpressionTree assignmentExpression) {
      if (isSameIdentifier(assignmentExpression.variable(), loopIdentifier)) {
        foundAssignment = true;
      }
      super.visitAssignmentExpression(assignmentExpression);
    }
  }

  private Integer otherOperandValue(BinaryExpressionTree binaryExp, IdentifierTree loopIdentifier) {
    IntAndIdentifierExpression intAndIdentifierExpression = IntAndIdentifierExpression.of(binaryExp);
    if (intAndIdentifierExpression != null && isSameIdentifier(intAndIdentifierExpression.identifier, loopIdentifier)) {
      return intAndIdentifierExpression.value;
    }
    return null;
  }

  private Integer initialValue(IdentifierTree loopIdentifier, ForStatementTree forStatement) {
    Integer value = null;
    for (StatementTree statement : forStatement.initializer()) {
      if (statement.is(Tree.Kind.VARIABLE)) {
        VariableTree variable = (VariableTree) statement;
        ExpressionTree initializer = variable.initializer();
        if (isSameIdentifier(variable.simpleName(), loopIdentifier) && initializer != null) {
          value = intLiteralValue(initializer);
        }
      }
      if (statement.is(Tree.Kind.EXPRESSION_STATEMENT)) {
        ExpressionTree expression = ((ExpressionStatementTree) statement).expression();
        AssignmentExpressionTree assignment = assignment(expression, loopIdentifier);
        if (assignment != null) {
          value = intLiteralValue(assignment.expression());
        }
      }
    }
    return value;
  }

  private AssignmentExpressionTree assignment(ExpressionTree expression, IdentifierTree identifier) {
    if (expression.is(Tree.Kind.ASSIGNMENT)) {
      AssignmentExpressionTree assignment = (AssignmentExpressionTree) expression;
      if (isSameIdentifier(assignment.variable(), identifier)) {
        return assignment;
      }
    }
    return null;
  }

  private static Integer intLiteralValue(LiteralTree literal) {
    return Integer.valueOf(literal.value());
  }

  private static Integer intLiteralValue(ExpressionTree expression) {
    if (expression.is(Tree.Kind.INT_LITERAL)) {
      return intLiteralValue((LiteralTree) expression);
    }
    if (expression.is(Tree.Kind.UNARY_MINUS, Tree.Kind.UNARY_PLUS)) {
      UnaryExpressionTree unaryExp = (UnaryExpressionTree) expression;
      ExpressionTree subExpression = unaryExp.expression();
      if (subExpression.is(Tree.Kind.INT_LITERAL)) {
        Integer subExpressionValue = intLiteralValue((LiteralTree) subExpression);
        return expression.is(Tree.Kind.UNARY_MINUS) ? subExpressionValue * -1 : subExpressionValue;
      }
    }
    return null;
  }
  
  private static class IntAndIdentifierExpression {
    
    private final IdentifierTree identifier;
    private final int value;
    
    private IntAndIdentifierExpression(IdentifierTree identifier, int value) {
      this.identifier = identifier;
      this.value = value;
    }
    
    public static IntAndIdentifierExpression of(BinaryExpressionTree binaryExp) {
      Integer value = null;
      IdentifierTree identifier = null;
      for (ExpressionTree expressionTree : ImmutableList.of(binaryExp.leftOperand(), binaryExp.rightOperand())) {
        if (expressionTree.is(Tree.Kind.IDENTIFIER)) {
          identifier = (IdentifierTree) expressionTree;
        } else {
          value = intLiteralValue(expressionTree);
        }
      }
      if (identifier != null && value != null) {
        return new IntAndIdentifierExpression(identifier, value);
      }
      return null;
    }
  }

}
