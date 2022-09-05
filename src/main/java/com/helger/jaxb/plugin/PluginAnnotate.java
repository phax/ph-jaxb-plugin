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
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.impl.CommonsHashSet;
import com.helger.commons.collection.impl.ICommonsSet;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Create {@link Nonnull}/{@link Nullable} annotations in all bean generated
 * objects as well as in the ObjectFactory classes
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginAnnotate extends AbstractPlugin
{
  public static final String OPT = "Xph-annotate";
  private static final Logger LOGGER = LoggerFactory.getLogger (PluginAnnotate.class);

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" +
           OPT +
           " :  add @javax.annotation.Nullable/@javax.annotation.Nonnull annotations to getters and setters";
  }

  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    LOGGER.info ("Running JAXB plugin -" + getOptionName ());

    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final ICommonsSet <JDefinedClass> aEffectedClasses = new CommonsHashSet <> ();

    // For all classes
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      for (final JMethod aMethod : CollectionHelper.newList (jClass.methods ()))
      {
        final List <JVar> aParams = aMethod.params ();
        if (aMethod.name ().startsWith ("get") && aParams.isEmpty ())
        {
          final JType aReturnType = aMethod.type ();
          // Find e.g. List<ItemListType> getItemList()
          if (aReturnType.erasure ().name ().equals ("List"))
          {
            aMethod.annotate (Nonnull.class);
            aMethod.annotate (ReturnsMutableObject.class).param ("value", "JAXB implementation style");
            aEffectedClasses.add (jClass);
          }
          else
            if (!aReturnType.isPrimitive ())
            {
              aMethod.annotate (Nullable.class);
              aEffectedClasses.add (jClass);
            }
        }
        else
          if (aMethod.type () == aCodeModel.VOID && aMethod.name ().startsWith ("set") && aParams.size () == 1)
          {
            final JVar aParam = aParams.get (0);
            if (!aParam.type ().isPrimitive ())
            {
              aParam.annotate (Nullable.class);
              aEffectedClasses.add (jClass);
            }
          }
      }
    }

    // Get all ObjectFactory classes
    final ICommonsSet <JDefinedClass> aObjFactories = new CommonsHashSet <> ();
    for (final CElementInfo ei : aOutline.getModel ().getAllElements ())
    {
      final JDefinedClass aClass = aOutline.getPackageContext (ei._package ())
                                           .objectFactoryGenerator ()
                                           .getObjectFactory ();
      aObjFactories.add (aClass);
    }

    // Manipulate all ObjectFactory classes
    for (final JDefinedClass aObjFactory : aObjFactories)
      for (final JMethod aMethod : aObjFactory.methods ())
      {
        final List <JVar> aParams = aMethod.params ();
        if (aMethod.name ().startsWith ("create") &&
            aMethod.type ().erasure ().name ().equals ("JAXBElement") &&
            aParams.size () == 1)
        {
          // Modify all JAXBElement<T> createT (Object o) methods

          // Modify parameter
          aParams.get (0).annotate (Nullable.class);

          // Modify method
          aMethod.annotate (Nonnull.class);
          aEffectedClasses.add (aObjFactory);
        }
        else
          if (aMethod.name ().startsWith ("create") && aParams.isEmpty ())
          {
            // Modify all Object createObject() methods
            aMethod.annotate (Nonnull.class);
            aEffectedClasses.add (aObjFactory);
          }
      }

    for (final JDefinedClass jClass : aEffectedClasses)
    {
      // General information
      jClass.javadoc ().add ("<p>This class was annotated by " + CJAXB22.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }

    return true;
  }
}
