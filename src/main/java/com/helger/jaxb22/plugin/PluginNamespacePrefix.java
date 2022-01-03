/*
 * Copyright (C) 2014-2022 Philip Helger (www.helger.com)
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JPackage;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.generator.bean.PackageOutlineImpl;
import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.outline.PackageOutline;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BIDeclaration;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BIXPluginCustomization;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BindInfo;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.impl.SchemaImpl;

/**
 * This plugin adds {@link javax.xml.bind.annotation.XmlNs} annotations to
 * <i>package-info.java</i> files. Those annotations tells Jaxb2 to generate XML
 * schema's instances with specific namespaces prefixes, instead of the
 * auto-generated (ns1, ns2, ...) prefixes. Definition of thoses prefixes is
 * done in the bindings.xml file.
 * <p/>
 * Bindings.xml file example:
 *
 * <pre>
 *  &lt;?xml version=&quot;1.0&quot;?&gt;
 *  &lt;jxb:bindings version=&quot;2.1&quot;
 *      xmlns:jxb=&quot;http://java.sun.com/xml/ns/jaxb&quot;
 *      xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot;
 *      xmlns:namespace=&quot;http://www.helger.com/namespaces/jaxb22/plugin/namespace-prefix&quot;&gt;
 *
 *      &lt;jxb:bindings schemaLocation=&quot;unireg-common-1.xsd&quot;&gt;
 *          &lt;jxb:schemaBindings&gt;
 *              &lt;jxb:package name=&quot;ch.vd.unireg.xml.common.v1&quot; /&gt;
 *          &lt;/jxb:schemaBindings&gt;
 *          &lt;jxb:bindings&gt;
 *              <b>&lt;namespace:prefix name=&quot;common-1&quot; /&gt;</b>
 *          &lt;/jxb:bindings&gt;
 *      &lt;/jxb:bindings&gt;
 *
 *  &lt;/jxb:bindings&gt;
 * </pre>
 *
 * @author Manuel Siggen (c) 2012 Etat-de-Vaud (www.vd.ch)
 * @author Philip Helger since 2.3.3.4
 */
@IsSPIImplementation
public class PluginNamespacePrefix extends AbstractPlugin
{
  public static final String OPT = "Xph-namespace-prefix";
  public static final String NAMESPACE_URI = CJAXB22.NSURI_PH + "/namespace-prefix";

