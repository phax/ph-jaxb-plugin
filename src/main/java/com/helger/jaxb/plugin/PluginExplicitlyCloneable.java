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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.xml.sax.ErrorHandler;

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.lang.IExplicitlyCloneable;
import com.helger.collection.commons.ICommonsMap;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Add <code>getClone()</code> method based on {@link IExplicitlyCloneable} interface.
 *
 * @author Philip Helger
 * @since 2.2.11.12
 */
@IsSPIImplementation
public class PluginExplicitlyCloneable extends AbstractPluginCloneable
{
  public static final String OPT = "Xph-cloneable2";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + "    :  implement clone() of IExplicitlyCloneable interface and cloneTo(target)";
  }

  @Override
  public boolean run (@NonNull final Outline aOutline,
                      @NonNull final Options aOpts,
                      @NonNull final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final JClass jObject = aCodeModel.ref (Object.class);
    final JClass jExplicitlyCloneable = aCodeModel.ref (IExplicitlyCloneable.class);
    final JClass jArrayList = aCodeModel.ref (ArrayList.class);
    final JClass jHashMap = aCodeModel.ref (HashMap.class);

    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;

      final boolean bIsRoot = jClass._extends () == null || jClass._extends ().equals (jObject);

      if (bIsRoot)
      {
        // Implement Cloneable
        jClass._implements (jExplicitlyCloneable);
      }

      final ICommonsMap <JFieldVar, String> aAllFields = getAllInstanceFields (aClassOutline);

      // cloneTo
      JMethod mCloneTo;
      {
        mCloneTo = jClass.method (JMod.PUBLIC, aCodeModel.VOID, "cloneTo");
        // No @Override because parameter types are different in the class
        // hierarchy
        mCloneTo.javadoc ()
                .add ("This method clones all values from <code>this</code> to the passed object. All data in the parameter object is overwritten!");

        final JVar jRet = mCloneTo.param (jClass, "ret");
        jRet.annotate (NonNull.class);
        mCloneTo.javadoc ().addParam (jRet).add ("The target object to clone to. May not be <code>null</code>.");

        // Call from super class as well
        if (!bIsRoot)
          mCloneTo.body ().add (JExpr._super ().invoke (mCloneTo).arg (jRet));

        for (final Map.Entry <JFieldVar, String> aEntry : aAllFields.entrySet ())
        {
          final JFieldVar aField = aEntry.getKey ();

          if (aField.type ().erasure ().name ().equals ("List"))
          {
            // List
            final JClass aTypeParam = ((JClass) aField.type ()).getTypeParameters ().get (0);

            // if (x == null)
            // ret.x = null;
            final JConditional aIf = mCloneTo.body ()._if (aField.eq (JExpr._null ()));
            aIf._then ().assign (jRet.ref (aField), JExpr._null ());

            // else
            {
              final JBlock aJElse = aIf._else ();

              // Ensure list is created :)
              final JVar aTargetList = aJElse.decl (aField.type (),
                                                    "ret" + aEntry.getValue (),
                                                    JExpr._new (jArrayList.narrow (aTypeParam)));

              // for (X aItem : getX())
              final String sGetter = CJAXB.getGetterName (aField.type (), aEntry.getValue ());
              final JForEach jForEach = aJElse.forEach (aTypeParam, "aItem", JExpr.invoke (sGetter));
              // aTargetList.add (_cloneOf_ (aItem))
              jForEach.body ()
                      .add (aTargetList.invoke ("add").arg (_getCloneCode (aCodeModel, jForEach.var (), aTypeParam)));
              aJElse.assign (jRet.ref (aField), aTargetList);
            }
          }
          else
            if (aField.type ().erasure ().name ().equals ("Map"))
            {
              // Map (for xs:anyAttribute/> - Map<QName,String>)
              // has no setter - need to assign directly!
              final JConditional aIf = mCloneTo.body ()._if (aField.eq (JExpr._null ()));
              aIf._then ().assign (jRet.ref (aField), JExpr._null ());
              // Use regular HashMap to ensure the same type is created
              final JBlock aElse = aIf._else ();
              aElse.assign (jRet.ref (aField),
                            JExpr._new (jHashMap.narrow (((JClass) aField.type ()).getTypeParameters ())).arg (aField));
            }
            else
            {
              mCloneTo.body ().assign (jRet.ref (aField), _getCloneCode (aCodeModel, aField, aField.type ()));
            }
        }

        mCloneTo.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
      }

      // Cannot instantiate abstract classes
      if (jClass.isAbstract ())
      {
        // Create an abstract clone method
        // clone
        // Do not use "getClone" as this is the name of a JAXB generated method
        // for the XSD Element "Clone" :(
        final JMethod mClone = jClass.method (JMod.PUBLIC | JMod.ABSTRACT, jClass, "clone");
        mClone.annotate (NonNull.class);
        mClone.annotate (ReturnsMutableCopy.class);
        mClone.annotate (Override.class);

        mClone.javadoc ().addReturn ().add ("The cloned object. Never <code>null</code>.");

        mClone.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
      }
      else
      {
        // clone
        // Do not use "getClone" as this is the name of a JAXB generated method
        // for the XSD Element "Clone" :(
        final JMethod mClone = jClass.method (JMod.PUBLIC, jClass, "clone");
        mClone.annotate (NonNull.class);
        mClone.annotate (ReturnsMutableCopy.class);
        mClone.annotate (Override.class);

        mClone.javadoc ().addReturn ().add ("The cloned object. Never <code>null</code>.");

        final JVar jRet = mClone.body ().decl (jClass, "ret", JExpr._new (jClass));
        mClone.body ().invoke (mCloneTo).arg (jRet);
        mClone.body ()._return (jRet);

        mClone.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
      }

      // General information
      jClass.javadoc ().add ("<p>This class contains methods created by " + CJAXB.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }
    return true;
  }
}
