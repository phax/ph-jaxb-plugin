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
package com.helger.jaxb.plugin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ErrorHandler;

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.datetime.rt.OffsetDate;
import com.helger.datetime.xml.XMLOffsetDate;
import com.helger.datetime.xml.XMLOffsetDateTime;
import com.helger.datetime.xml.XMLOffsetTime;
import com.helger.jaxb.plugin.cm.MyTernaryOp;
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
 * <code>XMLOffsetDate</code> <code>OffsetTime</code> and
 * <code>OffsetDateTime</code> with method to access them as their
 * <code>Local*</code> pendants.
 *
 * @author Philip Helger
 * @since 2.3.3.2
 */
@IsSPIImplementation
public class PluginOffsetDTExtension extends AbstractPlugin
{
  public static final String OPT = "Xph-offset-dt-extension";

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
  public static JType getSecondaryDataType (@NonNull final JType aType, @NonNull final JCodeModel cm)
  {
    switch (aType.name ())
    {
      case "OffsetDate":
      case "XMLOffsetDate":
        return cm.ref (LocalDate.class);
      case "OffsetTime":
      case "XMLOffsetTime":
        return cm.ref (LocalTime.class);
      case "OffsetDateTime":
      case "XMLOffsetDateTime":
        return cm.ref (LocalDateTime.class);
      // Ignore all others
    }
    return null;
  }

  @Override
  public boolean run (final Outline aOutline, final Options aOpts, final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

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
          case "XMLOffsetDate":
          {
            final JType aNewType = aCodeModel.ref (LocalDate.class);
            // XMLOffsetDate has an optional time zone
            final boolean bIsXML = aOldType.name ().equals ("XMLOffsetDate");

            // Create Getter
            {
              final JMethod aGetter = jClass.method (JMod.PUBLIC,
                                                     aNewType,
                                                     CJAXB.getGetterName (aOldType, aField.name ()) + "Local");
              aGetter.annotate (Nullable.class);
              aGetter.body ()
                     ._return (MyTernaryOp.cond (aField.eq (JExpr._null ()),
                                                 JExpr._null (),
                                                 aField.invoke ("toLocalDate")));
              aGetter.javadoc ()
                     .addReturn ()
                     .add ("The LocalDate representation of " + aField.name () + ". May be <code>null</code>.");
              aGetter.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            // Create Setter
            {
              final JMethod aSetter = jClass.method (JMod.PUBLIC,
                                                     aCodeModel.VOID,
                                                     CJAXB.getSetterName (aField.name ()));
              final JVar aParam = aSetter.param (JMod.FINAL, aNewType, "aValue");
              aParam.annotate (Nullable.class);
              aSetter.body ()
                     .assign (aField,
                              MyTernaryOp.cond (aParam.eq (JExpr._null ()),
                                                JExpr._null (),
                                                bIsXML ? aCodeModel.ref (XMLOffsetDate.class)
                                                                   .staticInvoke ("of")
                                                                   .arg (aParam)
                                                                   .arg (JExpr._null ())
                                                       : aCodeModel.ref (OffsetDate.class)
                                                                   .staticInvoke ("of")
                                                                   .arg (aParam)
                                                                   .arg (aCodeModel.ref (ZoneOffset.class)
                                                                                   .staticRef ("UTC"))));
              aSetter.javadoc ().addParam (aParam).add ("The LocalDate to set. May be <code>null</code>.");
              aSetter.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            aEffectedClasses.add (jClass);
            break;
          }
          case "OffsetTime":
          case "XMLOffsetTime":
          {
            final JType aNewType = aCodeModel.ref (LocalTime.class);
            // XMLOffsetTime has an optional time zone
            final boolean bIsXML = aOldType.name ().equals ("XMLOffsetTime");

            // Create Getter
            {
              final JMethod aGetter = jClass.method (JMod.PUBLIC,
                                                     aNewType,
                                                     CJAXB.getGetterName (aOldType, aField.name ()) + "Local");
              aGetter.annotate (Nullable.class);
              aGetter.body ()
                     ._return (MyTernaryOp.cond (aField.eq (JExpr._null ()),
                                                 JExpr._null (),
                                                 aField.invoke ("toLocalTime")));
              aGetter.javadoc ()
                     .addReturn ()
                     .add ("The LocalTime representation of " + aField.name () + ". May be <code>null</code>.");
              aGetter.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            // Create Setter
            {
              final JMethod aSetter = jClass.method (JMod.PUBLIC,
                                                     aCodeModel.VOID,
                                                     CJAXB.getSetterName (aField.name ()));
              final JVar aParam = aSetter.param (JMod.FINAL, aNewType, "aValue");
              aParam.annotate (Nullable.class);
              aSetter.body ()
                     .assign (aField,
                              MyTernaryOp.cond (aParam.eq (JExpr._null ()),
                                                JExpr._null (),
                                                bIsXML ? aCodeModel.ref (XMLOffsetTime.class)
                                                                   .staticInvoke ("of")
                                                                   .arg (aParam)
                                                                   .arg (JExpr._null ())
                                                       : aCodeModel.ref (OffsetTime.class)
                                                                   .staticInvoke ("of")
                                                                   .arg (aParam)
                                                                   .arg (aCodeModel.ref (ZoneOffset.class)
                                                                                   .staticRef ("UTC"))));
              aSetter.javadoc ().addParam (aParam).add ("The LocalTime to set. May be <code>null</code>.");
              aSetter.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            aEffectedClasses.add (jClass);
            break;
          }
          case "OffsetDateTime":
          case "XMLOffsetDateTime":
          {
            final JType aNewType = aCodeModel.ref (LocalDateTime.class);
            // XMLOffsetDateTime has an optional time zone
            final boolean bIsXML = aOldType.name ().equals ("XMLOffsetDateTime");

            // Create Getter
            {
              final JMethod aGetter = jClass.method (JMod.PUBLIC,
                                                     aNewType,
                                                     CJAXB.getGetterName (aOldType, aField.name ()) + "Local");
              aGetter.annotate (Nullable.class);
              aGetter.body ()
                     ._return (MyTernaryOp.cond (aField.eq (JExpr._null ()),
                                                 JExpr._null (),
                                                 aField.invoke ("toLocalDateTime")));
              aGetter.javadoc ()
                     .addReturn ()
                     .add ("The LocalDateTime representation of " + aField.name () + ". May be <code>null</code>.");
              aGetter.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            // Create Setter
            {
              final JMethod aSetter = jClass.method (JMod.PUBLIC,
                                                     aCodeModel.VOID,
                                                     CJAXB.getSetterName (aField.name ()));
              final JVar aParam = aSetter.param (JMod.FINAL, aNewType, "aValue");
              aParam.annotate (Nullable.class);
              aSetter.body ()
                     .assign (aField,
                              MyTernaryOp.cond (aParam.eq (JExpr._null ()),
                                                JExpr._null (),
                                                bIsXML ? aCodeModel.ref (XMLOffsetDateTime.class)
                                                                   .staticInvoke ("of")
                                                                   .arg (aParam)
                                                                   .arg (JExpr._null ())
                                                       : aCodeModel.ref (OffsetDateTime.class)
                                                                   .staticInvoke ("of")
                                                                   .arg (aParam)
                                                                   .arg (aCodeModel.ref (ZoneOffset.class)
                                                                                   .staticRef ("UTC"))));
              aSetter.javadoc ().addParam (aParam).add ("The LocalDateTime to set. May be <code>null</code>.");
              aSetter.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
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
      jClass.javadoc ().add ("<p>This class contains methods created by " + CJAXB.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }

    return true;
  }
}
