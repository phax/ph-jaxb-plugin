/*
 * Copyright (C) 2014-2023 Philip Helger (www.helger.com)
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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.xml.sax.ErrorHandler;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.CommonsHashMap;
import com.helger.commons.collection.impl.CommonsHashSet;
import com.helger.commons.collection.impl.CommonsTreeSet;
import com.helger.commons.collection.impl.ICommonsMap;
import com.helger.commons.collection.impl.ICommonsSet;
import com.helger.commons.lang.GenericReflection;
import com.helger.jaxb.plugin.cm.MyTernaryOp;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Add special "value" constructors, setters and getters for JAXB generated
 * elements. This is used e.g. for UBL and CII code generation.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginValueExtender extends AbstractPlugin
{
  /**
   * Map from class name to the Codemodel type of the "value" field
   *
   * @author Philip Helger
   */
  final class SuperClassMap extends CommonsHashMap <String, JType>
  {
    private static final String FIELD_VALUE = "value";

    private void _recursiveFill (@Nonnull final ICommonsSet <String> aHandledClasses,
                                 @Nonnull final JCodeModel cm,
                                 @Nonnull final JType jType)
    {
      final String sClassFullName = jType.fullName ();

      // Ignore all system super classes
      if (sClassFullName.startsWith ("java."))
        return;

      // Try to load each class only once
      if (!aHandledClasses.add (sClassFullName))
        return;

      if (false)
        logDebug ( () -> "!!" + jType.getClass ().getSimpleName () + " -- " + sClassFullName);

      // We don't care about the classes we created
      // Take only "external", meaning non-generated super classes
      if (jType instanceof JDefinedClass)
      {
        logDebug ( () -> "  Searching defined super class '" + sClassFullName + "'");
        for (final Map.Entry <String, JFieldVar> aFieldEntry : ((JDefinedClass) jType).fields ().entrySet ())
          if (aFieldEntry.getKey ().equals (FIELD_VALUE))
          {
            put (sClassFullName, aFieldEntry.getValue ().type ());
            logDebug ( () -> "    Found value field of type '" + aFieldEntry.getValue ().type ().name () + "'");
            break;
          }

        _recursiveFill (aHandledClasses, cm, ((JDefinedClass) jType)._extends ());
      }
      else
      {
        // Try to load class
        final Class <?> aSuperClass = GenericReflection.getClassFromNameSafe (sClassFullName);
        if (aSuperClass != null)
        {
          logDebug ( () -> "  Successfully loaded super class '" + sClassFullName + "' via reflection");

          // Check if that class has a "value" field (name of the variable
          // created by JAXB to indicate the content of an XML element)
          for (final Field aField : aSuperClass.getFields ())
            if (aField.getName ().equals (FIELD_VALUE))
            {
              // Map from super class name to codemodel value field type
              put (sClassFullName, cm._ref (aField.getType ()));
              logDebug ( () -> "    Found value field of type '" + aField.getType ().getName () + "'");
              break;
            }

          // Use super class of super class
          _recursiveFill (aHandledClasses, cm, cm.ref (aSuperClass.getSuperclass ()));
        }
        else
        {
          logWarn ("  Failed to load super class '" + sClassFullName + "' via reflection");
        }
      }
    }

    private void _fill (@Nonnull final Outline aOutline)
    {
      final JCodeModel cm = aOutline.getCodeModel ();
      final ICommonsSet <String> aHandledClasses = new CommonsHashSet <> ();
      for (final ClassOutline aClassOutline : aOutline.getClasses ())
      {
        final JDefinedClass jClass = aClassOutline.implClass;
        // Never deal with the classes we created
        _recursiveFill (aHandledClasses, cm, jClass._extends ());
      }
    }

    private void _addIfNotPresent (@Nonnull final String sClassname, @Nonnull final JType aFieldType)
    {
      if (!containsKey (sClassname))
        put (sClassname, aFieldType);
    }

    public SuperClassMap (@Nonnull final Outline aOutline)
    {
      // Add some classes that are known to be such super types
      final JCodeModel cm = aOutline.getCodeModel ();

      _fill (aOutline);

      // Make sure some commonly used classes are present
      // Reside in ph-xsds-ccts-cct-schemamodule
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.AmountType", cm.ref (BigDecimal.class));
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.BinaryObjectType", cm.ref (byte [].class));
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.CodeType", cm.ref (String.class));
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.DateTimeType", cm.ref (String.class));
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.IdentifierType", cm.ref (String.class));
      // Indicator is complex
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.MeasureType", cm.ref (BigDecimal.class));
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.NumericType", cm.ref (BigDecimal.class));
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.QuantityType", cm.ref (BigDecimal.class));
      _addIfNotPresent ("com.helger.xsds.ccts.cct.schemamodule.TextType", cm.ref (String.class));

      logDebug ( () -> "Found " + size () + " super classes with '" + FIELD_VALUE + "' fields");
    }

    /**
     * Get the {@link JType} of the <code>value</code> field in the provided
     * class
     *
     * @param jClass
     *        Class to inspect
     * @return <code>null</code> if none was found
     */
    @Nullable
    public JType getValueFieldType (@Nonnull final JDefinedClass jClass)
    {
      JType aValueType = null;
      // Check if that class has a "value" member (name of the variable
      // created by JAXB to indicate the content of an XML element)
      for (final JFieldVar aField : jClass.fields ().values ())
        if (aField.name ().equals (FIELD_VALUE))
        {
          aValueType = aField.type ();
          break;
        }

      if (aValueType == null && jClass._extends () != null && !(jClass._extends () instanceof JDefinedClass))
      {
        // Check only super classes that are not defined in this generation run
        // but e.g. imported via episodes (bindings)
        aValueType = get (jClass._extends ().fullName ());
      }
      return aValueType;
    }
  }

  public static final String OPT = "Xph-value-extender";
  // @author is only valid for file comments
  public static final String AUTHOR = "<br>\nNote: automatically created by " + CJAXB.PLUGIN_NAME + " -" + OPT;

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
           "    :  create additional constructors with the 'value' as argument + getter and setter for the value";
  }

  private void _addDefaultCtors (@Nonnull final Outline aOutline)
  {
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;

      // Always add default constructor
      final JMethod aDefCtor = jClass.constructor (JMod.PUBLIC);
      aDefCtor.javadoc ().add ("Default constructor");
      aDefCtor.javadoc ().add (AUTHOR);

      // General information
      jClass.javadoc ().add ("<p>This class contains methods created by " + CJAXB.PLUGIN_NAME + " -" + OPT + "</p>\n");
    }
    logDebug ( () -> "Added default constructors to " + aOutline.getClasses ().size () + " classes");
  }

  private void _recursiveAddValueConstructorToDerivedClasses (@Nonnull final Outline aOutline,
                                                              @Nonnull final JDefinedClass jParentClass,
                                                              @Nonnull final JType aValueType,
                                                              @Nonnull final Set <JClass> aAllRelevantClasses,
                                                              final boolean bHasPluginOffsetDT)
  {
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jCurClass = aClassOutline.implClass;
      if (jCurClass._extends () == jParentClass)
      {
        aAllRelevantClasses.add (jCurClass);

        logDebug ( () -> "  New ctor '" + jCurClass.name () + "(" + aValueType.name () + ")'");

        {
          final JMethod aValueCtor = jCurClass.constructor (JMod.PUBLIC);
          final JVar aParam = aValueCtor.param (JMod.FINAL, aValueType, "valueParam");
          if (!aValueType.isPrimitive ())
            aParam.annotate (Nullable.class);
          aValueCtor.body ().invoke ("super").arg (aParam);
          aValueCtor.javadoc ()
                    .add ("Constructor for value of type " +
                          aValueType.erasure ().name () +
                          " calling super class constructor.");
          aValueCtor.javadoc ()
                    .addParam (aParam)
                    .add ("The value to be set." + (aValueType.isPrimitive () ? "" : " May be <code>null</code>."));
          aValueCtor.javadoc ().add (AUTHOR);
        }

        if (bHasPluginOffsetDT)
        {
          final JType aNewType = PluginOffsetDTExtension.getOtherType (aValueType, aOutline.getCodeModel ());
          if (aNewType != null)
          {
            final JMethod aValueCtor = jCurClass.constructor (JMod.PUBLIC);
            final JVar aParam = aValueCtor.param (JMod.FINAL, aNewType, "valueParam");
            aParam.annotate (Nullable.class);
            aValueCtor.body ().invoke ("setValue").arg (aParam);
            aValueCtor.javadoc ().add ("Constructor for value of type " + aNewType.name ());
            aValueCtor.javadoc ().addParam (aParam).add ("The value to be set. May be <code>null</code>.");
            aValueCtor.javadoc ().add (AUTHOR);
          }
        }

        // Set in all derived classes
        _recursiveAddValueConstructorToDerivedClasses (aOutline,
                                                       jCurClass,
                                                       aValueType,
                                                       aAllRelevantClasses,
                                                       bHasPluginOffsetDT);
      }
    }
  }

  private void _addValueSetterInUsingClasses (@Nonnull final Outline aOutline,
                                              @Nonnull final JType aValueType,
                                              @Nonnull final Set <JClass> aAllRelevantClasses,
                                              final boolean bHasPluginOffsetDT)
  {
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      // Work on a copy of the methods, because they are changed
      for (final JMethod aMethod : new CommonsArrayList <> (jClass.methods ()))
        // Must be a setter
        if (aMethod.name ().startsWith ("set"))
        {
          // Must have exactly 1 parameter that is part of aAllRelevantClasses
          final List <JVar> aParams = aMethod.params ();
          if (aParams.size () == 1 && aAllRelevantClasses.contains (aParams.get (0).type ()))
          {
            final JType aParamType = aParams.get (0).type ();

            logDebug ( () -> "  New setter '" +
                             aParamType.name () +
                             " " +
                             jClass.name () +
                             "." +
                             aMethod.name () +
                             "(" +
                             aValueType.name () +
                             ")'");

            {
              final JMethod aSetter = jClass.method (JMod.PUBLIC, aParamType, aMethod.name ());
              aSetter.annotate (Nonnull.class);
              final JVar aParam = aSetter.param (JMod.FINAL, aValueType, "valueParam");
              if (!aValueType.isPrimitive ())
                aParam.annotate (Nullable.class);
              final JVar aObj = aSetter.body ()
                                       .decl (aParamType, "aObj", JExpr.invoke ("get" + aMethod.name ().substring (3)));
              final JConditional aIf = aSetter.body ()._if (aObj.eq (JExpr._null ()));
              aIf._then ().assign (aObj, JExpr._new (aParamType).arg (aParam));
              aIf._then ().invoke (aMethod).arg (aObj);
              aIf._else ().invoke (aObj, "setValue").arg (aParam);
              aSetter.body ()._return (aObj);
              aSetter.javadoc ().add ("Special setter with value of type " + aParam.type ().name ());
              aSetter.javadoc ()
                     .addParam (aParam)
                     .add ("The value to be set." + (aValueType.isPrimitive () ? "" : " May be <code>null</code>."));
              aSetter.javadoc ()
                     .addReturn ()
                     .add ("The created intermediary object of type " +
                           aParamType.name () +
                           " and never <code>null</code>");
              aSetter.javadoc ().add (AUTHOR);
            }

            if (bHasPluginOffsetDT)
            {
              // Add the setter for the 2nd data type as well
              final JType aNewType = PluginOffsetDTExtension.getOtherType (aValueType, aOutline.getCodeModel ());
              if (aNewType != null)
              {
                final JMethod aSetter = jClass.method (JMod.PUBLIC, aParamType, aMethod.name ());
                aSetter.annotate (Nonnull.class);
                final JVar aParam = aSetter.param (JMod.FINAL, aNewType, "valueParam");
                aParam.annotate (Nullable.class);
                final JVar aObj = aSetter.body ()
                                         .decl (aParamType,
                                                "aObj",
                                                JExpr.invoke ("get" + aMethod.name ().substring (3)));
                final JConditional aIf = aSetter.body ()._if (aObj.eq (JExpr._null ()));
                aIf._then ().assign (aObj, JExpr._new (aParamType).arg (aParam));
                aIf._then ().invoke (aMethod).arg (aObj);
                aIf._else ().invoke (aObj, "setValue").arg (aParam);
                aSetter.body ()._return (aObj);
                aSetter.javadoc ().add ("Special setter with value of type " + aParam.type ().name ());
                aSetter.javadoc ().addParam (aParam).add ("The value to be set. May be <code>null</code>.");
                aSetter.javadoc ()
                       .addReturn ()
                       .add ("The created intermediary object of type " +
                             aParamType.name () +
                             " and never <code>null</code>");
                aSetter.javadoc ().add (AUTHOR);
              }
            }
          }
        }
    }
  }

  @Nonnull
  @ReturnsMutableCopy
  private ICommonsMap <JClass, JType> _addValueCtorsAndSetters (@Nonnull final Outline aOutline,
                                                                final boolean bHasPluginOffsetDT)
  {
    final SuperClassMap aAllSuperClassNames = new SuperClassMap (aOutline);

    final ICommonsMap <JClass, JType> aAllCtorClasses = new CommonsHashMap <> ();

    // Check all defined classes
    final ICommonsSet <ClassOutline> aRemainingOutlineClasses = new CommonsTreeSet <> (Comparator.comparing (x -> x.getImplClass ()
                                                                                                                   .name ()));
    aRemainingOutlineClasses.addAll (aOutline.getClasses ());
    for (final ClassOutline aClassOutline : aRemainingOutlineClasses)
    {
      final JDefinedClass jClass = aClassOutline.implClass;

      final JType aValueType = aAllSuperClassNames.getValueFieldType (jClass);
      if (aValueType != null)
      {
        logDebug ( () -> "  New value ctor '" + jClass.name () + "(" + aValueType.name () + ")'");

        // Create constructor with value (if available)
        {
          final JMethod aValueCtor = jClass.constructor (JMod.PUBLIC);
          final JVar aParam = aValueCtor.param (JMod.FINAL, aValueType, "valueParam");
          if (!aValueType.isPrimitive ())
            aParam.annotate (Nullable.class);
          aValueCtor.body ().invoke ("setValue").arg (aParam);
          aValueCtor.javadoc ().add ("Constructor for value of type " + aValueType.erasure ().name ());
          aValueCtor.javadoc ()
                    .addParam (aParam)
                    .add ("The value to be set." + (aValueType.isPrimitive () ? "" : " May be <code>null</code>."));
          aValueCtor.javadoc ().add (AUTHOR);
        }

        if (bHasPluginOffsetDT)
        {
          final JType aNewType = PluginOffsetDTExtension.getOtherType (aValueType, aOutline.getCodeModel ());
          if (aNewType != null)
          {
            final JMethod aValueCtor = jClass.constructor (JMod.PUBLIC);
            final JVar aParam = aValueCtor.param (JMod.FINAL, aNewType, "valueParam");
            aParam.annotate (Nullable.class);
            aValueCtor.body ().invoke ("setValue").arg (aParam);
            aValueCtor.javadoc ().add ("Constructor for value of type " + aNewType.name ());
            aValueCtor.javadoc ().addParam (aParam).add ("The value to be set. May be <code>null</code>.");
            aValueCtor.javadoc ().add (AUTHOR);
          }
        }

        // Set constructor in all derived classes
        final ICommonsSet <JClass> aAllRelevantClasses = new CommonsHashSet <> ();
        aAllRelevantClasses.add (jClass);

        // Add the constructor in all derived classes
        _recursiveAddValueConstructorToDerivedClasses (aOutline,
                                                       jClass,
                                                       aValueType,
                                                       aAllRelevantClasses,
                                                       bHasPluginOffsetDT);

        for (final JClass jRelevantClass : aAllRelevantClasses)
          aAllCtorClasses.put (jRelevantClass, aValueType);

        // Add additional setters in all classes that have a setter with one of
        // the relevant classes
        _addValueSetterInUsingClasses (aOutline, aValueType, aAllRelevantClasses, bHasPluginOffsetDT);
      }
      else
      {
        logDebug ( () -> "[" +
                         jClass.getClass ().getSimpleName () +
                         "] " +
                         jClass.fullName () +
                         " is not a value-based class");
      }
    }

    return aAllCtorClasses;
  }

  private static boolean _containsMethodWithoutParams (@Nonnull final Collection <JMethod> aMethods,
                                                       @Nonnull final String sMethodName)
  {
    for (final JMethod aMethod : aMethods)
      if (aMethod.name ().equals (sMethodName) && aMethod.params ().isEmpty ())
        return true;
    return false;
  }

  /**
   * Create all getter
   *
   * @param aOutline
   *        JAXB outline
   * @param aAllCtorClasses
   *        Map from class with value (direct and derived) to value type
   * @param bHasPluginOffsetDT
   *        <code>true</code> if the "OffsetDTExtension" plugin is present
   */
  private void _addValueGetter (@Nonnull final Outline aOutline,
                                @Nonnull final Map <JClass, JType> aAllCtorClasses,
                                final boolean bHasPluginOffsetDT)
  {
    final JCodeModel aCodeModel = aOutline.getCodeModel ();
    // For all generated classes
    for (final ClassOutline aClassOutline : aOutline.getClasses ())
    {
      // Get the implementation class
      final JDefinedClass jClass = aClassOutline.implClass;
      // For all methods in the class (copy!)
      for (final JMethod aMethod : new CommonsArrayList <> (jClass.methods ()))
        if (aMethod.name ().startsWith ("get") && aMethod.params ().isEmpty ())
        {
          final JType aReturnType = aMethod.type ();
          final JType aValueType = aAllCtorClasses.get (aReturnType);
          if (aValueType != null)
          {
            final boolean bIsBoolean = aValueType == aCodeModel.BOOLEAN;
            final String sMethodName;
            if (bIsBoolean)
              sMethodName = "is" + aMethod.name ().substring (3) + "Value";
            else
              sMethodName = aMethod.name () + "Value";
            if (_containsMethodWithoutParams (jClass.methods (), sMethodName))
            {
              // This can happen if an XSD contains the element "X" and
              // "XValue" in the same type.
              // Noticed in CII D16B for BasicWorkItemType with "Index" and
              // "IndexValue" elements
              logWarn ("Another method with name '" +
                       sMethodName +
                       "' and no parameters is already present in class '" +
                       jClass.name () +
                       "' - not creating it.");
              continue;
            }

            logDebug ( () -> "  New value getter '" +
                             aValueType.name () +
                             " " +
                             jClass.name () +
                             "." +
                             sMethodName +
                             "'");

            // The return type is a generated class
            if (aValueType.isPrimitive ())
            {
              final JMethod aGetter;
              final JVar aParam;
              if (bIsBoolean)
              {
                // Create the boolean is...Value() method
                aGetter = jClass.method (JMod.PUBLIC, aValueType, sMethodName);
                aParam = aGetter.param (JMod.FINAL, aValueType, "nullValue");
                final JVar aObj = aGetter.body ().decl (aReturnType, "aObj", JExpr.invoke (aMethod));
                aGetter.body ()._return (MyTernaryOp.cond (aObj.eq (JExpr._null ()), aParam, aObj.invoke ("isValue")));
              }
              else
              {
                // Create the byte/char/double/float/int/long/short
                // get...Value() method
                aGetter = jClass.method (JMod.PUBLIC, aValueType, sMethodName);
                aParam = aGetter.param (JMod.FINAL, aValueType, "nullValue");
                final JVar aObj = aGetter.body ().decl (aReturnType, "aObj", JExpr.invoke (aMethod));
                aGetter.body ()._return (MyTernaryOp.cond (aObj.eq (JExpr._null ()), aParam, aObj.invoke ("getValue")));
              }

              // Javadoc
              aGetter.javadoc ().add ("Get the value of the contained " + aReturnType.name () + " object");
              aGetter.javadoc ()
                     .addParam (aParam)
                     .add ("The value to be returned, if the owning object is <code>null</code>");
              aGetter.javadoc ()
                     .addReturn ()
                     .add ("Either the value of the contained " +
                           aReturnType.name () +
                           " object or the passed " +
                           aParam.name ());
              aGetter.javadoc ().add (AUTHOR);
            }
            else
            {
              // Create the Object get...Value() method
              {
                final JMethod aGetter = jClass.method (JMod.PUBLIC, aValueType, sMethodName);
                aGetter.annotate (Nullable.class);
                final JVar aObj = aGetter.body ().decl (aReturnType, "aObj", JExpr.invoke (aMethod));
                aGetter.body ()
                       ._return (MyTernaryOp.cond (aObj.eq (JExpr._null ()), JExpr._null (), aObj.invoke ("getValue")));
                aGetter.javadoc ().add ("Get the value of the contained " + aReturnType.name () + " object");
                aGetter.javadoc ()
                       .addReturn ()
                       .add ("Either the value of the contained " +
                             aReturnType.name () +
                             " object or <code>null</code>");
                aGetter.javadoc ().add (AUTHOR);
              }

              if (bHasPluginOffsetDT)
              {
                final JType aNewType = PluginOffsetDTExtension.getOtherType (aValueType, aCodeModel);
                if (aNewType != null)
                {
                  final JMethod aGetter = jClass.method (JMod.PUBLIC, aNewType, sMethodName + "Local");
                  aGetter.annotate (Nullable.class);
                  final JVar aObj = aGetter.body ().decl (aReturnType, "aObj", JExpr.invoke (aMethod));
                  aGetter.body ()
                         ._return (MyTernaryOp.cond (aObj.eq (JExpr._null ()),
                                                     JExpr._null (),
                                                     aObj.invoke ("getValueLocal")));
                  aGetter.javadoc ().add ("Get the value of the contained " + aReturnType.name () + " object");
                  aGetter.javadoc ()
                         .addReturn ()
                         .add ("Either the value of the contained " +
                               aReturnType.name () +
                               " object or <code>null</code>");
                  aGetter.javadoc ().add (AUTHOR);
                }
              }
            }
          }
        }
    }
  }

  /**
   * Main method to create methods for value: constructors, derived
   * constructors, setter and getter.
   *
   * @param aOutline
   *        JAXB Outline
   * @param aOpts
   *        Options
   * @param aErrorHandler
   *        Error handler
   */
  @Override
  public boolean run (@Nonnull final Outline aOutline,
                      @Nonnull final Options aOpts,
                      @Nonnull final ErrorHandler aErrorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    // Check if the "Plugin OffsetDT plugin" is also registered
    final boolean bHasPluginOffsetDT = CollectionHelper.containsAny (aOpts.getAllPlugins (),
                                                                     p -> p.getOptionName ()
                                                                           .equals (PluginOffsetDTExtension.OPT));
    if (bHasPluginOffsetDT)
      logInfo ("  Found OffsetDTExtension plugin");

    _addDefaultCtors (aOutline);

    final ICommonsMap <JClass, JType> aAllCtorClasses = _addValueCtorsAndSetters (aOutline, bHasPluginOffsetDT);

    // Create all getters
    _addValueGetter (aOutline, aAllCtorClasses, bHasPluginOffsetDT);
    logInfo ("  Finished JAXB plugin -" + getOptionName ());
    return true;
  }
}
