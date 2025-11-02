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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ErrorHandler;

import com.helger.base.numeric.BigHelper;
import com.helger.base.string.StringParser;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CAttributePropertyInfo;
import com.sun.tools.xjc.model.CElementPropertyInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.CReferencePropertyInfo;
import com.sun.tools.xjc.model.CValuePropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.impl.AttributeUseImpl;
import com.sun.xml.xsom.impl.ElementDecl;
import com.sun.xml.xsom.impl.RestrictionSimpleTypeImpl;
import com.sun.xml.xsom.impl.parser.DelayedRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * big thanks to original author: cocorossello
 *
 * @author Philip Helger
 */
public abstract class AbstractPluginBeanValidation extends AbstractPlugin
{
  private static final BigInteger UNBOUNDED = BigInteger.valueOf (XSParticle.UNBOUNDED);
  private static final String [] NUMBER_TYPES = { "BigDecimal",
                                                  "BigInteger",
                                                  "String",
                                                  "byte",
                                                  "short",
                                                  "int",
                                                  "long" };

  // JSR 303 = Bean Validation 1.0
  // JSR 349 = Bean Validation 1.1
  private final boolean m_bJSR349;

  protected AbstractPluginBeanValidation (final boolean bValidation10)
  {
    m_bJSR349 = !bValidation10;
  }

  @Override
  public boolean run (final Outline aModel, final Options aOpts, final ErrorHandler errorHandler)
  {
    initPluginLogging (aOpts.debugMode);
    logInfo ("Running JAXB plugin -" + getOptionName ());

    try
    {
      for (final ClassOutline aClassOutline : aModel.getClasses ())
      {
        final List <CPropertyInfo> aPropertyInfos = aClassOutline.target.getProperties ();
        for (final CPropertyInfo aPropertyInfo : aPropertyInfos)
        {
          if (aPropertyInfo instanceof CElementPropertyInfo)
            _processElementProperty ((CElementPropertyInfo) aPropertyInfo, aClassOutline);
          else
            if (aPropertyInfo instanceof CAttributePropertyInfo)
              _processAttributeProperty ((CAttributePropertyInfo) aPropertyInfo, aClassOutline);
            else
              if (aPropertyInfo instanceof CValuePropertyInfo)
                _processValueProperty ((CValuePropertyInfo) aPropertyInfo, aClassOutline);
              else
                if (aPropertyInfo instanceof CReferencePropertyInfo)
                {
                  // Ignore
                }
                else
                  logWarn ("Unsupported property: " + aPropertyInfo);
        }
      }

      return true;
    }
    catch (final Exception ex)
    {
      logError ("Internal error creating bean validation", ex);
      return false;
    }
  }

  /*
   * XS:Element
   */
  private void _processElementProperty (@NonNull final CElementPropertyInfo aElement,
                                        @NonNull final ClassOutline aClassOutline)
  {
    // It's a ParticleImpl
    final XSParticle aParticle = (XSParticle) aElement.getSchemaComponent ();
    final JCodeModel aCM = aClassOutline.implClass.owner ();
    final BigInteger aMinOccurs = aParticle.getMinOccurs ();
    final BigInteger aMaxOccurs = aParticle.getMaxOccurs ();
    final JFieldVar aField = aClassOutline.implClass.fields ().get (aElement.getName (false));

    // workaround for choices
    final boolean bRequired = aElement.isRequired ();
    if (BigHelper.isLT0 (aMinOccurs) || (BigHelper.isGE1 (aMinOccurs) && bRequired))
    {
      if (!_hasAnnotation (aField, NotNull.class))
        aField.annotate (NotNull.class);
    }
    if (aMaxOccurs.compareTo (BigInteger.ONE) > 0)
    {
      if (!_hasAnnotation (aField, Size.class))
      {
        aField.annotate (Size.class).param ("min", aMinOccurs.intValue ()).param ("max", aMaxOccurs.intValue ());
      }
    }
    if (UNBOUNDED.equals (aMaxOccurs) && BigHelper.isGT0 (aMinOccurs))
    {
      if (!_hasAnnotation (aField, Size.class))
      {
        aField.annotate (Size.class).param ("min", aMinOccurs.intValue ());
      }
    }

    // For all collection types
    // For all types of generated classes
    final String sErasureFullName = aField.type ().erasure ().fullName ();
    if (aField.type ().isArray () ||
        sErasureFullName.equals ("java.util.Collection") ||
        sErasureFullName.equals ("java.util.Set") ||
        sErasureFullName.equals ("java.util.List") ||
        sErasureFullName.equals ("java.util.Map") ||
        aField.type () instanceof JDefinedClass)
    {
      // Complex type requires @Valid for nested validation
      if (!_hasAnnotation (aField, Valid.class))
        aField.annotate (Valid.class);
    }

    if (false)
    {
      // Enumerate all existing classes
      final Iterator <JPackage> itp = aCM.packages ();
      while (itp.hasNext ())
      {
        final JPackage p = itp.next ();
        final Iterator <JDefinedClass> cit = p.classes ();
        while (cit.hasNext ())
        {
          final JDefinedClass c = cit.next ();
          logInfo ("  " + c.fullName ());
        }
      }
    }

    final XSTerm aTerm = aParticle.getTerm ();
    if (aTerm instanceof ElementDecl)
      _processElement (aField, (ElementDecl) aTerm);
    else
      if (aTerm instanceof DelayedRef.Element)
      {
        final XSElementDecl xsElementDecl = ((DelayedRef.Element) aTerm).get ();
        _processElement (aField, (ElementDecl) xsElementDecl);
      }
      else
        if (aTerm instanceof XSModelGroup)
        {
          if (false)
          {
            final XSParticle [] c = ((XSModelGroup) aTerm).getChildren ();
            logInfo ("XSModelGroup children are: '" + Arrays.toString (c) + "'");
          }
        }
        else
          logWarn ("Unsupported particle term '" + aTerm + "'");
  }