  private static final Logger LOGGER = LoggerFactory.getLogger (PluginNamespacePrefix.class);

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "-" + OPT + " : activate namespaces prefix customizations";
  }

  @Override
  public List <String> getCustomizationURIs ()
  {
    return new CommonsArrayList <> (NAMESPACE_URI);
  }

  @Override
  public boolean isCustomizationTagName (final String nsUri, final String localName)
  {
    return NAMESPACE_URI.equals (nsUri) && "prefix".equals (localName);
  }

  @Override
  public boolean run (final Outline outline, final Options options, final ErrorHandler errorHandler)
  {
    LOGGER.info ("Running JAXB plugin -" + getOptionName ());

    final JClass jXmlNsClass = outline.getCodeModel ().ref (XmlNs.class);
    final JClass jXmlSchemaClass = outline.getCodeModel ().ref (XmlSchema.class);

    for (final PackageOutline packageOutline : outline.getAllPackageContexts ())
    {
      final JPackage p = packageOutline._package ();

      // get the target namespaces of all schemas that bind to the current
      // package
      final Set <String> packageNamespaces = _getPackageNamespace (packageOutline);

      // is there any prefix binding defined for the current package ?
      final Model packageModel = _getPackageModel ((PackageOutlineImpl) packageOutline);
      final List <Pair> list = _getPrefixBinding (packageModel, packageNamespaces);
      _acknowledgePrefixAnnotations (packageModel);

      if (list == null || list.isEmpty ())
      {
        // no prefix binding, nothing to do
        continue;
      }

      // add XML namespace prefix annotations
      final JAnnotationUse xmlSchemaAnnotation = _getOrAddXmlSchemaAnnotation (p, jXmlSchemaClass);
      if (xmlSchemaAnnotation == null)
        throw new RuntimeException ("Unable to get/add 'XmlSchema' annotation to package [" + p.name () + "]");

      final JAnnotationArrayMember members = xmlSchemaAnnotation.paramArray ("xmlns");
      for (final Pair pair : list)
      {
        final JAnnotationUse ns = members.annotate (jXmlNsClass);
        ns.param ("prefix", pair.getPrefix ());
        ns.param ("namespaceURI", pair.getNamespace ());
      }
    }

    return true;
  }

  private static Set <String> _getPackageNamespace (final PackageOutline packageOutline)
  {
    final Map <String, Integer> map = _getUriCountMap (packageOutline);
    return map == null ? Collections.<String> emptySet () : map.keySet ();
  }

  /**
   * Make sure the prefix annotations have been acknowledged.
   *
   * @param packageModel
   *        the package model
   */
  private void _acknowledgePrefixAnnotations (final Model packageModel)
  {
    final CCustomizations customizations = packageModel.getCustomizations ();
    if (customizations != null)
      for (final CPluginCustomization customization : customizations)
        if (customization.element.getNamespaceURI ().equals (NAMESPACE_URI))
        {
          if (!customization.element.getLocalName ().equals ("prefix"))
            throw new RuntimeException ("Unrecognized element [" + customization.element.getLocalName () + "]");
          customization.markAsAcknowledged ();
        }
  }

  /**
   * This method detects prefixes for a given package as specified in the
   * bindings file. Usually, there is only one namespace per package, but there
   * may be more.
   *
   * @param packageModel
   *        the package model
   * @param packageNamespace
   *        the target namespace for the package
   * @return the prefix annotations
   */
  private static List <Pair> _getPrefixBinding (final Model packageModel, final Set <String> packageNamespace)
  {
    final List <Pair> list = new ArrayList <> ();

    // loop on existing schemas (XSD files)
    for (final XSSchema schema : packageModel.schemaComponent.getSchemas ())
    {
      final SchemaImpl s = (SchemaImpl) schema;
      final XSAnnotation annotation = s.getAnnotation ();
      if (annotation == null)
        continue;

      final Object anno = annotation.getAnnotation ();
      if (anno == null || !(anno instanceof BindInfo))
        continue;

      final BindInfo b = (BindInfo) anno;
      final String targetNS = b.getOwner ().getOwnerSchema ().getTargetNamespace ();
      if (!packageNamespace.contains (targetNS))
      {
        // only consider schemas that bind the current package
        continue;
      }

      // get the prefix's name
      String prefix = "";
      for (final BIDeclaration declaration : b.getDecls ())
      {
        if (declaration instanceof BIXPluginCustomization)
        {
          final BIXPluginCustomization customization = (BIXPluginCustomization) declaration;
          if (customization.element.getNamespaceURI ().equals (NAMESPACE_URI))
          {
            if (!customization.element.getLocalName ().equals ("prefix"))
              throw new RuntimeException ("Unrecognized element [" + customization.element.getLocalName () + "]");
            prefix = customization.element.getAttribute ("name");
            customization.markAsAcknowledged ();
            break;
          }
        }
      }

      list.add (new Pair (targetNS, prefix));
    }

    return list;
  }

  private static JAnnotationUse _getOrAddXmlSchemaAnnotation (final JPackage p, final JClass xmlSchemaClass)
  {
    JAnnotationUse xmlAnn = null;

    final Collection <JAnnotationUse> annotations = p.annotations ();
    if (annotations != null)
    {
      for (final JAnnotationUse annotation : annotations)
      {
        final JClass clazz = annotation.getAnnotationClass ();
        if (clazz == xmlSchemaClass)
        {
          xmlAnn = annotation;
          break;
        }
      }
    }

    if (xmlAnn == null)
    {
      // XmlSchema annotation not found, let's add one
      xmlAnn = p.annotate (xmlSchemaClass);
    }

    return xmlAnn;
  }

  @SuppressWarnings ("unchecked")
  private static Map <String, Integer> _getUriCountMap (final PackageOutline packageOutline)
  {
    try
    {
      final Field field = PackageOutlineImpl.class.getDeclaredField ("uriCountMap");
      field.setAccessible (true);
      return (Map <String, Integer>) field.get (packageOutline);
    }
    catch (final NoSuchFieldException e)
    {
      throw new RuntimeException ("Unable to access 'uriCountMap' field for package outline [" +
                                  packageOutline._package ().name () +
                                  "] : " +
                                  e.getMessage (),
                                  e);
    }
    catch (final IllegalAccessException e)
    {
      throw new RuntimeException ("Unable to find 'uriCountMap' field for package outline [" +
                                  packageOutline._package ().name () +
                                  "] : " +
                                  e.getMessage (),
                                  e);
    }
  }

  private static Model _getPackageModel (final PackageOutlineImpl packageOutline)
  {
    try
    {
      final Field field = PackageOutlineImpl.class.getDeclaredField ("_model");
      field.setAccessible (true);
      return (Model) field.get (packageOutline);
    }
    catch (final NoSuchFieldException e)
    {
      throw new RuntimeException ("Unable to access '_model' field for package outline [" +
                                  packageOutline._package ().name () +
                                  "] : " +
                                  e.getMessage (),
                                  e);
    }
    catch (final IllegalAccessException e)
    {
      throw new RuntimeException ("Unable to find '_model' field for package outline [" +
                                  packageOutline._package ().name () +
                                  "] : " +
                                  e.getMessage (),
                                  e);
    }
  }

  private static class Pair
  {
    private final String m_sNamespace;
    private final String m_sPrefix;

    private Pair (final String namespace, final String prefix)
    {
      this.m_sNamespace = namespace;
      this.m_sPrefix = prefix;
    }

    public String getNamespace ()
    {
      return m_sNamespace;
    }

    public String getPrefix ()
    {
      return m_sPrefix;
    }
  }
}
