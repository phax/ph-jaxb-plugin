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

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.ArrayHelper;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.ext.CommonsLinkedHashMap;
import com.helger.commons.collection.ext.ICommonsMap;
import com.helger.commons.collection.ext.ICommonsOrderedMap;
import com.helger.commons.lang.CloneHelper;
import com.sun.codemodel.ClassType;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CPropertyInfo;
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

  private static boolean _isImmutable (@Nonnull final JType aType)
  {
    // int, byte, boolean etc?
    if (aType.isPrimitive ())
      return true;

    // Is it an enum?
    if (aType instanceof JDefinedClass)
    {
      final JDefinedClass aClass = (JDefinedClass) aType;
      if (aClass.getClassType () == ClassType.ENUM)
        return true;
    }

    // Check by name :)
    // TODO Element should also be cloned
    final String sTypeName = aType.name ();
    return sTypeName.equals ("Object") ||
           sTypeName.equals ("String") ||
           sTypeName.equals ("BigInteger") ||
           sTypeName.equals ("BigDecimal") ||
           sTypeName.equals ("Element") ||
           sTypeName.equals ("Boolean") ||
           sTypeName.equals ("Byte") ||
           sTypeName.equals ("Character") ||
           sTypeName.equals ("Double") ||
           sTypeName.equals ("Float") ||
           sTypeName.equals ("Integer") ||
           sTypeName.equals ("Long") ||
           sTypeName.equals ("Short");
  }

  private static boolean _isJavaCloneable (@Nonnull final JType aType)
  {
    // Check by name :)
    final String sTypeName = aType.name ();
    return sTypeName.equals ("XMLGregorianCalendar");
  }

  private static boolean _isImmutableArray (@Nonnull final JType aType)
  {
    return aType.isArray () && _isImmutable (aType.elementType ());
  }

  @Nonnull
  @ReturnsMutableCopy
  private static ICommonsMap <JFieldVar, String> _getAllFields (@Nonnull final Outline aOutline,
                                                                @Nonnull final ClassOutline aClassOutline)
  {
    final ICommonsOrderedMap <JFieldVar, String> ret = new CommonsLinkedHashMap<> ();

    // Add all fields of base class
    final JDefinedClass jClass = aClassOutline.implClass;
    final JClass jExtends = jClass._extends ();
    if (jExtends instanceof JDefinedClass)
    {
      for (final ClassOutline aCurClassOutline : aOutline.getClasses ())
        if (aCurClassOutline.implClass == jExtends)
        {
          ret.addAll (_getAllFields (aOutline, aCurClassOutline));
          break;
        }
    }

    // Add fields of this class
    for (final JFieldVar aFieldVar : CollectionHelper.getSortedByKey (jClass.fields ()).values ())
    {
      // Get public name
      final CPropertyInfo aPI = aClassOutline.target.getProperty (aFieldVar.name ());
      if (aPI == null)
      {
        throw new IllegalStateException ("'" +
                                         aFieldVar.name () +
                                         "' not found in " +
                                         CollectionHelper.newListMapped (aClassOutline.target.getProperties (),
                                                                         pi -> pi.getName (false)));
      }
      final String sFieldName = aPI.getName (true);
      ret.put (aFieldVar, sFieldName);
    }

    return ret;
  }

  @Nonnull
  private static JExpression _getCloneCode (final JCodeModel aCodeModel,
                                            final JExpression aGetter,
                                            final JType aTypeParam)
  {
    if (_isImmutable (aTypeParam))
    {
      // Immutable value
      // return aItem
      return aGetter;
    }

    if (_isImmutableArray (aTypeParam))
    {
      // Array of immutable objects
      // ArrayHelper.getCopy (aItem);
      // Method is null-safe
      return aCodeModel.ref (ArrayHelper.class).staticInvoke ("getCopy").arg (aGetter);
    }

    if (_isJavaCloneable (aTypeParam))
    {
      // Nested Java Cloneable value
      // aItem == null ? null : (X) aItem.clone ();
      if (true)
        return JOp.cond (aGetter.eq (JExpr._null ()),
                         JExpr._null (),
                         JExpr.cast (aTypeParam, aGetter.invoke ("clone")));
      return aCodeModel.ref (CloneHelper.class).staticInvoke ("getClonedValue").arg (aGetter);
    }

    if (aTypeParam.erasure ().name ().equals ("JAXBElement"))
    {
      // Array of immutable objects
      // CloneHelper.getClonedJAXBElement (aItem);
      // Method is null-safe
      return aCodeModel.ref (CloneHelper.class).staticInvoke ("getClonedJAXBElement").arg (aGetter);
    }

    // Nested Cloneable value
    // aItem == null ? null : aItem.clone ();
    return JOp.cond (aGetter.eq (JExpr._null ()), JExpr._null (), aGetter.invoke ("clone"));
  }

  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final JClass jObject = aCodeModel.ref (Object.class);
    final JClass jCloneable = aCodeModel.ref (Cloneable.class);

    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      final boolean bIsRoot = jClass._extends () == null || jClass._extends ().equals (jObject);

      if (bIsRoot)
      {
        // Implement Cloneable
        jClass._implements (jCloneable);
      }

      // clone
      // Do not use "getClone" as this is the name of a JAXB generated method
      // for the XSD Element "Clone" :(
      final JMethod mGetClone = jClass.method (JMod.PUBLIC, jClass, "clone");
      mGetClone.annotate (Nonnull.class);
      mGetClone.annotate (ReturnsMutableCopy.class);
      mGetClone.annotate (Override.class);

      final JVar jRet = mGetClone.body ().decl (jClass, "ret", JExpr._new (jClass));
      for (final Map.Entry <JFieldVar, String> aEntry : _getAllFields (aOutline, aClassOutline).entrySet ())
      {
        final JFieldVar aField = aEntry.getKey ();
        // Must use the getter in case this is a derived class. In this case we
        // cannot access the private member of the base class
        final String sGetter = CJAXB22.getGetterName (aField.type (), aEntry.getValue ());
        final String sSetter = CJAXB22.getSetterName (aEntry.getValue ());

        if (aField.type ().erasure ().name ().equals ("List"))
        {
          // List
          final JClass aTypeParam = ((JClass) aField.type ()).getTypeParameters ().get (0);

          // Ensure list is created :)
          final JVar aTargetList = mGetClone.body ().decl (aField.type (),
                                                           "_" + aEntry.getValue (),
                                                           jRet.invoke (sGetter));

          // for (X aItem : getX())
          final JForEach jForEach = mGetClone.body ().forEach (aTypeParam, "aItem", JExpr.invoke (sGetter));
          jForEach.body ()
                  .add (aTargetList.invoke ("add").arg (_getCloneCode (aCodeModel, jForEach.var (), aTypeParam)));
        }
        else
        {
          mGetClone.body ().add (jRet.invoke (sSetter)
                                     .arg (_getCloneCode (aCodeModel, JExpr.invoke (sGetter), aField.type ())));
        }
      }
      mGetClone.body ()._return (jRet);

      mGetClone.javadoc ().add ("Created by " + CJAXB22.PLUGIN_NAME + " -" + OPT);
    }
    return true;
  }
}
