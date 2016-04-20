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

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.helger.commons.annotation.Nonempty;
import com.sun.codemodel.JType;

@Immutable
public final class CJAXB22
{
  public static final String NSURI_PH = "http://www.helger.com/namespaces/jaxb22/plugin";

  public static final String PLUGIN_NAME = "ph-jaxb22-plugin";

  private CJAXB22 ()
  {}

  @Nonnull
  @Nonempty
  public static String getGetterName (@Nonnull final JType aType, @Nonnull final String sFieldName)
  {
    String sName = sFieldName;
    if (Character.isLowerCase (sName.charAt (0)))
      sName = sName.substring (0, 1).toUpperCase (Locale.US) + sName.substring (1);

    if (aType.name ().equals ("boolean") || aType.name ().equals ("Boolean"))
      return "is" + sName;
    return "get" + sName;
  }

  @Nonnull
  @Nonempty
  public static String getSetterName (@Nonnull final String sFieldName)
  {
    String sName = sFieldName;
    if (Character.isLowerCase (sName.charAt (0)))
      sName = sName.substring (0, 1).toUpperCase (Locale.US) + sName.substring (1);
    return "set" + sName;
  }
}
