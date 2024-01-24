/*
 * Copyright (C) 2014-2024 Philip Helger (www.helger.com)
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
package com.helger.jaxb.plugin;

import javax.annotation.Nonnull;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.collection.impl.ICommonsOrderedMap;
import com.helger.commons.string.ToStringGenerator;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Add default toString method using the {@link ToStringGenerator} class (using
 * getToString method).
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginToString extends AbstractPlugin
{
  public static final String OPT = "Xph-tostring";

  public PluginToString ()
  {}

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + "    :  auto implement toString using com.helger.commons.string.ToStringGenerator";
  }

  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final JClass jObject = aCodeModel.ref (Object.class);
    final JClass jToStringGenerator = aCodeModel.ref (ToStringGenerator.class);
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      final FieldOutline [] aFields = aClassOutline.getDeclaredFields ();
      final boolean bIsRoot = jClass._extends () == null || jClass._extends ().equals (jObject);

      if (!bIsRoot && aFields.length == 0)
      {
        // No additional fields -> no need to create code
        continue;
      }

      final ICommonsOrderedMap <JFieldVar, String> aFieldVars = getAllInstanceFields (aClassOutline);

      // toString
      {
        final JMethod mToString = jClass.method (JMod.PUBLIC, aCodeModel.ref (String.class), "toString");
        mToString.annotate (Override.class);

        JInvocation aInvocation;
        if (bIsRoot)
          aInvocation = JExpr._new (jToStringGenerator).arg (JExpr._this ());
        else
          aInvocation = jToStringGenerator.staticInvoke ("getDerived").arg (JExpr._super ().invoke (mToString));

        if (true)
        {
          // Instance fields only
          for (final JFieldVar aField : aFieldVars.keySet ())
          {
            final String sFieldName = aField.name ();
            aInvocation = aInvocation.invoke ("append").arg (JExpr.lit (sFieldName)).arg (JExpr.ref (sFieldName));
          }
        }
        else
        {
          // Does not handle static fields
          for (final FieldOutline aField : aFields)
          {
            final String sFieldName = aField.getPropertyInfo ().getName (false);
            aInvocation = aInvocation.invoke ("append").arg (JExpr.lit (sFieldName)).arg (JExpr.ref (sFieldName));
          }
        }
        mToString.body ()._return (aInvocation.invoke ("getToString"));

        mToString.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
      }

      // General information
      jClass.javadoc ().add ("<p>This class contains methods created by " + CJAXB.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }
    return true;
  }
}
