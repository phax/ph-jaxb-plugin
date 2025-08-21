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

import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.base.array.ArrayHelper;
import com.helger.base.equals.EqualsHelper;
import com.helger.base.hashcode.HashCodeCalculator;
import com.helger.base.hashcode.HashCodeGenerator;
import com.helger.base.reflection.GenericReflection;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.helper.CollectionEqualsHelper;
import com.helger.jaxb.adapter.JAXBHelper;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Add default equals and hashCode methods. For equals the {@link EqualsHelper} class is used and
 * for hashCode the {@link HashCodeGenerator} class is used.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginEqualsHashCode extends AbstractPlugin
{
  public static final String OPT = "Xph-equalshashcode";

  @Override
  public String getOptionName ()
  {
    return OPT;
  }

  @Override
  public String getUsage ()
  {
    return "  -" +
           OPT +
           "    :  auto implement equals and hashCode using com.helger.base.equals.EqualsHelper and com.helger.base.hashcode.HashCodeGenerator";
  }

  @Override
  public boolean run (final Outline aOutline, final Options aOpts, final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    final JClass jObject = aCodeModel.ref (Object.class);
    final JClass jNode = aCodeModel.ref (Node.class);
    final JClass jEqualsHelper = aCodeModel.ref (EqualsHelper.class);
    final JClass jCollEqualsHelper = aCodeModel.ref (CollectionEqualsHelper.class);
    final JClass jJaxbHelper = aCodeModel.ref (JAXBHelper.class);
    final JClass jHashCodeCalculator = aCodeModel.ref (HashCodeCalculator.class);
    final JClass jHashCodeGenerator = aCodeModel.ref (HashCodeGenerator.class);
    final JClass jGenericReflection = aCodeModel.ref (GenericReflection.class);
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final FieldOutline [] aFields = aClassOutline.getDeclaredFields ();
      final ICommonsOrderedMap <JFieldVar, String> aFieldVars = getAllInstanceFields (aClassOutline);
      final JDefinedClass jClass = aClassOutline.implClass;
      final boolean bIsRoot = jClass._extends () == null || jClass._extends ().equals (jObject);

      // equals
      {
        final JMethod mEquals = jClass.method (JMod.PUBLIC, aCodeModel.BOOLEAN, "equals");
        mEquals.annotate (Override.class);
        final JVar param = mEquals.param (JMod.FINAL, aCodeModel.ref (Object.class), "o");
        final JBlock jBody = mEquals.body ();

        if (!bIsRoot && aFields.length == 0)
        {
          // No additional fields -> no need to create code
          jBody._return (JExpr._super ().invoke (mEquals).arg (param));
        }
        else
        {
          // if(o==this)return true;
          jBody._if (param.eq (JExpr._this ()))._then ()._return (JExpr.TRUE);
          if (bIsRoot)
          {
            // if(o==null||!getClass().equals(o.getClass()))return false;
            jBody._if (param.eq (JExpr._null ())
                            .cor (JOp.not (JExpr.invoke ("getClass")
                                                .invoke ("equals")
                                                .arg (param.invoke ("getClass")))))._then ()._return (JExpr.FALSE);
          }
          else
          {
            // if(!super.equals(this))return false;
            jBody._if (JOp.not (JExpr._super ().invoke (mEquals).arg (param)))._then ()._return (JExpr.FALSE);
          }
          if (ArrayHelper.isNotEmpty (aFields))
          {
            // final type rhs = (type)o;
            final JVar jTyped = jBody.decl (JMod.FINAL, jClass, "rhs", JExpr.cast (jClass, param));
            for (final JFieldVar aField : aFieldVars.keySet ())
            {
              final String sFieldName = aField.name ();

              if (false)
                logWarn ("Gen [" + sFieldName + "] " + aField.type ().fullName ());

              if (aField.type ().erasure ().name ().equals ("List"))
              {
                final JClass aTypeParam = ((JClass) aField.type ()).getTypeParameters ().get (0);

                if (false)
                  logWarn ("  List: " + aTypeParam.erasure ().name ());

                if (aTypeParam.erasure ().name ().equals ("JAXBElement"))
                {
                  // Special case needed

                  // The method expects List<JAXBElement<?>> which does not work with a defined
                  // class
                  // -> that's why it requires an uncheckedCast instead
                  // Examples:
                  // a. [com.sun.codemodel.JTypeWildcard(? extends Object)]
                  // b. [com.sun.codemodel.JDefinedClass(Ebi40ReductionAndSurchargeType)]
                  boolean bNeedsCast = aTypeParam.getTypeParameters ().get (0) instanceof JDefinedClass;

                  final JExpression aThisExpr = jJaxbHelper.staticInvoke ("equalListJAXBElements")
                                                           .arg (bNeedsCast ? jGenericReflection.staticInvoke ("uncheckedCast")
                                                                                                .arg (JExpr.ref (sFieldName))
                                                                            : JExpr.ref (sFieldName))
                                                           .arg (bNeedsCast ? jGenericReflection.staticInvoke ("uncheckedCast")
                                                                                                .arg (jTyped.ref (sFieldName))
                                                                            : jTyped.ref (sFieldName));
                  jBody._if (JOp.not (aThisExpr))._then ()._return (JExpr.FALSE);
                }
                else
                  if (aTypeParam.erasure ().name ().equals ("Object"))
                  {
                    // Special case needed
                    // List<Object> means a List of "anything" and needs special attention on
                    // JAXBElements and DOM nodes

                    final JExpression aThisExpr = jJaxbHelper.staticInvoke ("equalListAnys")
                                                             .arg (JExpr.ref (sFieldName))
                                                             .arg (jTyped.ref (sFieldName));
                    jBody._if (JOp.not (aThisExpr))._then ()._return (JExpr.FALSE);
                  }
                  else
                  {
                    /*
                     * Ensure that "EqualsHelper.equals" is invoked on all child elements. This is
                     * an issue with "List<JAXBElement<?>>" in Java9 onwards, because JAXBElement
                     * does not implement equals. Note: use "equalsCollection" to allow for null
                     * values as well
                     */
                    final JExpression aThisExpr = jCollEqualsHelper.staticInvoke ("equalsCollection")
                                                                   .arg (JExpr.ref (sFieldName))
                                                                   .arg (jTyped.ref (sFieldName));
                    jBody._if (JOp.not (aThisExpr))._then ()._return (JExpr.FALSE);
                  }
              }
              else
                if (aField.type ().erasure ().name ().equals ("JAXBElement"))
                {
                  final JExpression aThisExpr = jJaxbHelper.staticInvoke ("equalJAXBElements")
                                                           .arg (JExpr.ref (sFieldName))
                                                           .arg (jTyped.ref (sFieldName));
                  jBody._if (JOp.not (aThisExpr))._then ()._return (JExpr.FALSE);
                }
                else
                  if (aField.type ().erasure ().name ().equals ("Object"))
                  {
                    // Runtime check, if an xs:any "Object" is a DOM Node or not
                    final JExpression aNodeExpr = jJaxbHelper.staticInvoke ("equalDOMNodes")
                                                             .arg (JExpr.cast (jNode, JExpr.ref (sFieldName)))
                                                             .arg (JExpr.cast (jNode, jTyped.ref (sFieldName)));
                    final JExpression aThisExpr = jEqualsHelper.staticInvoke ("equals")
                                                               .arg (JExpr.ref (sFieldName))
                                                               .arg (jTyped.ref (sFieldName));
                    JExpression aEquals = JOp.cond (JExpr.ref (sFieldName)._instanceof (jNode), aNodeExpr, aThisExpr);
                    jBody._if (JOp.not (aEquals))._then ()._return (JExpr.FALSE);
                  }
                  else
                  {
                    final JExpression aThisExpr = jEqualsHelper.staticInvoke ("equals")
                                                               .arg (JExpr.ref (sFieldName))
                                                               .arg (jTyped.ref (sFieldName));
                    jBody._if (JOp.not (aThisExpr))._then ()._return (JExpr.FALSE);
                  }
            }
          }
          jBody._return (JExpr.TRUE);
        }

        mEquals.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
      }

      // hashCode
      {
        final JMethod mHashCode = jClass.method (JMod.PUBLIC, aCodeModel.INT, "hashCode");
        mHashCode.annotate (Override.class);

        if (!bIsRoot && aFields.length == 0)
        {
          // No additional fields -> no need to create code
          mHashCode.body ()._return (JExpr._super ().invoke (mHashCode));
        }
        else
        {
          JInvocation aInvocation;
          if (bIsRoot)
            aInvocation = JExpr._new (jHashCodeGenerator).arg (JExpr._this ());
          else
            aInvocation = jHashCodeGenerator.staticInvoke ("getDerived").arg (JExpr._super ().invoke (mHashCode));

          // Instance fields only
          for (final JFieldVar aField : aFieldVars.keySet ())
          {
            final String sFieldName = aField.name ();
            if (aField.type ().erasure ().name ().equals ("List"))
            {
              final JClass aTypeParam = ((JClass) aField.type ()).getTypeParameters ().get (0);

              if (aTypeParam.erasure ().name ().equals ("JAXBElement"))
              {
                // Special hashCode
                // The method expects List<JAXBElement<?>> which does not work with a defined class
                // -> that's why it requires an uncheckedCast instead
                // Examples:
                // a. [com.sun.codemodel.JTypeWildcard(? extends Object)]
                // b. [com.sun.codemodel.JDefinedClass(Ebi40ReductionAndSurchargeType)]
                boolean bNeedsCast = aTypeParam.getTypeParameters ().get (0) instanceof JDefinedClass;

                aInvocation = aInvocation.invoke ("append")
                                         .arg (jJaxbHelper.staticInvoke ("getListJAXBElementHashCode")
                                                          .arg (bNeedsCast ? jGenericReflection.staticInvoke ("uncheckedCast")
                                                                                               .arg (JExpr.ref (sFieldName))
                                                                           : JExpr.ref (sFieldName)));
              }
              else
                if (aTypeParam.erasure ().name ().equals ("Object"))
                {
                  // Special hashCode
                  aInvocation = aInvocation.invoke ("append")
                                           .arg (jJaxbHelper.staticInvoke ("getListAnyHashCode")
                                                            .arg (JExpr.ref (sFieldName)));
                }
                else
                {
                  aInvocation = aInvocation.invoke ("append").arg (JExpr.ref (sFieldName));
                }
            }
            else
              if (aField.type ().erasure ().name ().equals ("JAXBElement"))
              {
                // Special hashCode
                aInvocation = aInvocation.invoke ("append")
                                         .arg (jJaxbHelper.staticInvoke ("getHashCode").arg (JExpr.ref (sFieldName)));
              }
              else
                if (aField.type ().erasure ().name ().equals ("Object"))
                {
                  // Runtime check, if an xs:any "Object" is a DOM Node or not
                  // Make sure, both expressions return "int"
                  final JExpression aNodeExpr = jJaxbHelper.staticInvoke ("getHashCode")
                                                           .arg (JExpr.cast (jNode, JExpr.ref (sFieldName)));
                  final JExpression aThisExpr = jHashCodeCalculator.staticInvoke ("hashCode")
                                                                   .arg (JExpr.ref (sFieldName));
                  JExpression aHashCode = JOp.cond (JExpr.ref (sFieldName)._instanceof (jNode), aNodeExpr, aThisExpr);
                  aInvocation = aInvocation.invoke ("append").arg (aHashCode);
                }
                else
                  aInvocation = aInvocation.invoke ("append").arg (JExpr.ref (sFieldName));
          }

          mHashCode.body ()._return (aInvocation.invoke ("getHashCode"));
        }

        mHashCode.javadoc ().add ("Created by " + CJAXB.PLUGIN_NAME + " -" + OPT);
      }

      // General information
      jClass.javadoc ().add ("<p>This class contains methods created by " + CJAXB.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }
    return true;
  }
}
