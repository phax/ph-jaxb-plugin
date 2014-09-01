/**
 * Copyright (C) 2014 Philip Helger (www.helger.com)
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

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;

import com.helger.commons.annotations.DevelopersNote;
import com.sun.tools.xjc.Driver;

public class XJCLoaderTest
{
  @Test
  @Ignore
  @DevelopersNote ("Ignored because Driver.main calls System.exit!")
  public void testLoadXJC () throws Throwable
  {
    Driver.main (new String [] { new File ("src/test/resources/changelog-1.0.xsd").getAbsolutePath (),
                                "-d",
                                new File ("target").getAbsolutePath () });
  }
}
