/*
 * Copyright (C) 2014-2022 Philip Helger (www.helger.com)
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

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.annotation.CodingStyleguideUnaware;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.CommonsLinkedHashMap;
import com.helger.commons.collection.impl.ICommonsOrderedMap;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMod;
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
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractPlugin.class);

  @Override
  @CodingStyleguideUnaware
  public List <String> getCustomizationURIs ()
  {
    return CollectionHelper.makeUnmodifiable (CJAXB.NSURI_PH);
  }

  @Nonnull
  @ReturnsMutableCopy
  protected static ICommonsOrderedMap <JFieldVar, String> getAllInstanceFields (@Nonnull final ClassOutline aClassOutline)
  {
    final ICommonsOrderedMap <JFieldVar, String> ret = new CommonsLinkedHashMap <> ();

    final JDefinedClass jClass = aClassOutline.implClass;

    // Add fields of this class
    for (final JFieldVar aFieldVar : CollectionHelper.getSortedByKey (jClass.fields ()).values ())
    {
      // Get public name
      final String sFieldVarName = aFieldVar.name ();

      // Ignore static fields
      if ((aFieldVar.mods ().getValue () & JMod.STATIC) == JMod.STATIC)
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Ignoring static field '" + sFieldVarName + "'");
        continue;
      }

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
}
