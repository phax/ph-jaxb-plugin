/**
 * Copyright (C) 2014 Philip Helger (www.helger.com)
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
package com.helger.jaxb22.plugin;

import java.util.List;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotations.IsSPIImplementation;
import com.helger.commons.collections.ArrayHelper;
import com.helger.commons.collections.ContainerHelper;
import com.helger.commons.equals.EqualsUtils;
import com.helger.commons.hash.HashCodeGenerator;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Add default equals and hashCode methods. For equals the {@link EqualsUtils}
 * class is used and for hashCode the {@link HashCodeGenerator} class is used.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginEqualsHashCode extends Plugin
{
  private static final String OPT = "Xph-equalshashcode";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" +
           OPT +
           "    :  auto implement equals and hashCode using com.helger.commons.equals.EqualsUtils and com.helger.commons.hash.HashCodeGenerator";
  }

  @Override
  public List <String> getCustomizationURIs ()
  {
    return ContainerHelper.newUnmodifiableList (CJAXB22.NSURI_PH);
  }

  @Override
  public boolean run (final Outline aOutline, final Options aOpts, final ErrorHandler aErrorHandler)
  {
    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final JClass jObject = aCodeModel.ref (Object.class);
    final JClass jEqualsUtils = aCodeModel.ref (EqualsUtils.class);
    final JClass jHashCodeGenerator = aCodeModel.ref (HashCodeGenerator.class);
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final FieldOutline [] aFields = aClassOutline.getDeclaredFields ();
      final JDefinedClass jClass = aClassOutline.implClass;
      final boolean bIsRoot = jClass._extends () == null || jClass._extends ().equals (jObject);

      // equals
      {
        final JMethod mEquals = jClass.method (JMod.PUBLIC, aCodeModel.BOOLEAN, "equals");
        mEquals.annotate (Override.class);
        final JVar param = mEquals.param (JMod.FINAL, aCodeModel.ref (Object.class), "o");
        final JBlock jBody = mEquals.body ();

        if (!bIsRoot && aFields.length == 0)
        {
          // No additional fields -> no need to create code
          jBody._return (JExpr._super ().invoke (mEquals).arg (param));
        }
        else
        {
          // if(o==this)return true;
          jBody._if (param.eq (JExpr._this ()))._then ()._return (JExpr.TRUE);
          if (bIsRoot)
          {
            // if(o==null||!getClass().equals(o.getClass()))return false;
            jBody._if (param.eq (JExpr._null ()).cor (JOp.not (JExpr.invoke ("getClass")
                                                                    .invoke ("equals")
                                                                    .arg (param.invoke ("getClass")))))
                 ._then ()
                 ._return (JExpr.FALSE);
          }
          else
          {
            // if(!super.equals(this))return false;
            jBody._if (JOp.not (JExpr._super ().invoke (mEquals).arg (param)))._then ()._return (JExpr.FALSE);
          }
          if (ArrayHelper.isNotEmpty (aFields))
          {
            // final type rhs = (type)o;
            final JVar jTyped = jBody.decl (JMod.FINAL, jClass, "rhs", JExpr.cast (jClass, param));
            for (final FieldOutline aField : aFields)
            {
              final String sFieldName = aField.getPropertyInfo ().getName (false);
              final JExpression aThisExpr = jEqualsUtils.staticInvoke ("equals")
                                                        .arg (JExpr.ref (sFieldName))
                                                        .arg (jTyped.ref (sFieldName));
              jBody._if (JOp.not (aThisExpr))._then ()._return (JExpr.FALSE);
            }
          }
          jBody._return (JExpr.TRUE);
        }

        mEquals.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
      }

      // hashCode
      {
        final JMethod mHashCode = jClass.method (JMod.PUBLIC, aCodeModel.INT, "hashCode");
        mHashCode.annotate (Override.class);

        if (!bIsRoot && aFields.length == 0)
        {
          // No additional fields -> no need to create code
          mHashCode.body ()._return (JExpr._super ().invoke (mHashCode));
        }
        else
        {
          JInvocation aInvocation;
          if (bIsRoot)
            aInvocation = JExpr._new (jHashCodeGenerator).arg (JExpr._this ());
          else
            aInvocation = jHashCodeGenerator.staticInvoke ("getDerived").arg (JExpr._super ().invoke (mHashCode));
          for (final FieldOutline aField : aFields)
          {
            final String sFieldName = aField.getPropertyInfo ().getName (false);
            aInvocation = aInvocation.invoke ("append").arg (JExpr.ref (sFieldName));
          }
          mHashCode.body ()._return (aInvocation.invoke ("getHashCode"));
        }

        mHashCode.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
      }
    }
    return true;
  }
}
