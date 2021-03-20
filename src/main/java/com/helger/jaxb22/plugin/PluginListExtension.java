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

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.CommonsHashSet;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.collection.impl.ICommonsSet;
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
 * Extend all bean List&lt;?&gt; getters with additional method to query the
 * content:
 * <ul>
 * <li>void set...(List)</li>
 * <li>boolean has...Entries()</li>
 * <li>boolean hasNo...Entries()</li>
 * <li>int get...Count()</li>
 * <li>T get...AtIndex(int)</li>
 * <li>void add...(T)</li>
 * </ul>
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginListExtension extends AbstractPlugin
{
  public static final String OPT = "Xph-list-extension";
  private static final Logger LOGGER = LoggerFactory.getLogger (PluginListExtension.class);

  /**
   * Does not work because upon reading the object gets filled with a regular
   * java.util.ArrayList!
   */
  private static final boolean USE_COMMONS_LIST = Boolean.FALSE.booleanValue ();

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
            final JMethod aSetter = jClass.method (JMod.PUBLIC, aCodeModel.VOID, CJAXB22.getSetterName (sFieldName));
            final JVar aParam = aSetter.param (JMod.FINAL, USE_COMMONS_LIST ? aNewType : aField.type (), "aList");
            aParam.annotate (Nullable.class);
            aSetter.body ().assign (aField, aParam);
            aSetter.javadoc ().addParam (aParam).add ("The new list member to set. May be <code>null</code>.");
            aSetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
          }

          // Create a new getter
          if (USE_COMMONS_LIST)
          {
            final JMethod aOldGetter = jClass.getMethod (CJAXB22.getGetterName (aField.type (), sFieldName),
                                                         new JType [0]);
            jClass.methods ().remove (aOldGetter);
            final JMethod aNewGetter = jClass.method (JMod.PUBLIC, aNewType, aOldGetter.name ());
            aNewGetter.annotate (Nonnull.class);
            aNewGetter.annotate (ReturnsMutableObject.class).param ("value", "JAXB style");
            final JVar aJRet = aNewGetter.body ().decl (aNewType, "ret", JExpr.cast (aNewType, aField));
            aNewGetter.body ()
                      ._if (aJRet.eq (JExpr._null ()))
                      ._then ()
                      .assign (aField,
                               aJRet.assign (JExpr._new (aCodeModel.ref (CommonsArrayList.class)
                                                                   .narrow (((JClass) aOldType).getTypeParameters ()))));
            aNewGetter.body ()._return (aJRet);
            aNewGetter.javadoc ().addReturn ().add ("The mutable list and never <code>null</code>");
            aNewGetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
          }

          aEffectedClasses.add (jClass);
        }
      }

      for (final JMethod aMethod : CollectionHelper.newList (jClass.methods ()))
        if (aMethod.name ().startsWith ("get") && aMethod.params ().isEmpty ())
        {
          final JType aReturnType = aMethod.type ();
          // Find e.g. List<ItemListType> getItemList()
          if (aReturnType.erasure ().name ().equals (USE_COMMONS_LIST ? "ICommonsList" : "List"))
          {
            final String sRelevantTypeName = aMethod.name ().substring (3);
            final JType aListElementType = ((JClass) aReturnType).getTypeParameters ().get (0);

            // boolean hasXXXEntries ()
            {
              final JMethod mHasEntries = jClass.method (JMod.PUBLIC,
                                                         aCodeModel.BOOLEAN,
                                                         "has" + sRelevantTypeName + "Entries");
              if (USE_COMMONS_LIST)
                mHasEntries.body ()._return (JExpr.invoke (aMethod).invoke ("isNotEmpty"));
              else
                mHasEntries.body ()._return (JOp.not (JExpr.invoke (aMethod).invoke ("isEmpty")));

              mHasEntries.javadoc ()
                         .addReturn ()
                         .add ("<code>true</code> if at least one item is contained, <code>false</code> otherwise.");
              mHasEntries.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
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
              mHasNoEntries.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            // int getXXXCount ()
            {
              final JMethod mCount = jClass.method (JMod.PUBLIC, aCodeModel.INT, "get" + sRelevantTypeName + "Count");
              mCount.annotate (Nonnegative.class);
              mCount.body ()._return (JExpr.invoke (aMethod).invoke ("size"));

              mCount.javadoc ().addReturn ().add ("The number of contained elements. Always &ge; 0.");
              mCount.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            // ELEMENTTYPE getXXXAtIndex (int) throws IndexOutOfBoundsException
            {
              final JMethod mAtIndex = jClass.method (JMod.PUBLIC,
                                                      aListElementType,
                                                      "get" + sRelevantTypeName + "AtIndex");
              mAtIndex.annotate (Nullable.class);
              mAtIndex._throws (IndexOutOfBoundsException.class);
              final JVar aParam = mAtIndex.param (JMod.FINAL, aCodeModel.INT, "index");
              aParam.annotate (Nonnegative.class);
              if (USE_COMMONS_LIST)
                mAtIndex.body ()._return (JExpr.invoke (aMethod).invoke ("getAtIndex").arg (aParam));
              else
                mAtIndex.body ()._return (JExpr.invoke (aMethod).invoke ("get").arg (aParam));

              mAtIndex.javadoc ().addParam (aParam).add ("The index to retrieve");
              mAtIndex.javadoc ().addReturn ().add ("The element at the specified index. May be <code>null</code>");
              mAtIndex.javadoc ().addThrows (IndexOutOfBoundsException.class).add ("if the index is invalid!");
              mAtIndex.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            // void addXXX (ELEMENTTYPE)
            {
              final JMethod mAdd = jClass.method (JMod.PUBLIC, aCodeModel.VOID, "add" + sRelevantTypeName);
              final JVar aParam = mAdd.param (JMod.FINAL, aListElementType, "elem");
              aParam.annotate (Nonnull.class);
              mAdd.body ().add (JExpr.invoke (aMethod).invoke ("add").arg (aParam));

              mAdd.javadoc ().addParam (aParam).add ("The element to be added. May not be <code>null</code>.");
              mAdd.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            aEffectedClasses.add (jClass);
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
