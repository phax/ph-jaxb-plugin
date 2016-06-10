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

import java.io.Serializable;
import java.util.List;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.CodingStyleguideUnaware;
import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.commons.regex.RegExHelper;
import com.helger.graph.IMutableDirectedGraphNode;
import com.helger.graph.simple.SimpleDirectedGraph;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Make all classes implement special interfaces that need to be passed as
 * arguments. A typical example is "java.io.Serializable"
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginImplements extends Plugin
{
  private static final String GRAPH_ATTR_VALUE = "value";
  private static final String OPT = "Xph-implements";
  private ICommonsList <String> m_aInterfacesToImplement;

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" + OPT + " interfaceName[,interfaceName] :  implement 1-n interfaces in all classes/enums";
  }

  @Override
  public int parseArgument (final Options opt, final String [] args, final int i) throws BadCommandLineException
  {
    if (args[i].equals ("-" + OPT))
    {
      final String sClassNames = opt.requireArgument ("-" + OPT, args, i + 1);
      m_aInterfacesToImplement = RegExHelper.getSplitToList (sClassNames, "[,;]+");
      if (m_aInterfacesToImplement.isEmpty ())
        throw new BadCommandLineException ("No interface names provided. They must be seprated by comma (,) or semicolon (;)");
      return 2;
    }
    return 0;
  }

  @Override
  @CodingStyleguideUnaware
  public List <String> getCustomizationURIs ()
  {
    return CollectionHelper.makeUnmodifiable (CJAXB22.NSURI_PH);
  }

  @Override
  public boolean run (final Outline aOutline, final Options aOpts, final ErrorHandler aErrorHandler)
  {
    final JCodeModel aCodeModel = aOutline.getCodeModel ();

    // Build the graph with all classes and there hierarchy
    final SimpleDirectedGraph aSG = new SimpleDirectedGraph ();
    // Create all nodes
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
      aSG.createNode (aClassOutline.implClass.fullName ()).setAttribute (GRAPH_ATTR_VALUE, aClassOutline.implClass);
    // Connect them
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      // Check if there is a super-class node present (not present e.g. for
      // Object.class)
      final IMutableDirectedGraphNode aParentNode = aSG.getNodeOfID (aClassOutline.implClass._extends ().fullName ());
      if (aParentNode != null)
        aSG.createRelation (aParentNode, aSG.getNodeOfID (aClassOutline.implClass.fullName ()));
    }

    for (final String sInterface : m_aInterfacesToImplement)
    {
      final String sCleanInterfaceName = sInterface.trim ();
      final JClass aInterface = aCodeModel.ref (sCleanInterfaceName);

      // Implement interfaces only in all base classes, because sub-classes have
      // them already!
      for (final IMutableDirectedGraphNode aNode : aSG.getAllStartNodes ())
        ((JDefinedClass) aNode.getCastedAttribute (GRAPH_ATTR_VALUE))._implements (aInterface);

      // Enums are automatically serializable
      if (!sCleanInterfaceName.equals (Serializable.class.getName ()))
        for (final EnumOutline aEnumOutline : aOutline.getEnums ())
          aEnumOutline.clazz._implements (aInterface);
    }
    return true;
  }
}
