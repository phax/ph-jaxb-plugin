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

import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.style.CodingStyleguideUnaware;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.CollectionHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.helper.CollectionSort;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;

/**
 * Abstract plugin stuff.
 *
 * @author Philip Helger
 * @since 2.2.11.14
 */
public abstract class AbstractPlugin extends Plugin
{
  private final Logger m_aLogger;
  private boolean m_bDebugMode;

  protected AbstractPlugin ()
  {
    m_aLogger = LoggerFactory.getLogger (getClass ());
  }

  protected final void initPluginLogging (final boolean bDebugMode)
  {
    m_bDebugMode = bDebugMode;
  }

  protected final void logDebug (@NonNull final Supplier <String> a)
  {
    if (m_bDebugMode)
      logInfo (a.get ());
  }

  protected final void logInfo (final String s)
  {
    m_aLogger.info (s);
  }

  protected final void logWarn (final String s)
  {
    m_aLogger.warn (s);
  }

  protected final void logError (final String s)
  {
    m_aLogger.error (s);
  }

  protected final void logError (final String s, @Nullable final Exception ex)
  {
    m_aLogger.error (s, ex);
  }

  @Override
  @CodingStyleguideUnaware
  public List <String> getCustomizationURIs ()
  {
    return CollectionHelper.makeUnmodifiable (CJAXB.NSURI_PH);
  }

  @NonNull
  @ReturnsMutableCopy
  protected ICommonsOrderedMap <JFieldVar, String> getAllInstanceFields (@NonNull final ClassOutline aClassOutline)
  {
    final ICommonsOrderedMap <JFieldVar, String> ret = new CommonsLinkedHashMap <> ();

    final JDefinedClass jClass = aClassOutline.implClass;

    // Add fields of this class
    for (final JFieldVar aFieldVar : CollectionSort.getSortedByKey (jClass.fields ()).values ())
    {
      // Get public name
      final String sFieldVarName = aFieldVar.name ();

      // Ignore static fields
      if ((aFieldVar.mods ().getValue () & JMod.STATIC) == JMod.STATIC)
      {
        logDebug ( () -> "Ignoring static field '" + sFieldVarName + "'");
        continue;
      }

      final CPropertyInfo aPI = aClassOutline.target.getProperty (sFieldVarName);
      String sFieldName;
      if (aPI == null)
      {
        if (!"otherAttributes".equals (sFieldVarName))
        {
          throw new IllegalStateException ("'" +
                                           aFieldVar.name () +
                                           "' not found in " +
                                           new CommonsArrayList <> (aClassOutline.target.getProperties (),
                                                                    pi -> pi.getName (false)) +
                                           " of " +
                                           jClass.fullName ());
        }
        // Created by <xs:anyAttribute/>
        sFieldName = sFieldVarName;
      }
      else
      {
        sFieldName = aPI.getName (true);
      }
      ret.put (aFieldVar, sFieldName);
    }

    return ret;
  }

  protected boolean allowsJSpecifyAnnotations (@NonNull JDefinedClass jClass, @NonNull JType aLocalType)
  {
    if (aLocalType.isPrimitive ())
    {
      // Primitive types cannot be annotated
      return false;
    }
    if (aLocalType.name ().equals (jClass.name ()))
    {
      // Type has the same name as the outer class and would therefore be generated as
      // FQCN. This does not work with JSpecify.
      return false;
    }
    if (aLocalType instanceof JDefinedClass jdc && jdc.outer () != null)
    {
      // It's an inner class and would be rendered as C1.C2 which does not work with
      // JSpecify
      return false;
    }
    if (aLocalType.name ().equals ("IdentifierType"))
    {
      // Special name usually imported
      return false;
    }

    if (false)
      logInfo (jClass.fullName () + " vs. " + aLocalType.fullName ());
    return true;
  }
}
