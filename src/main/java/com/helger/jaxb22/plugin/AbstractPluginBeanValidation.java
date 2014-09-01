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

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.xml.sax.ErrorHandler;

import com.helger.commons.collections.ContainerHelper;
import com.helger.commons.math.MathHelper;
import com.helger.commons.string.StringParser;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
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
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.impl.AttributeUseImpl;
import com.sun.xml.xsom.impl.ElementDecl;
import com.sun.xml.xsom.impl.ParticleImpl;
import com.sun.xml.xsom.impl.RestrictionSimpleTypeImpl;
import com.sun.xml.xsom.impl.parser.DelayedRef;

/**
 * big thanks to original author: cocorossello
 * 
 * @author Philip Helger
 */
public abstract class AbstractPluginBeanValidation extends Plugin
{
  private static final BigInteger UNBOUNDED = BigInteger.valueOf (XSParticle.UNBOUNDED);
  private static final String [] NUMBER_TYPES = new String [] { "BigDecimal",
                                                               "BigInteger",
                                                               "String",
                                                               "byte",
                                                               "short",
                                                               "int",
                                                               "long" };

  private boolean m_bJSR349 = false;

  protected AbstractPluginBeanValidation (final boolean bValidation10)
  {
    m_bJSR349 = !bValidation10;
  }

  @Override
  public List <String> getCustomizationURIs ()
  {
    return ContainerHelper.newUnmodifiableList (CJAXB22.NSURI_PH);
  }

  @Override
  public boolean run (final Outline aModel, final Options aOpt, final ErrorHandler errorHandler)
  {
    try
    {
      for (final ClassOutline aClassOutline : aModel.getClasses ())
      {
        final List <CPropertyInfo> aPropertyInfos = aClassOutline.target.getProperties ();
        for (final CPropertyInfo aPropertyInfo : aPropertyInfos)
        {
          if (aPropertyInfo instanceof CElementPropertyInfo)
            _processElement ((CElementPropertyInfo) aPropertyInfo, aClassOutline);
          else
            if (aPropertyInfo instanceof CAttributePropertyInfo)
              _processAttribute ((CAttributePropertyInfo) aPropertyInfo, aClassOutline);
            else
              if (aPropertyInfo instanceof CValuePropertyInfo)
                _processValue ((CValuePropertyInfo) aPropertyInfo, aClassOutline);
              else
                if (aPropertyInfo instanceof CReferencePropertyInfo)
                {

                }
                else
                  System.err.println ("Unsupported property: " + aPropertyInfo);
        }
      }

      return true;
    }
    catch (final Exception e)
    {
      e.printStackTrace ();
      return false;
    }
  }

