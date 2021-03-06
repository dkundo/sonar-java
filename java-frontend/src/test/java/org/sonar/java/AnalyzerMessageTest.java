/*
 * SonarQube Java
 * Copyright (C) 2012-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java;

import org.assertj.core.api.Fail;
import org.junit.Test;
import org.sonar.java.AnalyzerMessage.TextSpan;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.plugins.java.api.JavaCheck;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class AnalyzerMessageTest {

  @Test
  public void testAnalyzerMessage() {
    JavaCheck javaCheck = mock(JavaCheck.class);
    File file = new File("a");
    int line = 5;
    String message = "analyzer message";
    int cost = 3;
    AnalyzerMessage analyzerMessage = new AnalyzerMessage(javaCheck, file, line, message, cost);
    assertThat(analyzerMessage.getCheck()).isEqualTo(javaCheck);
    assertThat(analyzerMessage.getFile()).isEqualTo(file);
    assertThat(analyzerMessage.getLine()).isEqualTo(line);
    assertThat(analyzerMessage.getMessage()).isEqualTo(message);
    assertThat(analyzerMessage.getCost()).isEqualTo(cost);

    AnalyzerMessage.TextSpan location = analyzerMessage.primaryLocation();
    assertThat(location.startLine).isEqualTo(line);
    assertThat(location.startCharacter).isEqualTo(-1);
    assertThat(location.endLine).isEqualTo(line);
    assertThat(location.endCharacter).isEqualTo(-1);
    assertThat(location.isEmpty()).isTrue();
    assertThat(location.toString()).isEqualTo("(5:-1)-(5:-1)");
  }

  @Test
  public void testAnalyzerMessageOnFile2() {
    JavaCheck javaCheck = mock(JavaCheck.class);
    File file = new File("a");
    String message = "analyzer message";
    int cost = 3;
    AnalyzerMessage analyzerMessage = new AnalyzerMessage(javaCheck, file, -5, message, cost);
    assertThat(analyzerMessage.getCheck()).isEqualTo(javaCheck);
    assertThat(analyzerMessage.getFile()).isEqualTo(file);
    assertThat(analyzerMessage.getLine()).isEqualTo(null);
    assertThat(analyzerMessage.getMessage()).isEqualTo(message);
    assertThat(analyzerMessage.getCost()).isEqualTo(cost);
    assertThat(analyzerMessage.primaryLocation()).isNull();
  }

  @Test
  public void emptyTextSpan() {
    // same line, same offset
    assertThat(new AnalyzerMessage.TextSpan(42, 2, 42, 2).isEmpty()).isTrue();
    // different offset
    assertThat(new AnalyzerMessage.TextSpan(42, 2, 42, 5).isEmpty()).isFalse();
    // different lines, different offset
    assertThat(new AnalyzerMessage.TextSpan(42, 2, 43, 5).isEmpty()).isFalse();
    // different lines, same offset
    assertThat(new AnalyzerMessage.TextSpan(42, 2, 43, 2).isEmpty()).isFalse();
  }

  @Test
  public void textSpanOnLine() {
    assertThat(new AnalyzerMessage.TextSpan(42).onLine()).isTrue();
    assertThat(new AnalyzerMessage.TextSpan(0, -1, 0, 5).onLine()).isTrue();
    assertThat(new AnalyzerMessage.TextSpan(0, 2, 0, 2).onLine()).isFalse();
  }

  @Test
  public void textSpanForTrees() {
    CompilationUnitTree cut = (CompilationUnitTree) JavaParser.createParser(StandardCharsets.UTF_8).parse("class A {\n}\n");
    ClassTree classTree = (ClassTree) cut.types().get(0);

    TextSpan textSpan;

    textSpan = AnalyzerMessage.textSpanFor(classTree);
    assertThat(textSpan.startLine).isEqualTo(1);
    assertThat(textSpan.startCharacter).isEqualTo(0);
    assertThat(textSpan.endLine).isEqualTo(2);
    assertThat(textSpan.endCharacter).isEqualTo(1);

    textSpan = AnalyzerMessage.textSpanBetween(classTree.declarationKeyword(), classTree.openBraceToken());
    assertThat(textSpan.startLine).isEqualTo(1);
    assertThat(textSpan.startCharacter).isEqualTo(0);
    assertThat(textSpan.endLine).isEqualTo(1);
    assertThat(textSpan.endCharacter).isEqualTo(9);
  }

  @Test
  public void shouldFailOnEmptyTrees() {
    CompilationUnitTree cut = (CompilationUnitTree) JavaParser.createParser(StandardCharsets.UTF_8)
      .parse("class A {\n}\n");

    try {
      AnalyzerMessage.textSpanFor(cut.eofToken());
      Fail.fail("Should have failed on empty issue location");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
      assertThat(e.getMessage()).isEqualTo("Invalid issue location: Text span is empty when trying reporting on (l:3, c:0).");
    }
  }

  @Test
  public void testAnalyzerMessageOnFile() {
    JavaCheck javaCheck = mock(JavaCheck.class);
    File file = new File("a");
    String message = "analyzer message";
    int cost = 3;
    AnalyzerMessage analyzerMessage = new AnalyzerMessage(javaCheck, file, null, message, cost);
    assertThat(analyzerMessage.getCheck()).isEqualTo(javaCheck);
    assertThat(analyzerMessage.getFile()).isEqualTo(file);
    assertThat(analyzerMessage.getLine()).isEqualTo(null);
    assertThat(analyzerMessage.getMessage()).isEqualTo(message);
    assertThat(analyzerMessage.getCost()).isEqualTo(cost);
    assertThat(analyzerMessage.primaryLocation()).isNull();
  }

  @Test
  public void testAnalyzerMessageWithoutCost() {
    JavaCheck javaCheck = mock(JavaCheck.class);
    File file = new File("a");
    String message = "analyzer message";
    int cost = 0;
    AnalyzerMessage analyzerMessage = new AnalyzerMessage(javaCheck, file, null, message, cost);
    assertThat(analyzerMessage.getCheck()).isEqualTo(javaCheck);
    assertThat(analyzerMessage.getFile()).isEqualTo(file);
    assertThat(analyzerMessage.getLine()).isEqualTo(null);
    assertThat(analyzerMessage.getMessage()).isEqualTo(message);
    assertThat(analyzerMessage.getCost()).isNull();
    assertThat(analyzerMessage.primaryLocation()).isNull();
  }

  @Test
  public void toString_test() throws Exception {
    JavaCheck javaCheck = mock(JavaCheck.class);
    File file = new File("file");
    String message = "analyzer message";
    int cost = 0;
    AnalyzerMessage analyzerMessage = new AnalyzerMessage(javaCheck, file, 12, message, cost);
    assertThat(analyzerMessage.toString()).isEqualTo("'analyzer message' in file:12");
    analyzerMessage = new AnalyzerMessage(javaCheck, null, null, null, cost);
    assertThat(analyzerMessage.toString()).isEqualTo("'null' in null:null");
  }
}
