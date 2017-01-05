/**
 * Copyright (C) 2014-2017 Philip Helger (www.helger.com)
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

/**
 * big thanks to original author: cocorossello
 *
 * @author Philip Helger
 */
public class PluginBeanValidation11 extends AbstractPluginBeanValidation
{
  private static final String OPT = "Xph-bean-validation11";

  public PluginBeanValidation11 ()
  {
    super (false);
  }

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + " locale   :  inject Bean validation 1.1 annotations (JSR 349)";
  }
}
