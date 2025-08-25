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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.xml.sax.ErrorHandler;

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.reflection.GenericReflection;
import com.helger.collection.CollectionFind;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.CommonsTreeSet;
import com.helger.collection.commons.ICommonsNavigableMap;
import com.helger.collection.commons.ICommonsNavigableSet;
import com.helger.collection.commons.ICommonsSet;
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Add special "value" constructors, setters and getters for JAXB generated elements. This is used
 * e.g. for UBL and CII code generation.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public class PluginValueExtender extends AbstractPlugin
{
  private static final String FIELD_VALUE = "value";

  /**
   * Map from class name to the Codemodel type of the "value" field
   *
   * @author Philip Helger
   */
  final class ClassNameValueFieldTypeMap extends CommonsHashMap <String, JType>
  {
    private void _recursiveFill (@Nonnull final ICommonsSet <String> aHandledClasses,
                                 @Nonnull final JCodeModel cm,
                                 @Nonnull final JType jType)
    {
      final String sClassFullName = jType.fullName ();

      // Ignore all system super classes
      if (sClassFullName.startsWith ("java."))
        return;

      // Handle each class only once
      if (!aHandledClasses.add (sClassFullName))
        return;

      if (false)
        logDebug ( () -> "!!" + jType.getClass ().getSimpleName () + " -- " + sClassFullName);

      // We don't care about the classes we created
      // Take only "external", meaning non-generated super classes
      if (jType instanceof JDefinedClass)
      {
        final JDefinedClass jdClass = (JDefinedClass) jType;
        logDebug ( () -> "  Scanning defined super class '" + sClassFullName + "'");
        for (final Map.Entry <String, JFieldVar> aFieldEntry : jdClass.fields ().entrySet ())
          if (aFieldEntry.getKey ().equals (FIELD_VALUE))
          {
            put (sClassFullName, aFieldEntry.getValue ().type ());
            logDebug ( () -> "    Found value field of type '" + aFieldEntry.getValue ().type ().name () + "'");
            break;
          }

        // Use super class of super class
        _recursiveFill (aHandledClasses, cm, jdClass._extends ());
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
          for (final Field aField : aSuperClass.getDeclaredFields ())
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

    public ClassNameValueFieldTypeMap (@Nonnull final Outline aOutline)
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
     * Get the {@link JType} of the <code>value</code> field in the provided class or any super
     * class (<code>extends</code>)
     *
     * @param jClass
     *        Class to inspect
     * @return <code>null</code> if none was found
     */
    @Nullable
    public JType getValueFieldTypeIncludeHierarchy (@Nonnull final JDefinedClass jClass)
    {
      JType aValueType = null;

      // Check if that class has a "value" member (name of the variable
      // created by JAXB to indicate the content of an XML element)
      for (final JFieldVar aField : jClass.fields ().values ())
        if (aField.name ().equals (FIELD_VALUE))
        {
          aValueType = aField.type ();
          logDebug ( () -> "    [" + aField.type ().name () + " " + FIELD_VALUE + "] found directly in class");
          break;
        }

      // Find any super class with a value field
      JClass jCurClass = jClass;
      int nLevel = 1;
      while (jCurClass != null && aValueType == null)
      {
        jCurClass = jCurClass._extends ();
        if (jCurClass != null)
        {
          // Check only super classes that are not defined in this generation
          // run but e.g. imported via episodes (bindings)
          aValueType = get (jCurClass.fullName ());
          if (aValueType != null)
          {
            // Make final vars
            final JType vtFinal = aValueType;
            final JClass clsFinal = jCurClass;
            final int lvlFinal = nLevel;
            logDebug ( () -> "    [" +
                             vtFinal.name () +
                             " " +
                             FIELD_VALUE +
                             "] found in parent[" +
                             lvlFinal +
                             "] class '" +
                             clsFinal.name () +
                             "'");
          }
        }
        ++nLevel;
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

  private static final Comparator <ClassOutline> COMP_CO = Comparator.comparing (x -> x.getImplClass ().fullName ());

  @Nonnull
  private static ICommonsNavigableSet <ClassOutline> _getSortedClassOutlines (@Nonnull final Outline aOutline)
  {
    final ICommonsNavigableSet <ClassOutline> aAllOutlineClasses = new CommonsTreeSet <> (COMP_CO);
    aAllOutlineClasses.addAll (aOutline.getClasses ());
    return aAllOutlineClasses;
  }

  private void _addDefaultCtors (@Nonnull final Outline aOutline)
  {
    // Add default constructors to all classes, because when adding other
    // constructors, the "default" one need to be added explicitly
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

  @Nonnull
  @ReturnsMutableCopy
  private ICommonsNavigableMap <String, JType> _addValueCtors (@Nonnull final Outline aOutline,
                                                               final boolean bHasPluginOffsetDT)
  {
    final JCodeModel cm = aOutline.getCodeModel ();

    // The map from class name to value field type
    final ClassNameValueFieldTypeMap aClassValueFieldTypeMap = new ClassNameValueFieldTypeMap (aOutline);

    // Return map from class to value field type
    final ICommonsNavigableMap <String, JType> ret = new CommonsTreeMap <> ();

    logDebug ( () -> "Start creating value ctors");

    // Check all defined classes
    for (final ClassOutline aClassOutline : _getSortedClassOutlines (aOutline))
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      final String sClassFullName = jClass.fullName ();

      logDebug ( () -> "  Handling class '" + sClassFullName + "'");

      final JType aValueType = aClassValueFieldTypeMap.getValueFieldTypeIncludeHierarchy (jClass);
      if (aValueType != null)
      {
        // We have a "value" field in the hierarchy

        // Create constructor with value (if available)
        {
          logDebug ( () -> "    New value ctor '" + jClass.name () + "(" + aValueType.name () + ")'");

          final JMethod aValueCtor = jClass.constructor (JMod.PUBLIC);
          final JVar aParam = aValueCtor.param (JMod.FINAL, aValueType, "valueParam");
          if (!aValueType.isPrimitive ())
            aParam.annotate (Nullable.class);
          // Just call "setValue" in the constructor
          aValueCtor.body ().invoke ("setValue").arg (aParam);
          aValueCtor.javadoc ().add ("Constructor for value of type " + aValueType.erasure ().name ());
          aValueCtor.javadoc ()
                    .addParam (aParam)
                    .add ("The value to be set." + (aValueType.isPrimitive () ? "" : " May be <code>null</code>."));
          aValueCtor.javadoc ().add (AUTHOR);
        }

        if (bHasPluginOffsetDT)
        {
          final JType aSecondaryValueType = PluginOffsetDTExtension.getSecondaryDataType (aValueType, cm);
          if (aSecondaryValueType != null)
          {
            logDebug ( () -> "    New value ctor '" + jClass.name () + "(" + aSecondaryValueType.name () + ")'");

            final JMethod aValueCtor = jClass.constructor (JMod.PUBLIC);
            final JVar aParam = aValueCtor.param (JMod.FINAL, aSecondaryValueType, "valueParam");
            aParam.annotate (Nullable.class);
            // Just call "setValue" in the constructor
            aValueCtor.body ().invoke ("setValue").arg (aParam);
            aValueCtor.javadoc ().add ("Constructor for value of type " + aSecondaryValueType.name ());
            aValueCtor.javadoc ().addParam (aParam).add ("The value to be set. May be <code>null</code>.");
            aValueCtor.javadoc ().add (AUTHOR);
          }
        }

        ret.put (sClassFullName, aValueType);
      }
      else
      {
        logDebug ( () -> "    Found no [" + FIELD_VALUE + "] field");
      }
    }

    return ret;
  }

  private void _addValueSetters (@Nonnull final Outline aOutline,
                                 @Nonnull final ICommonsNavigableMap <String, JType> aAllCtorClasses,
                                 final boolean bHasPluginOffsetDT)
  {
    logDebug ( () -> "Start creating setters for value ctors");

    // For all classes
    for (final ClassOutline aClassOutline : _getSortedClassOutlines (aOutline))
    {
      final JDefinedClass jClass = aClassOutline.implClass;
      final String sClassFullName = jClass.fullName ();

      logDebug ( () -> "  Handling class '" + sClassFullName + "'");

      // Work on a copy of the methods, because they are changed
      for (final JMethod aMethod : new CommonsArrayList <> (jClass.methods ()))
        // Must be a setter
        if (aMethod.name ().startsWith ("set"))
        {
          // Must have exactly 1 parameter that is part of aAllRelevantClasses
          final List <JVar> aParams = aMethod.params ();
          if (aParams.size () != 1)
            continue;

          final JType aParamType = aParams.get (0).type ();
          final JType aValueType = aAllCtorClasses.get (aParamType.fullName ());
          if (aValueType == null)
          {
            logDebug ( () -> "    No setter for '" +
                             aParamType.fullName () +
                             "' because not found in constructor list");
            continue;
          }

          {
            logDebug ( () -> "    New setter '" +
                             aParamType.name () +
                             " " +
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
              final JType aSecondaryValueType = PluginOffsetDTExtension.getSecondaryDataType (aValueType,
                                                                                              aOutline.getCodeModel ());
              if (aSecondaryValueType != null)
              {
                logDebug ( () -> "    New setter '" +
                                 aParamType.name () +
                                 " " +
                                 aMethod.name () +
                                 "(" +
                                 aSecondaryValueType.name () +
                                 ")'");

                final JMethod aSetter = jClass.method (JMod.PUBLIC, aParamType, aMethod.name ());
                aSetter.annotate (Nonnull.class);
                final JVar aParam = aSetter.param (JMod.FINAL, aSecondaryValueType, "valueParam");
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
                                @Nonnull final ICommonsNavigableMap <String, JType> aAllCtorClasses,
                                final boolean bHasPluginOffsetDT)
  {
    final JCodeModel cm = aOutline.getCodeModel ();

    logDebug ( () -> "Start creating setters for value ctors");

    // For all generated classes
    for (final ClassOutline aClassOutline : _getSortedClassOutlines (aOutline))
    {
      // Get the implementation class
      final JDefinedClass jClass = aClassOutline.implClass;
      final String sClassFullName = jClass.fullName ();

      logDebug ( () -> "  Handling class '" + sClassFullName + "'");

      // For all methods in the class (copy!)
      for (final JMethod aMethod : new CommonsArrayList <> (jClass.methods ()))
        if (aMethod.name ().startsWith ("get") && aMethod.params ().isEmpty ())
        {
          final JType aReturnType = aMethod.type ();
          final JType aValueType = aAllCtorClasses.get (aReturnType.fullName ());
          if (aValueType != null)
          {
            final boolean bIsBoolean = aValueType == cm.BOOLEAN;
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

            // The return type is a generated class
            if (aValueType.isPrimitive ())
            {
              logDebug ( () -> "    New value getter '" +
                               aValueType.name () +
                               " " +
                               sMethodName +
                               "(" +
                               aValueType.name () +
                               ")'");

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
                logDebug ( () -> "    New value getter '" + aValueType.name () + " " + sMethodName + "()'");

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
                final JType aSecondaryValueType = PluginOffsetDTExtension.getSecondaryDataType (aValueType, cm);
                if (aSecondaryValueType != null)
                {
                  logDebug ( () -> "    New value getter '" +
                                   aSecondaryValueType.name () +
                                   " " +
                                   sMethodName +
                                   "Local()'");

                  final JMethod aGetter = jClass.method (JMod.PUBLIC, aSecondaryValueType, sMethodName + "Local");
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
   * Main method to create methods for value: constructors, derived constructors, setter and getter.
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
    final boolean bHasPluginOffsetDT = CollectionFind.containsAny (aOpts.getAllPlugins (),
                                                                   p -> p.getOptionName ()
                                                                         .equals (PluginOffsetDTExtension.OPT));
    if (bHasPluginOffsetDT)
      logInfo ("  Found OffsetDTExtension plugin");

    // Must do anyway - so that other ctors can be added
    _addDefaultCtors (aOutline);

    // Create constructors for "value" types
    final ICommonsNavigableMap <String, JType> aAllCtorClasses = _addValueCtors (aOutline, bHasPluginOffsetDT);

    // Create all setters for the new value ctors
    _addValueSetters (aOutline, aAllCtorClasses, bHasPluginOffsetDT);

    // Create all getters
    _addValueGetter (aOutline, aAllCtorClasses, bHasPluginOffsetDT);

    logInfo ("  Finished JAXB plugin -" + getOptionName ());
    return true;
  }
}
