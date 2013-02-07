/*
 * Copyright 2013 The authors
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

package com.intellij.lang.ognl.psi;

import com.intellij.lang.ognl.OgnlLanguage;
import com.intellij.lang.ognl.OgnlTypes;
import org.intellij.lang.annotations.Language;

/**
 * {@link OgnlVariableAssignmentExpression}.
 *
 * @author Yann C&eacute;bron
 */
public class VariableAssignmentExpressionPsiTest extends PsiTestCase {

  public void testVariableAssignment() {
    final OgnlVariableAssignmentExpression expression = parse("#varName = 1 + 2");
    final OgnlExpression assignment = expression.getExpression();
    assertElementType(OgnlTypes.BINARY_EXPRESSION, assignment);
  }

  private OgnlVariableAssignmentExpression parse(@Language(value = OgnlLanguage.ID,
                                                           prefix = OgnlLanguage.EXPRESSION_PREFIX,
                                                           suffix = OgnlLanguage.EXPRESSION_SUFFIX) final String expression) {
    return (OgnlVariableAssignmentExpression)parseSingleExpression(expression);
  }
}