/**
 * Copyright (C) 2014-2015 Philip Helger (www.helger.com)
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotations.IsSPIImplementation;
import com.helger.commons.collections.ContainerHelper;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Improved the code quality by avoiding some compiler warnings, and by making
 * special constants accessible from the outside.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginCodeQuality extends Plugin
{
  private static final String OPT = "Xph-code-quality";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + "    :  fix some issues that cause warnings in the generated code";
  }

  @Override
  public List <String> getCustomizationURIs ()
  {
    return ContainerHelper.newUnmodifiableList (CJAXB22.NSURI_PH);
  }

  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;

      final Set <String> aFieldNames = new HashSet <String> ();
      for (final JFieldVar aField : jClass.fields ().values ())
        aFieldNames.add (aField.name ());

      for (final JMethod jMethod : jClass.methods ())
      {
        final List <JVar> aParams = jMethod.params ();
        if (jMethod.name ().startsWith ("set") && aParams.size () == 1)
        {
          final JVar aParam = aParams.get (0);
          if (aFieldNames.contains (aParam.name ()))
          {
            // Change name because it conflicts with field "value"
            aParam.name (aParam.name () + "Param");

            // TODO update javaDoc - currently not possible see
            // https://java.net/jira/browse/CODEMODEL-15
          }
        }
      }
    }

    // Get all ObjectFactory classes
    final Set <JDefinedClass> aObjFactories = new HashSet <JDefinedClass> ();
    for (final CElementInfo ei : aOutline.getModel ().getAllElements ())
    {
      final JDefinedClass aClass = aOutline.getPackageContext (ei._package ())
                                           .objectFactoryGenerator ()
                                           .getObjectFactory ();
      aObjFactories.add (aClass);
    }

    // Manipulate all ObjectFactory classes
    final String sByteArrayTypeName = aOutline.getCodeModel ().BYTE.array ().name ();
    for (final JDefinedClass aObjFactory : aObjFactories)
    {
      for (final JFieldVar aFieldVar : aObjFactory.fields ().values ())
      {
        // Make all static QNames public
        if (aFieldVar.type ().name ().equals ("QName"))
          aFieldVar.mods ().setPublic ();
      }

      for (final JMethod aMethod : aObjFactory.methods ())
      {
        final List <JVar> aParams = aMethod.params ();
        if (aMethod.name ().startsWith ("create") &&
            aMethod.type ().name ().startsWith ("JAXBElement<") &&
            aParams.size () == 1)
        {
          // Modify all JAXBElement<T> createT (Object o) methods
          final JVar aParam = aParams.get (0);

          // Modify parameter
          aParam.mods ().setFinal (true);

          // Modify method
          aMethod.javadoc ().addReturn ().add ("The created JAXBElement and never <code>null</code>.");

          if (aParam.type ().name ().equals (sByteArrayTypeName))
          {
            // Try to remove the contained cast
            // TODO Unfortunately this does not work with the current code model
            // See https://java.net/jira/browse/CODEMODEL-13
          }
        }
        else
          if (aMethod.name ().startsWith ("create") && aParams.isEmpty ())
          {
            // Modify all Object createObject() methods
            aMethod.javadoc ()
                   .addReturn ()
                   .add ("The created " + aMethod.type ().name () + " object and never <code>null</code>.");
          }
      }
    }
    return true;
  }
}
