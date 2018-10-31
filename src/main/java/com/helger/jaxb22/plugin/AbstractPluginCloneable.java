/**
 * Copyright (C) 2014-2018 Philip Helger (www.helger.com)
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

import javax.annotation.Nonnull;

import com.helger.commons.annotation.CodingStyleguideUnaware;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.ArrayHelper;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.CommonsLinkedHashMap;
import com.helger.commons.collection.impl.ICommonsMap;
import com.helger.commons.collection.impl.ICommonsOrderedMap;
import com.helger.commons.lang.CloneHelper;
import com.sun.codemodel.ClassType;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;

/**
 * Abstract cloneable support.
 *
 * @author Philip Helger
 * @since 2.2.11.12
 */
public abstract class AbstractPluginCloneable extends Plugin
{
  @Override
  @CodingStyleguideUnaware
  public List <String> getCustomizationURIs ()
  {
    return CollectionHelper.makeUnmodifiable (CJAXB22.NSURI_PH);
  }

  protected static boolean _isImmutable (@Nonnull final JType aType)
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
           sTypeName.equals ("Duration") ||
           sTypeName.equals ("W3CEndpointReference") ||
           sTypeName.equals ("LocalDate") ||
           sTypeName.equals ("LocalDateTime") ||
           sTypeName.equals ("LocalTime") ||
           sTypeName.equals ("ZonedDateTime") ||
           sTypeName.equals ("OffsetDateTime") ||
           sTypeName.equals ("Boolean") ||
           sTypeName.equals ("Byte") ||
           sTypeName.equals ("Character") ||
           sTypeName.equals ("Double") ||
           sTypeName.equals ("Float") ||
           sTypeName.equals ("Integer") ||
           sTypeName.equals ("Long") ||
           sTypeName.equals ("Short");
  }

  protected static boolean _isJavaCloneable (@Nonnull final JType aType)
  {
    // Check by name :)
    final String sTypeName = aType.name ();
    return sTypeName.equals ("XMLGregorianCalendar");
  }

  protected static boolean _isImmutableArray (@Nonnull final JType aType)
  {
    return aType.isArray () && _isImmutable (aType.elementType ());
  }

  @Nonnull
  @ReturnsMutableCopy
  protected static ICommonsMap <JFieldVar, String> _getAllFields (@Nonnull final ClassOutline aClassOutline)
  {
    final ICommonsOrderedMap <JFieldVar, String> ret = new CommonsLinkedHashMap <> ();

    final JDefinedClass jClass = aClassOutline.implClass;

    // Add fields of this class
    for (final JFieldVar aFieldVar : CollectionHelper.getSortedByKey (jClass.fields ()).values ())
    {
      // Get public name
      final String sFieldVarName = aFieldVar.name ();
      final CPropertyInfo aPI = aClassOutline.target.getProperty (sFieldVarName);
      String sFieldName;
      if (aPI == null)
      {
        if ("otherAttributes".equals (sFieldVarName))
        {
          // Created by <xs:anyAttribute/>
          sFieldName = sFieldVarName;
        }
        else
        {
          throw new IllegalStateException ("'" +
                                           aFieldVar.name () +
                                           "' not found in " +
                                           new CommonsArrayList <> (aClassOutline.target.getProperties (),
                                                                    pi -> pi.getName (false)) +
                                           " of " +
                                           jClass.fullName ());
        }
      }
      else
      {
        sFieldName = aPI.getName (true);
      }
      ret.put (aFieldVar, sFieldName);
    }

    return ret;
  }

  @Nonnull
  protected static JExpression _getCloneCode (final JCodeModel aCodeModel,
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
}
