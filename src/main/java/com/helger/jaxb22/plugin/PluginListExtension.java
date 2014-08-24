/**
 * Copyright (C) 2006-2014 phloc systems (www.phloc.com)
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
import java.util.Locale;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotations.IsSPIImplementation;
import com.helger.commons.collections.ContainerHelper;
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
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Extend all bean List<?> getters with additional method to query the content:
 * boolean has...Entries(), boolean hasNo...Entries(), int get...Count() and T
 * get...AtIndex(int)
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginListExtension extends Plugin
{
  private static final String OPT = "Xph-list-extension";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + " :  add additional methods for list types";
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

    // For all classes
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;

      for (final JFieldVar aField : jClass.fields ().values ())
      {
        final JType aType = aField.type ();
        if (aType.name ().startsWith ("List<"))
        {
          String sName = aField.name ();
          if (Character.isLowerCase (sName.charAt (0)))
            sName = sName.substring (0, 1).toUpperCase (Locale.US) + sName.substring (1);
          final JMethod aSetter = jClass.method (JMod.PUBLIC, aCodeModel.VOID, "set" + sName);
          final JVar aParam = aSetter.param (JMod.FINAL, aType, "aList");
          aParam.annotate (Nullable.class);
          aSetter.body ().assign (aField, aParam);
          aSetter.javadoc ().addParam (aParam).add ("The new list member to set. May be <code>null</code>.");
          aSetter.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
        }
      }

      for (final JMethod aMethod : ContainerHelper.newList (jClass.methods ()))
        if (aMethod.name ().startsWith ("get") && aMethod.params ().isEmpty ())
        {
          final JType aReturnType = aMethod.type ();
          // Find e.g. List<ItemListType> getItemList()
          if (aReturnType.name ().startsWith ("List<"))
          {
            final JType aListElementType = ((JClass) aReturnType).getTypeParameters ().get (0);

            {
              final JMethod mHasEntries = jClass.method (JMod.PUBLIC,
                                                         aCodeModel.BOOLEAN,
                                                         "has" + aMethod.name ().substring (3) + "Entries");
              mHasEntries.body ()._return (JOp.not (JExpr.invoke (aMethod).invoke ("isEmpty")));

              mHasEntries.javadoc ()
                         .addReturn ()
                         .add ("<code>true</code> if at least one item is contained, <code>false</code> otherwise.");
              mHasEntries.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            {
              final JMethod mHasNoEntries = jClass.method (JMod.PUBLIC,
                                                           aCodeModel.BOOLEAN,
                                                           "hasNo" + aMethod.name ().substring (3) + "Entries");
              mHasNoEntries.body ()._return (JExpr.invoke (aMethod).invoke ("isEmpty"));

              mHasNoEntries.javadoc ()
                           .addReturn ()
                           .add ("<code>true</code> if no item is contained, <code>false</code> otherwise.");
              mHasNoEntries.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            {
              final JMethod mCount = jClass.method (JMod.PUBLIC, aCodeModel.INT, aMethod.name () + "Count");
              mCount.annotate (Nonnegative.class);
              mCount.body ()._return (JExpr.invoke (aMethod).invoke ("size"));

              mCount.javadoc ().addReturn ().add ("The number of contained elements. Always &ge; 0.");
              mCount.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }

            {
              final JMethod mAtIndex = jClass.method (JMod.PUBLIC,
                                                      aListElementType,
                                                      "get" + aMethod.name ().substring (3) + "AtIndex");
              mAtIndex.annotate (Nullable.class);
              final JVar aParam = mAtIndex.param (JMod.FINAL, aCodeModel.INT, "index");
              aParam.annotate (Nonnegative.class);
              mAtIndex.body ()._return (JExpr.invoke (aMethod).invoke ("get").arg (aParam));

              mAtIndex.javadoc ().addParam (aParam).add ("The index to retrieve");
              mAtIndex.javadoc ().addReturn ().add ("The element at the specified index. May be <code>null</code>");
              mAtIndex.javadoc ().addThrows (ArrayIndexOutOfBoundsException.class).add ("if the index is invalid!");
              mAtIndex.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
            }
          }
        }
    }
    return true;
  }
}
