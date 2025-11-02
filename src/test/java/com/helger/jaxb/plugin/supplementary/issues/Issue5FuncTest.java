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
package com.helger.jaxb.plugin.supplementary.issues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.CGlobal;
import com.helger.base.lang.ClassPathHelper;
import com.helger.base.string.StringImplode;
import com.helger.base.string.StringReplace;
import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.io.file.FileHelper;
import com.helger.io.file.FileOperationManager;
import com.helger.io.file.FilenameHelper;
import com.helger.io.file.SimpleFileIO;
import com.sun.tools.xjc.Driver;

public final class Issue5FuncTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (Issue5FuncTest.class);

  private static int _run (@NonNull final File aXSDFile,
                           @Nullable final File aBindingFile,
                           @Nullable final File aCatalogFile,
                           @NonNull final File aDestDir,
                           @Nullable final File aLogFile) throws Exception
  {
    final ICommonsList <String> aParams = new CommonsArrayList <> (aXSDFile.getAbsolutePath (),
                                                                   "-d",
                                                                   aDestDir.getAbsolutePath (),
                                                                   "-Xph-offset-dt-extension",
                                                                   "-Xph-value-extender",
                                                                   "-extension");
    if (false)
      aParams.add ("-debug");
    if (aBindingFile != null)
      aParams.addAll ("-b", aBindingFile.getAbsolutePath ());
    if (aCatalogFile != null)
      aParams.addAll ("-catalog", aCatalogFile.getAbsolutePath ());

    // Catalog base
    final Wrapper <String> aCatalogXML = new Wrapper <> ("<?xml version='1.0' encoding='utf-8'?>\n" +
                                                         "<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">\n" +
                                                         "  <public publicId=\"http://www.w3.org/2000/09/xmldsig#\" uri=\"jar:file:$1!/schemas/xmldsig-core-schema.xsd\" />\n" +
                                                         "  <public publicId=\"http://uri.etsi.org/01903/v1.3.2#\" uri=\"jar:file:$2!/schemas/XAdES01903v132-201601.xsd\" />\n" +
                                                         "  <public publicId=\"http://uri.etsi.org/01903/v1.4.1#\" uri=\"jar:file:$3!/schemas/XAdES01903v141-201601.xsd\" />\n" +
                                                         "  <public publicId=\"urn:un:unece:uncefact:data:specification:CoreComponentTypeSchemaModule:2\" uri=\"jar:file:$4!/schemas/CCTS_CCT_SchemaModule.xsd\" />\n" +
                                                         "</catalog>");

    // Put effective paths into catalog XML
    ClassPathHelper.forAllClassPathEntries (x -> {
      if (x.endsWith (".jar"))
      {
        // Fill in absolute path for local machine
        final File f = new File (URLDecoder.decode (x, StandardCharsets.ISO_8859_1));
        final Function <String, String> mod = FilenameHelper::getPathUsingUnixSeparator;
        if (f.getName ().startsWith ("ph-xsds-xmldsig-"))
          aCatalogXML.set (StringReplace.replaceAll (aCatalogXML.get (), "$1", mod.apply (x)));
        else
          if (f.getName ().startsWith ("ph-xsds-xades132-"))
            aCatalogXML.set (StringReplace.replaceAll (aCatalogXML.get (), "$2", mod.apply (x)));
          else
            if (f.getName ().startsWith ("ph-xsds-xades141-"))
              aCatalogXML.set (StringReplace.replaceAll (aCatalogXML.get (), "$3", mod.apply (x)));
            else
              if (f.getName ().startsWith ("ph-xsds-ccts-cct-schemamodule-"))
                aCatalogXML.set (StringReplace.replaceAll (aCatalogXML.get (), "$4", mod.apply (x)));

        aParams.add (x);

        if (false)
          LOGGER.info (x);
      }
    });

    // Store catalog file on disc?
    if (aCatalogFile != null)
    {
      if (false)
        LOGGER.info (aCatalogXML.get ());
      // Write effective catalog
      SimpleFileIO.writeFile (aCatalogFile, aCatalogXML.get ().getBytes (StandardCharsets.UTF_8));
    }

    try (final PrintStream aPS = new PrintStream (aLogFile == null ? System.out : FileHelper.getOutputStream (aLogFile),
                                                  true,
                                                  StandardCharsets.UTF_8.name ()))
    {
      // Don't use Driver.main because it calls System.exit
      FileOperationManager.INSTANCE.createDirRecursiveIfNotExisting (aDestDir);
      return Driver.run (aParams.toArray (CGlobal.EMPTY_STRING_ARRAY), aPS, aPS);
    }
  }

  @Test
  @Ignore ("This must be ignored during release building, because there is a circular dependency between ph-xsds and this plugin")
  public void testIssue5 () throws Throwable
  {
    // Main code generation
    final File fTargetDir = new File ("target/ubl23-waybill");
    final int nErrorCode = _run (new File ("src/test/resources/external/ubl23/maindoc/UBL-Waybill-2.3.xsd"),
                                 new File ("src/test/resources/external/ubl23/bindings23.xjb"),
                                 new File ("src/test/resources/external/ubl23/catalog.xml"),
                                 fTargetDir,
                                 false ? null : new File ("target/ubl23-waybill-result.txt"));
    assertEquals (0, nErrorCode);

    // Check outcome
    final File fFileUnderQuestion = new File (fTargetDir,
                                              "oasis/names/specification/ubl/schema/xsd/commonaggregatecomponents_23/DocumentDistributionType.java");
    assertTrue (fFileUnderQuestion.exists ());

    final ICommonsList <String> aFileLines = SimpleFileIO.getAllFileLines (fFileUnderQuestion, StandardCharsets.UTF_8);
    final int nStart = 378;
    assertTrue (aFileLines.size () > nStart);
    if (false)
      LOGGER.info ("...[" +
                   nStart +
                   "-" +
                   aFileLines.size () +
                   "]\n" +
                   StringImplode.imploder ().source (aFileLines).offset (nStart).separator ('\n').build ());
  }
}
