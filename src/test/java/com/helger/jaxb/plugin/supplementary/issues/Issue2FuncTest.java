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
package com.helger.jaxb.plugin.supplementary.issues;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.junit.Test;

import com.helger.commons.io.file.FileHelper;
import com.helger.commons.io.file.FileOperationManager;
import com.sun.tools.xjc.Driver;

public final class Issue2FuncTest
{
  private static int _run (@Nonnull final File aXSDFile,
                           @Nonnull final File aDestDir,
                           @Nonnull final File aLogFile) throws Exception
  {
    try (final PrintStream aPS = new PrintStream (FileHelper.getOutputStream (aLogFile),
                                                  true,
                                                  StandardCharsets.UTF_8.name ()))
    {
      // Don't use Driver.main because it calls System.exit
      FileOperationManager.INSTANCE.createDirRecursiveIfNotExisting (aDestDir);
      return Driver.run (new String [] { aXSDFile.getAbsolutePath (),
                                         "-d",
                                         aDestDir.getAbsolutePath (),
                                         "-Xph-bean-validation11" },
                         aPS,
                         aPS);
    }
  }

  @Test
  public void testIssue2 () throws Throwable
  {
    final int n = _run (new File ("src/test/resources/external/xsd/issue2.xsd"),
                        new File ("target/issue2"),
                        new File ("target/issue2-result.txt"));
    assertEquals (0, n);
  }
}
