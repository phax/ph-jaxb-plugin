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

import org.jspecify.annotations.NonNull;
import org.xml.sax.ErrorHandler;

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.sun.codemodel.JAssignment;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Improved the code quality by avoiding some compiler warnings, and by making special constants
 * accessible from the outside.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginCodeQuality extends AbstractPlugin
{
  public static final String OPT = "Xph-code-quality";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + "  fix some issues that cause warnings in the generated code";
  }

  @Override
  public boolean run (@NonNull final Outline aOutline,
                      @NonNull final Options aOpts,
                      @NonNull final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;

      if (false)
      {
        // Does not work, because the execution order of plugins is undefined.
        // And if this happens after the equals/hashCode plugin it will create
        // invalid code!
        // Change field name - copy the list!
        for (final JFieldVar aField : new CommonsArrayList <> (jClass.fields ().values ()))
          if ((aField.mods ().getValue () & JMod.STATIC) == 0)
            if (!aField.name ().startsWith ("m_"))
              aField.name ("m_" + aField.name ());
      }
      else
        if (false)
        {
          // This fails, because the Java 8 javadoc will create errors because
          // of this
          final ICommonsSet <String> aFieldNames = new CommonsHashSet <> ();
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

      if (false)
      {
        for (final JMethod jMethod : jClass.methods ())
          if (jMethod.name ().startsWith ("get") &&
              jMethod.params ().isEmpty () &&
              jMethod.type ().erasure ().name ().equals ("List"))
          {
            List <Object> aContents = jMethod.body ().getContents ();
            Object aFirst = aContents.get (0);
            if (aFirst instanceof JConditional)
            {
              aContents = ((JConditional) aFirst)._then ().getContents ();
              aFirst = aContents.get (0);
              if (aFirst instanceof JAssignment)
              {
                final JAssignment aAss = (JAssignment) aFirst;
                // No way to change assignment :(
              }
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
            aMethod.type ().erasure ().name ().equals ("JAXBElement") &&
            aParams.size () == 1)
        {
          // Modify all JAXBElement<T> createT (Object o) methods
          final JVar aParam = aParams.get (0);

          // Modify parameter
          aParam.mods ().setFinal (true);

          // Modify method
          aMethod.javadoc ().addReturn ().add ("The created JAXBElement and never <code>null</code>.");

          if (false)
            if (aParam.type ().name ().equals (sByteArrayTypeName))
            {
              // Try to remove the contained cast
              // TODO Unfortunately this does not work with the current code
              // model
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
