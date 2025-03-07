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

import javax.annotation.Nonnull;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Plugin that makes all fields private instead of the default "protected"
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginFieldsPrivate extends AbstractPlugin
{
  public static final String OPT = "Xph-fields-private";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + "    :  mark all fields as private";
  }

  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      for (final JFieldVar aFieldVar : jClass.fields ().values ())
        aFieldVar.mods ().setPrivate ();
    }
    return true;
  }
}
