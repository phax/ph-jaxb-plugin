/**
 * Copyright (C) 2014-2019 Philip Helger (www.helger.com)
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.collection.impl.CommonsHashSet;
import com.helger.commons.collection.impl.ICommonsSet;
import com.helger.commons.datetime.OffsetDate;
import com.helger.jaxb22.plugin.cm.MyTernaryOp;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Extend all bean members of type <code>OffsetDate</code>,
 * <code>OffsetTime</code> and <code>OffsetDateTime</code> with method to access
 * them as their <code>Local*</code> pendants.
 *
 * @author Philip Helger
 * @since 2.3.3.2
 */
@IsSPIImplementation
public class PluginOffsetDTExtension extends AbstractPlugin
{
  public static final String OPT = "Xph-offset-dt-extension";
  private static final Logger LOGGER = LoggerFactory.getLogger (PluginOffsetDTExtension.class);

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + " :  add additional methods for Offset* date time types";
  }

  @Nullable
  public static JType getOtherType (@Nonnull final JType aType, @Nonnull final JCodeModel cm)
  {
    switch (aType.name ())
    {
      case "OffsetDate":
        return cm.ref (LocalDate.class);
      case "OffsetTime":
        return cm.ref (LocalTime.class);
      case "OffsetDateTime":
        return cm.ref (LocalDateTime.class);
      // Ignore all others
    }
    return null;
  }

  @Override
  public boolean run (final Outline aOutline, final Options aOpts, final ErrorHandler aErrorHandler)
  {
    LOGGER.info ("Running JAXB plugin -" + getOptionName ());

    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final ICommonsSet <JDefinedClass> aEffectedClasses = new CommonsHashSet <> ();

    // For all classes
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;

      // Find all List members
      for (final JFieldVar aField : jClass.fields ().values ())
      {
        final JType aOldType = aField.type ();
        switch (aOldType.name ())
        {
          case "OffsetDate":
          {
            final JType aNewType = aCodeModel.ref (LocalDate.class);

            // Create Getter
            {
              final JMethod aGetter = jClass.method (JMod.PUBLIC,
                                                     aNewType,
                                                     CJAXB22.getGetterName (aOldType, aField.name ()) + "Local");
              aGetter.annotate (Nullable.class);
              aGetter.body ()
                     ._return (MyTernaryOp.cond (aField.eq (JExpr._null ()),
                                                 JExpr._null (),
                                                 aField.invoke ("toLocalDate")));
              aGetter.javadoc ()
                     .addReturn ()
                     .add ("The LocalDate representation of " + aField.name () + ". May be <code>null</code>.");
              aGetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            // Create Setter
            {
              final JMethod aSetter = jClass.method (JMod.PUBLIC,
                                                     aCodeModel.VOID,
                                                     CJAXB22.getSetterName (aField.name ()));
              final JVar aParam = aSetter.param (JMod.FINAL, aNewType, "aValue");
              aParam.annotate (Nullable.class);
              aSetter.body ()
                     .assign (aField,
                              MyTernaryOp.cond (aParam.eq (JExpr._null ()),
                                                JExpr._null (),
                                                aCodeModel.ref (OffsetDate.class)
                                                          .staticInvoke ("of")
                                                          .arg (aParam)
                                                          .arg (aCodeModel.ref (ZoneOffset.class).staticRef ("UTC"))));
              aSetter.javadoc ().addParam (aParam).add ("The LocalDate to set. May be <code>null</code>.");
              aSetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            aEffectedClasses.add (jClass);
            break;
          }
          case "OffsetTime":
          {
            final JType aNewType = aCodeModel.ref (LocalTime.class);

            // Create Getter
            {
              final JMethod aGetter = jClass.method (JMod.PUBLIC,
                                                     aNewType,
                                                     CJAXB22.getGetterName (aOldType, aField.name ()) + "Local");
              aGetter.annotate (Nullable.class);
              aGetter.body ()
                     ._return (MyTernaryOp.cond (aField.eq (JExpr._null ()),
                                                 JExpr._null (),
                                                 aField.invoke ("toLocalTime")));
              aGetter.javadoc ()
                     .addReturn ()
                     .add ("The LocalTime representation of " + aField.name () + ". May be <code>null</code>.");
              aGetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            // Create Setter
            {
              final JMethod aSetter = jClass.method (JMod.PUBLIC,
                                                     aCodeModel.VOID,
                                                     CJAXB22.getSetterName (aField.name ()));
              final JVar aParam = aSetter.param (JMod.FINAL, aNewType, "aValue");
              aParam.annotate (Nullable.class);
              aSetter.body ()
                     .assign (aField,
                              MyTernaryOp.cond (aParam.eq (JExpr._null ()),
                                                JExpr._null (),
                                                aCodeModel.ref (OffsetTime.class)
                                                          .staticInvoke ("of")
                                                          .arg (aParam)
                                                          .arg (aCodeModel.ref (ZoneOffset.class).staticRef ("UTC"))));
              aSetter.javadoc ().addParam (aParam).add ("The LocalTime to set. May be <code>null</code>.");
              aSetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            aEffectedClasses.add (jClass);
            break;
          }
          case "OffsetDateTime":
          {
            final JType aNewType = aCodeModel.ref (LocalDateTime.class);

            // Create Getter
            {
              final JMethod aGetter = jClass.method (JMod.PUBLIC,
                                                     aNewType,
                                                     CJAXB22.getGetterName (aOldType, aField.name ()) + "Local");
              aGetter.annotate (Nullable.class);
              aGetter.body ()
                     ._return (MyTernaryOp.cond (aField.eq (JExpr._null ()),
                                                 JExpr._null (),
                                                 aField.invoke ("toLocalDateTime")));
              aGetter.javadoc ()
                     .addReturn ()
                     .add ("The LocalDateTime representation of " + aField.name () + ". May be <code>null</code>.");
              aGetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            // Create Setter
            {
              final JMethod aSetter = jClass.method (JMod.PUBLIC,
                                                     aCodeModel.VOID,
                                                     CJAXB22.getSetterName (aField.name ()));
              final JVar aParam = aSetter.param (JMod.FINAL, aNewType, "aValue");
              aParam.annotate (Nullable.class);
              aSetter.body ()
                     .assign (aField,
                              MyTernaryOp.cond (aParam.eq (JExpr._null ()),
                                                JExpr._null (),
                                                aCodeModel.ref (OffsetDateTime.class)
                                                          .staticInvoke ("of")
                                                          .arg (aParam)
                                                          .arg (aCodeModel.ref (ZoneOffset.class).staticRef ("UTC"))));
              aSetter.javadoc ().addParam (aParam).add ("The LocalDateTime to set. May be <code>null</code>.");
              aSetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            aEffectedClasses.add (jClass);
            break;
          }
          // Ignore all other types
        }
      }
    }

    for (final JDefinedClass jClass : aEffectedClasses)
    {
      // General information
      jClass.javadoc ()
            .add ("<p>This class contains methods created by " + CJAXB22.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }

    return true;
  }
}