  private void _processElement (@NonNull final JFieldVar aField, final ElementDecl aElement)
  {
    final XSType aElementType = aElement.getType ();

    if (aElementType instanceof XSSimpleType)
      _processType ((XSSimpleType) aElementType, aField);
    else
      if (aElementType.getBaseType () instanceof XSSimpleType)
        _processType ((XSSimpleType) aElementType.getBaseType (), aField);
  }

  private boolean _isSizeAnnotationApplicable (@NonNull final JFieldVar aField)
  {
    return aField.type ().name ().equals ("String") || aField.type ().isArray ();
  }

  private void _processType (final XSSimpleType aSimpleType, @NonNull final JFieldVar aField)
  {
    if (!_hasAnnotation (aField, Size.class) && _isSizeAnnotationApplicable (aField))
    {
      final XSFacet fMaxLength = aSimpleType.getFacet ("maxLength");
      final Integer aMaxLength = fMaxLength == null ? null : StringParser.parseIntObj (fMaxLength.getValue ().value);
      final XSFacet fMinLength = aSimpleType.getFacet ("minLength");
      final Integer aMinLength = fMinLength == null ? null : StringParser.parseIntObj (fMinLength.getValue ().value);
      if (aMinLength != null)
      {
        if (aMaxLength != null)
          aField.annotate (Size.class).param ("min", aMinLength.intValue ()).param ("max", aMaxLength.intValue ());
        else
          aField.annotate (Size.class).param ("min", aMinLength.intValue ());
      }
      else
      {
        if (aMaxLength != null)
          aField.annotate (Size.class).param ("max", aMaxLength.intValue ());
        // else neither nor
      }
    }

    /**
     * <annox:annotate annox:class="jakarta.validation.constraints.Pattern" message= "Name can only
     * contain capital letters, numbers and the symbols '-', '_', '/', ' '" regexp=
     * "^[A-Z0-9_\s//-]*" />
     */
    final XSFacet aFacetPattern = aSimpleType.getFacet ("pattern");
    if (aFacetPattern != null)
    {
      final String sPattern = aFacetPattern.getValue ().value;
      // cxf-codegen fix
      if (!"\\c+".equals (sPattern))
      {
        // Note: flags like "multiline" or "case insensitive" are not supported
        // in XSD. See e.g. https://www.regular-expressions.info/xml.html
        if (!_hasAnnotation (aField, Pattern.class))
          aField.annotate (Pattern.class).param ("regexp", sPattern);
      }
    }

    if (_isNumericType (aField))
    {
      final XSFacet aMaxInclusive = aSimpleType.getFacet ("maxInclusive");
      if (aMaxInclusive != null && _isValidMinMaxValue (aMaxInclusive) && !_hasAnnotation (aField, DecimalMax.class))
      {
        aField.annotate (DecimalMax.class).param ("value", aMaxInclusive.getValue ().value);
      }

      final XSFacet aMinInclusive = aSimpleType.getFacet ("minInclusive");
      if (aMinInclusive != null && _isValidMinMaxValue (aMinInclusive) && !_hasAnnotation (aField, DecimalMin.class))
      {
        aField.annotate (DecimalMin.class).param ("value", aMinInclusive.getValue ().value);
      }

      final XSFacet aMaxExclusive = aSimpleType.getFacet ("maxExclusive");
      if (aMaxExclusive != null && _isValidMinMaxValue (aMaxExclusive) && !_hasAnnotation (aField, DecimalMax.class))
      {
        final JAnnotationUse aAnnotation = aField.annotate (DecimalMax.class);
        aAnnotation.param ("value", aMaxExclusive.getValue ().value);
        if (m_bJSR349)
          aAnnotation.param ("inclusive", false);
      }
      final XSFacet aMinExclusive = aSimpleType.getFacet ("minExclusive");
      if (aMinExclusive != null && _isValidMinMaxValue (aMinExclusive) && !_hasAnnotation (aField, DecimalMin.class))
      {
        final JAnnotationUse aAnnotation = aField.annotate (DecimalMin.class);
        aAnnotation.param ("value", aMinExclusive.getValue ().value);
        if (m_bJSR349)
          aAnnotation.param ("inclusive", false);
      }
    }

    final XSFacet aXSTotalDigits = aSimpleType.getFacet ("totalDigits");
    final XSFacet aXSFractionDigits = aSimpleType.getFacet ("fractionDigits");
    final Integer aTotalDigits = aXSTotalDigits == null ? null : StringParser.parseIntObj (aXSTotalDigits
                                                                                                         .getValue ().value);
    final Integer aFractionDigits = aXSFractionDigits == null ? null : StringParser.parseIntObj (aXSFractionDigits
                                                                                                                  .getValue ().value);
    if (!_hasAnnotation (aField, Digits.class) && aTotalDigits != null)
    {
      final JAnnotationUse aAnnotDigits = aField.annotate (Digits.class);
      if (aFractionDigits == null)
        aAnnotDigits.param ("integer", aTotalDigits.intValue ());
      else
      {
        aAnnotDigits.param ("integer", aTotalDigits.intValue () - aFractionDigits.intValue ());
        aAnnotDigits.param ("fraction", aFractionDigits.intValue ());
      }
    }
  }

