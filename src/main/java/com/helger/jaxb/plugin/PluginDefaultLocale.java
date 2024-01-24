/*
 * Copyright (C) 2014-2024 Philip Helger (www.helger.com)
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

import java.util.Locale;

import javax.annotation.Nonnull;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.locale.LocaleCache;
import com.helger.commons.string.StringHelper;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.Outline;

/**
 * Plugin implementation, that sets the default locale to "en_US" so that the
 * comments are generated in the chosen locale instead of the platform default
 * locale.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginDefaultLocale extends AbstractPlugin
{
  public static final String OPT = "Xph-default-locale";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + " locale   :  set Java default locale to the specified parameter. Use e.g. 'en_US'";
  }

  @Override
  public int parseArgument (final Options opt, final String [] args, final int i) throws BadCommandLineException
  {
    if (args[i].equals ("-" + OPT))
    {
      final String sLocale = opt.requireArgument ("-" + OPT, args, i + 1);
      if (StringHelper.hasNoText (sLocale))
        throw new BadCommandLineException ("No locale name provided. Use e.g. 'en_US'");
      Locale.setDefault (LocaleCache.getInstance ().getLocale (sLocale));
      logInfo ("Default Locale was set to '" + sLocale + "'");
      return 2;
    }
    return 0;
  }

  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    // Nothing to do here
    return true;
  }
}