  /*
   * XS:Element
   */
  private void _processElement (@Nonnull final CElementPropertyInfo aElement, @Nonnull final ClassOutline aClassOutline)
  {
    final ParticleImpl aParticle = (ParticleImpl) aElement.getSchemaComponent ();
    final BigInteger aMinOccurs = aParticle.getMinOccurs ();
    final BigInteger aMaxOccurs = aParticle.getMaxOccurs ();
    final JFieldVar aField = aClassOutline.implClass.fields ().get (aElement.getName (false));

    // workaround for choices
    final boolean bRequired = aElement.isRequired ();
    if (MathHelper.isLowerThanZero (aMinOccurs) || (aMinOccurs.compareTo (BigInteger.ONE) >= 0 && bRequired))
    {
      if (!_hasAnnotation (aField, NotNull.class))
      {
        aField.annotate (NotNull.class);
      }
    }
    if (aMaxOccurs.compareTo (BigInteger.ONE) > 0)
    {
      if (!_hasAnnotation (aField, Size.class))
      {
        aField.annotate (Size.class).param ("min", aMinOccurs.intValue ()).param ("max", aMaxOccurs.intValue ());
      }
    }
    if (UNBOUNDED.equals (aMaxOccurs) && MathHelper.isGreaterThanZero (aMinOccurs))
    {
      if (!_hasAnnotation (aField, Size.class))
      {
        aField.annotate (Size.class).param ("min", aMinOccurs.intValue ());
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
        System.out.println ("Unsupported particle term " + aTerm);
  }

  private void _processElement (@Nonnull final JFieldVar aField, final ElementDecl aElement)
  {
    final XSType aElementType = aElement.getType ();

    if (aElementType instanceof XSSimpleType)
      _processType ((XSSimpleType) aElementType, aField);
    else
      if (aElementType.getBaseType () instanceof XSSimpleType)
        _processType ((XSSimpleType) aElementType.getBaseType (), aField);
  }

  private boolean _isSizeAnnotationApplicable (@Nonnull final JFieldVar aField)
  {
    return aField.type ().name ().equals ("String") || aField.type ().isArray ();
  }

  private void _processType (final XSSimpleType aSimpleType, @Nonnull final JFieldVar aField)
  {
    if (!_hasAnnotation (aField, Size.class) && _isSizeAnnotationApplicable (aField))
    {
      final XSFacet fMaxLength = aSimpleType.getFacet ("maxLength");
      final Integer aMaxLength = fMaxLength == null ? null : StringParser.parseIntObj (fMaxLength.getValue ().value);
      final XSFacet fMinLength = aSimpleType.getFacet ("minLength");
      final Integer aMinLength = fMinLength == null ? null : StringParser.parseIntObj (fMinLength.getValue ().value);
      if (aMaxLength != null && aMinLength != null)
        aField.annotate (Size.class).param ("max", aMaxLength.intValue ()).param ("min", aMinLength.intValue ());
      else
        if (aMinLength != null)
          aField.annotate (Size.class).param ("min", aMinLength.intValue ());
        else
          if (aMaxLength != null)
            aField.annotate (Size.class).param ("max", aMaxLength.intValue ());
    }

    /**
     * <annox:annotate annox:class="javax.validation.constraints.Pattern"
     * message=
     * "Name can only contain capital letters, numbers and the symbols '-', '_', '/', ' '"
     * regexp="^[A-Z0-9_\s//-]*" />
     */
    if (aSimpleType.getFacet ("pattern") != null)
    {
      final String sPattern = aSimpleType.getFacet ("pattern").getValue ().value;
      // cxf-codegen fix
      if (!"\\c+".equals (sPattern))
      {
        if (!_hasAnnotation (aField, Pattern.class))
        {
          aField.annotate (Pattern.class).param ("regexp", sPattern);
        }
      }
    }

    if (_isNumericType (aField))
    {
      final XSFacet aMaxInclusive = aSimpleType.getFacet ("maxInclusive");
      if (aMaxInclusive != null && _isValidValue (aMaxInclusive) && !_hasAnnotation (aField, DecimalMax.class))
      {
        aField.annotate (DecimalMax.class).param ("value", aMaxInclusive.getValue ().value);
      }

      final XSFacet aMinInclusive = aSimpleType.getFacet ("minInclusive");
      if (aMinInclusive != null && _isValidValue (aMinInclusive) && !_hasAnnotation (aField, DecimalMin.class))
      {
        aField.annotate (DecimalMin.class).param ("value", aMinInclusive.getValue ().value);
      }

      final XSFacet aMaxExclusive = aSimpleType.getFacet ("maxExclusive");
      if (aMaxExclusive != null && _isValidValue (aMaxExclusive) && !_hasAnnotation (aField, DecimalMax.class))
      {
        final JAnnotationUse aAnnotation = aField.annotate (DecimalMax.class);
        aAnnotation.param ("value", aMaxExclusive.getValue ().value);
        if (m_bJSR349)
          aAnnotation.param ("inclusive", false);
      }
      final XSFacet aMinExclusive = aSimpleType.getFacet ("minExclusive");
      if (aMinExclusive != null && _isValidValue (aMinExclusive) && !_hasAnnotation (aField, DecimalMin.class))
      {
        final JAnnotationUse aAnnotation = aField.annotate (DecimalMin.class);
        aAnnotation.param ("value", aMinExclusive.getValue ().value);
        if (m_bJSR349)
          aAnnotation.param ("inclusive", false);
      }
    }

    final XSFacet aXSTotalDigits = aSimpleType.getFacet ("totalDigits");
    final XSFacet aXSFractionDigits = aSimpleType.getFacet ("fractionDigits");
    final Integer aTotalDigits = aXSTotalDigits == null ? null
                                                       : StringParser.parseIntObj (aXSTotalDigits.getValue ().value);
    final Integer aFractionDigits = aXSFractionDigits == null ? null
                                                             : StringParser.parseIntObj (aXSFractionDigits.getValue ().value);
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
  private void _processValue (@Nonnull final CValuePropertyInfo aProperty, final ClassOutline aClassOutline)
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
  private void _processAttribute (final CAttributePropertyInfo aPropertyInfo, final ClassOutline aClassOutline)
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

  private static boolean _isEqual (final long nVal, @Nullable final String sValue)
  {
    return Long.toString (nVal).equals (sValue);
  }

  private static boolean _isValidValue (@Nonnull final XSFacet aFacet)
  {
    final String value = aFacet.getValue ().value;
    // cxf-codegen puts max and min as value when there is not anything defined
    // in wsdl.
    return value != null &&
           !(_isEqual (Long.MAX_VALUE, value) ||
             _isEqual (Integer.MAX_VALUE, value) ||
             _isEqual (Long.MIN_VALUE, value) || _isEqual (Integer.MIN_VALUE, value));
  }

  private static boolean _hasAnnotation (@Nonnull final JFieldVar aField, @Nonnull final Class <?> aAnnotationClass)
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

  private static boolean _isNumericType (@Nonnull final JFieldVar aFieldVar)
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
