/*
 * Copyright (C) 2014-2025 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.jaxb.plugin.cm;

import org.jspecify.annotations.NonNull;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JExpressionImpl;
import com.sun.codemodel.JFormatter;

public class MyTernaryOp extends JExpressionImpl
{
  private final String m_op1;
  private final String m_op2;
  private final JExpression m_e1;
  private final JExpression m_e2;
  private final JExpression m_e3;

  protected MyTernaryOp (final String op1,
                         final String op2,
                         final JExpression e1,
                         final JExpression e2,
                         final JExpression e3)
  {
    m_e1 = e1;
    m_op1 = op1;
    m_e2 = e2;
    m_op2 = op2;
    m_e3 = e3;
  }

  public void generate (final JFormatter f)
  {
    f.g (m_e1).p (m_op1).g (m_e2).p (m_op2).g (m_e3);
  }

  @NonNull
  public static MyTernaryOp cond (final JExpression e1, final JExpression e2, final JExpression e3)
  {
    return new MyTernaryOp ("?", ":", e1, e2, e3);
  }
}
