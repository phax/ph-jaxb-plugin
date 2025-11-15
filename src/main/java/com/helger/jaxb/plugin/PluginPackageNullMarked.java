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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.xml.sax.ErrorHandler;

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.sun.codemodel.JPackage;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Makes sure that each package has a <code>package-info.java<code> which has the <code></code>
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginPackageNullMarked extends AbstractPlugin
{
  public static final String OPT = "Xph-package-null-marked";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + " :  add @org.jspecify.annotations.NullMarked annotations to package-info.java";
  }

  @Override
  public boolean run (@NonNull final Outline aOutline,
                      @NonNull final Options aOpts,
                      @NonNull final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    final ICommonsSet <JPackage> aEffectedPackages = new CommonsHashSet <> ();

    // Find all packages
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      aEffectedPackages.add (aClassOutline._package ()._package ());
    }

    for (final JPackage jPackage : aEffectedPackages)
    {
      // Add annotation
      jPackage.annotate (NullMarked.class);

      // add docs
      jPackage.javadoc ().add ("<p>This class was annotated by " + CJAXB.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }

    return true;
  }
}
