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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ErrorHandler;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.style.IsSPIImplementation;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Extend all bean List&lt;?&gt; getters with additional method to query the content:
 * <ul>
 * <li>void set...(List)</li>
 * <li>boolean has...Entries()</li>
 * <li>boolean hasNo...Entries()</li>
 * <li>int get...Count()</li>
 * <li>T get...AtIndex(int)</li>
 * <li>void add...(T)</li>
 * </ul>
 * Note: don't use ICommonsList here, because it is not supported in the underlying JAXB
 * implementation, which explicitly checks for ArrayList.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginListExtension extends AbstractPlugin
{
  public static final String OPT = "Xph-list-extension";

  private static final JType [] JTYPE_EMPTY = {};

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + " :  add additional methods for List types";
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
        if (aOldType.erasure ().name ().equals ("List"))
        {
          final JType aNewType = aCodeModel.ref (ICommonsList.class).narrow (((JClass) aOldType).getTypeParameters ());
          if (false)
          {
            // Change type to ICommonsList
            // It's important that the type of the field stays "List" even if it
            // is an ICommonsList in reality! Otherwise XJC cracks!
            aField.type (aNewType);
          }

          // Important for correct casing
          final String sFieldName = aClassOutline.target.getProperty (aField.name ()).getName (true);

          // Create Setter
          {
            final JMethod aSetter = jClass.method (JMod.PUBLIC, aCodeModel.VOID, CJAXB.getSetterName (sFieldName));
            final JVar aParam = aSetter.param (JMod.FINAL, aField.type (), "aList");
            if (allowsJSpecifyAnnotations (jClass, aField.type ()))
              aParam.annotate (Nullable.class);
            aSetter.body ().assign (aField, aParam);
            aSetter.javadoc ().addParam (aParam).add ("The new list member to set. May be <code>null</code>.");
            aSetter.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
          }

          aEffectedClasses.add (jClass);
        }
      }

      // Create a copy of the methods
      for (final JMethod aMethod : new CommonsArrayList <> (jClass.methods ()))
        if (aMethod.name ().startsWith ("get") && aMethod.params ().isEmpty ())
        {
          final JType aReturnType = aMethod.type ();
          // Find e.g. List<ItemListType> getItemList()
          if (aReturnType.erasure ().name ().equals ("List"))
          {
            final String sRelevantTypeName = aMethod.name ().substring (3);
            final JType aListElementType = ((JClass) aReturnType).getTypeParameters ().get (0);

            // boolean hasXXXEntries ()
            {
              final JMethod mHasEntries = jClass.method (JMod.PUBLIC,
                                                         aCodeModel.BOOLEAN,
                                                         "has" + sRelevantTypeName + "Entries");
              mHasEntries.body ()._return (JOp.not (JExpr.invoke (aMethod).invoke ("isEmpty")));

              mHasEntries.javadoc ()
                         .addReturn ()
                         .add ("<code>true</code> if at least one item is contained, <code>false</code> otherwise.");
              mHasEntries.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            // boolean hasNoXXXEntries ()
            {
              final JMethod mHasNoEntries = jClass.method (JMod.PUBLIC,
                                                           aCodeModel.BOOLEAN,
                                                           "hasNo" + sRelevantTypeName + "Entries");
              mHasNoEntries.body ()._return (JExpr.invoke (aMethod).invoke ("isEmpty"));

              mHasNoEntries.javadoc ()
                           .addReturn ()
                           .add ("<code>true</code> if no item is contained, <code>false</code> otherwise.");
              mHasNoEntries.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            // int getXXXCount () or getXXXListCount ()
            {
              String sName = "get" + sRelevantTypeName + "Count";
              if (jClass.getMethod (sName, JTYPE_EMPTY) != null)
                sName = "get" + sRelevantTypeName + "ListCount";

              if (jClass.getMethod (sName, JTYPE_EMPTY) == null)
              {
                final JMethod mCount = jClass.method (JMod.PUBLIC, aCodeModel.INT, sName);
                mCount.annotate (Nonnegative.class);
                mCount.body ()._return (JExpr.invoke (aMethod).invoke ("size"));

                mCount.javadoc ().addReturn ().add ("The number of contained elements. Always &ge; 0.");
                mCount.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
              }
              else
                logWarn ("Cannot create 'get" + sRelevantTypeName + "Count' method because it already exists");
            }

            // ELEMENTTYPE getXXXAtIndex (int) throws IndexOutOfBoundsException
            {
              final JMethod mAtIndex = jClass.method (JMod.PUBLIC,
                                                      aListElementType,
                                                      "get" + sRelevantTypeName + "AtIndex");
              if (allowsJSpecifyAnnotations (jClass, aListElementType))
                mAtIndex.annotate (Nullable.class);
              mAtIndex._throws (IndexOutOfBoundsException.class);
              final JVar aParam = mAtIndex.param (JMod.FINAL, aCodeModel.INT, "index");
              aParam.annotate (Nonnegative.class);
              mAtIndex.body ()._return (JExpr.invoke (aMethod).invoke ("get").arg (aParam));

              mAtIndex.javadoc ().addParam (aParam).add ("The index to retrieve");
              mAtIndex.javadoc ().addReturn ().add ("The element at the specified index. May be <code>null</code>");
              mAtIndex.javadoc ().addThrows (IndexOutOfBoundsException.class).add ("if the index is invalid!");
              mAtIndex.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            // void addXXX (ELEMENTTYPE)
            {
              final JMethod mAdd = jClass.method (JMod.PUBLIC, aCodeModel.VOID, "add" + sRelevantTypeName);
              final JVar aParam = mAdd.param (JMod.FINAL, aListElementType, "elem");
              if (allowsJSpecifyAnnotations (jClass, aListElementType))
                aParam.annotate (NonNull.class);
              mAdd.body ().add (JExpr.invoke (aMethod).invoke ("add").arg (aParam));

              mAdd.javadoc ().addParam (aParam).add ("The element to be added. May not be <code>null</code>.");
              mAdd.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
            }

            aEffectedClasses.add (jClass);
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
