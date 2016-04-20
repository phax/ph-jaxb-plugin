/**
 * Copyright (C) 2014-2016 Philip Helger (www.helger.com)
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

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.lang.CloneHelper;
import com.helger.commons.lang.ICloneable;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Add <code>getClone()</code> method.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginCloneable extends Plugin
{
  private static final String OPT = "Xph-cloneable";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + "    :  auto implement getClone() of ICloneable interface";
  }

  @Override
  public List <String> getCustomizationURIs ()
  {
    return CollectionHelper.makeUnmodifiable (CJAXB22.NSURI_PH);
  }

  private static boolean _isImmutable (@Nonnull final String sTypeName)
  {
    return sTypeName.equals ("String") ||
           sTypeName.equals ("Boolean") ||
           sTypeName.equals ("Byte") ||
           sTypeName.equals ("Character") ||
           sTypeName.equals ("Double") ||
           sTypeName.equals ("Float") ||
           sTypeName.equals ("Integer") ||
           sTypeName.equals ("Long") ||
           sTypeName.equals ("Short");
  }

  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final JClass jICloneable = aCodeModel.ref (ICloneable.class);
    final JClass jCollectionHelper = aCodeModel.ref (CollectionHelper.class);
    final JClass jCloneHelper = aCodeModel.ref (CloneHelper.class);

    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      jClass._implements (jICloneable);

      // getClone
      final JMethod mGetClone = jClass.method (JMod.PUBLIC, jClass, "getClone");
      mGetClone.annotate (Nonnull.class);
      mGetClone.annotate (ReturnsMutableCopy.class);

      final JVar jRet = mGetClone.body ().decl (jClass, "ret", JExpr._new (jClass));
      final Collection <JFieldVar> aFields = CollectionHelper.getSortedByKey (jClass.fields ()).values ();
      for (final JFieldVar aField : aFields)
      {
        final String sFieldName = aField.name ();

        final String sGetter = CJAXB22.getGetterName (sFieldName);
        final String sSetter = CJAXB22.getSetterName (sFieldName);

        if (aField.type ().name ().equals ("List"))
        {
          // List
          final JTypeVar aTypeParam = ((JClass) aField.type ()).typeParams ()[0];
          if (_isImmutable (aTypeParam.name ()))
          {
            // Immutable value
            // ret.setX (CollectionHelper.newList (getX ()))
            mGetClone.body ()
                     .add (jRet.invoke (sSetter)
                               .arg (jCollectionHelper.staticInvoke ("newList").arg (JExpr._this ().invoke (sGetter))));
          }
          else
          {
            // Nested cloneable value
            // ret.setX (CloneHelper.getClonedList (getX ().getClone ()));
            mGetClone.body ().add (jRet.invoke (sSetter).arg (jCloneHelper.staticInvoke ("getClonedList")
                                                                          .arg (JExpr._this ().invoke (sGetter))));
          }
        }
        else
        {
          if (aField.type ().isPrimitive () || _isImmutable (aField.type ().name ()))
          {
            // Immutable value
            // ret.setX (getX ())
            mGetClone.body ().add (jRet.invoke (sSetter).arg (JExpr._this ().invoke (sGetter)));
          }
          else
          {
            // Nested cloneable value
            // ret.setX (getX ().getClone ());
            mGetClone.body ().add (jRet.invoke (sSetter).arg (JExpr._this ().invoke (sGetter).invoke ("getClone")));
          }
        }
      }
      mGetClone.body ()._return (jRet);

      mGetClone.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
    }
    return true;
  }
}