  /*
   * attribute from parent declaration
   */
  private void _processValueProperty (@NonNull final CValuePropertyInfo aProperty, final ClassOutline aClassOutline)
  {
    final String sPropertyName = aProperty.getName (false);

    final XSComponent aDefinition = aProperty.getSchemaComponent ();
    if (aDefinition instanceof RestrictionSimpleTypeImpl)
    {
      final RestrictionSimpleTypeImpl aParticle = (RestrictionSimpleTypeImpl) aDefinition;
      final XSSimpleType aSimpleType = aParticle.asSimpleType ();
      final JFieldVar aFieldVar = aClassOutline.implClass.fields ().get (sPropertyName);

      // if (particle.isRequired()) {
      // if (!hasAnnotation(var, NotNull.class)) {
      // if (notNullAnnotations) {
      // var.annotate(NotNull.class);
      // }
      // }
      // }

      _processType (aSimpleType, aFieldVar);
    }
  }

  /*
   * XS:Attribute
   */
  private void _processAttributeProperty (final CAttributePropertyInfo aPropertyInfo, final ClassOutline aClassOutline)
  {
    final String sPropertyName = aPropertyInfo.getName (false);

    final XSComponent aDefinition = aPropertyInfo.getSchemaComponent ();
    final AttributeUseImpl aParticle = (AttributeUseImpl) aDefinition;
    final XSSimpleType type = aParticle.getDecl ().getType ();

    final JFieldVar aFieldVar = aClassOutline.implClass.fields ().get (sPropertyName);
    if (aParticle.isRequired () && !_hasAnnotation (aFieldVar, NotNull.class))
      aFieldVar.annotate (NotNull.class);

    _processType (type, aFieldVar);
  }

  private static boolean _isEqualStr (final long nVal, @Nullable final String sValue)
  {
    return Long.toString (nVal).equals (sValue);
  }

  private static boolean _isValidMinMaxValue (@NonNull final XSFacet aFacet)
  {
    final String sValue = aFacet.getValue ().value;
    // cxf-codegen puts max and min as value when there is not anything defined
    // in wsdl.
    return sValue != null &&
           !_isEqualStr (Long.MAX_VALUE, sValue) &&
           !_isEqualStr (Integer.MAX_VALUE, sValue) &&
           !_isEqualStr (Long.MIN_VALUE, sValue) &&
           !_isEqualStr (Integer.MIN_VALUE, sValue);
  }

  private static boolean _hasAnnotation (@NonNull final JFieldVar aField, @NonNull final Class <?> aAnnotationClass)
  {
    final Collection <JAnnotationUse> aAnnotations = aField.annotations ();
    if (aAnnotations != null)
    {
      final String sSearchName = aAnnotationClass.getCanonicalName ();
      for (final JAnnotationUse annotationUse : aAnnotations)
        if (annotationUse.getAnnotationClass ().fullName ().equals (sSearchName))
          return true;
    }
    return false;
  }

  private static boolean _isNumericType (@NonNull final JFieldVar aFieldVar)
  {
    final JType aFieldType = aFieldVar.type ();
    for (final String sType : NUMBER_TYPES)
      if (sType.equalsIgnoreCase (aFieldType.name ()))
        return true;

    try
    {
      final Class <?> aClass = Class.forName (aFieldType.fullName ());
      return aClass != null && Number.class.isAssignableFrom (aClass);
    }
    catch (final Exception e)
    {
      // whatever
    }
    return false;
  }
}
