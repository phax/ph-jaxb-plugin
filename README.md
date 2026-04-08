# ph-jaxb-plugin

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger/ph-jaxb-plugin/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger/ph-jaxb-plugin/)
[![javadoc](https://javadoc.io/badge2/com.helger/ph-jaxb-plugin/javadoc.svg)](https://javadoc.io/doc/com.helger/ph-jaxb-plugin)
<!-- ph-badge-end -->

JAXB 4.0.x plugin that adds some commonly needed functionality.

* Version 5.x requires Java 17 and builds on ph-commons v12.
* Version 4.0.3 was the last version requiring Java 11 and builds on ph-commons v11.
* The old version for JAXB 2.2 was called `ph-jaxb22-plugin`

This project is licensed under the Apache 2 license.

# Maven usage

Add something **like** the following to your pom.xml to use this artifact:

```xml
<plugin>
  <groupId>org.jvnet.jaxb</groupId>
  <artifactId>jaxb-maven-plugin</artifactId>
  <version>4.0.11</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <!-- regular plugin configuration goes here -->
    <args>
      <!-- other direct arguments like -no-header -->
      <arg>-Xph-default-locale</arg>
      <arg>en_US</arg>
      <arg>-Xph-annotate</arg>
      <arg>-Xph-fields-private</arg>
      <arg>-Xph-code-quality</arg>
      <arg>-Xph-implements</arg>
      <arg>java.io.Serializable</arg>
      <arg>-Xph-equalshashcode</arg>
      <arg>-Xph-tostring</arg>
      <arg>-Xph-list-extension</arg>
      <arg>-Xph-bean-validation11</arg>
      <arg>-Xph-csu</arg>
      <arg>-Xph-cloneable2</arg>
    </args>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>com.helger</groupId>
      <artifactId>ph-jaxb-plugin</artifactId>
      <!-- Use the right version below -->
      <version>5.1.1</version>
    </dependency>
  </dependencies>
</plugin>
```

## Old stuff 

For usage with JAXB 2.2 use this dependency:

```xml
    <dependency>
      <groupId>com.helger</groupId>
      <artifactId>ph-jaxb22-plugin</artifactId> <!-- different!!! -->
      <version>2.3.3.3</version>
    </dependency>
```


# JAXB Plugins

All plugins are activated by passing `-Xph-<name>` as an XJC argument.
Some plugins require additional runtime dependencies in the generated code (noted per plugin below).

## ph-annotate

**XJC argument:** `-Xph-annotate`

Adds JSpecify nullability annotations to generated bean classes and `ObjectFactory` methods.

On bean **getters**:
* `@Nullable` for single-value object fields
* `@NonNull @ReturnsMutableObject` for `List` fields

On bean **setters**:
* `@Nullable` on parameters for non-primitive types

On `ObjectFactory` **create methods**:
* `@NonNull` on the return type
* `@Nullable` on parameters

**Example** - a field `protected String name` will produce:

```java
@Nullable
public String getName() { return name; }

public void setName(@Nullable String value) { this.name = value; }
```

## ph-bean-validation10

**XJC argument:** `-Xph-bean-validation10`

Injects JSR 303 (Bean Validation 1.0) annotations based on XSD constraints.
The generated code requires `jakarta.validation:jakarta.validation-api` at runtime.

**Annotations added based on XSD facets:**

| XSD Constraint | Generated Annotation |
|---|---|
| `minOccurs="1"` / `use="required"` | `@NotNull` |
| `minLength` / `maxLength` | `@Size(min=..., max=...)` |
| `pattern` | `@Pattern(regexp="...")` |
| `minInclusive` / `maxInclusive` | `@DecimalMin("...") / @DecimalMax("...")` |
| `minExclusive` / `maxExclusive` | `@DecimalMin(value="...", inclusive=false)` / `@DecimalMax(value="...", inclusive=false)` |
| `totalDigits` / `fractionDigits` | `@Digits(integer=..., fraction=...)` |
| Complex type reference | `@Valid` (for cascading validation) |

**Example** - for an XSD element:
```xml
<xs:element name="email" type="xs:string" minOccurs="1">
  <xs:simpleType>
    <xs:restriction base="xs:string">
      <xs:maxLength value="255"/>
      <xs:pattern value="[^@]+@[^@]+"/>
    </xs:restriction>
  </xs:simpleType>
</xs:element>
```

The generated field will have:
```java
@NotNull
@Size(max = 255)
@Pattern(regexp = "[^@]+@[^@]+")
protected String email;
```

## ph-bean-validation11

**XJC argument:** `-Xph-bean-validation11`

Same as `ph-bean-validation10` but uses JSR 349 (Bean Validation 1.1) annotations.
The difference is in the package used for the `@DecimalMin`/`@DecimalMax` `inclusive` parameter which was added in Bean Validation 1.1.

## ph-cloneable

**XJC argument:** `-Xph-cloneable`

Implements the `Cloneable` interface on all generated classes and adds deep-clone methods.
The generated code requires [ph-commons](https://github.com/phax/ph-commons) at runtime.

**Methods added:**
* `public void cloneTo(TargetType ret)` - copies all fields from `this` to `ret` with proper deep cloning
* `public Object clone()` - creates a new instance and calls `cloneTo`

The plugin handles deep cloning correctly for:
* Immutable types (primitives, `String`, `BigDecimal`, enums, etc.) - assigned directly
* `XMLGregorianCalendar` and similar Java-cloneable types - cloned via `.clone()`
* `List` fields - deep-cloned element by element via `CloneHelper`
* Nested JAXB types - recursively cloned

**Example** - generated code:

```java
public class AddressType implements Cloneable {
    // ... fields ...

    public void cloneTo(AddressType ret) {
        ret.street = this.street;           // String is immutable
        ret.lines = ((this.lines == null) ? null : new ArrayList<>(this.lines));  // List deep clone
    }

    public Object clone() {
        AddressType ret = new AddressType();
        cloneTo(ret);
        return ret;
    }
}
```

## ph-cloneable2

**XJC argument:** `-Xph-cloneable2`

Same deep-clone functionality as `ph-cloneable`, but implements `com.helger.commons.lang.IExplicitlyCloneable` instead of `java.lang.Cloneable`.
The generated code requires [ph-commons](https://github.com/phax/ph-commons) at runtime.

Use this variant when you want explicit clone support that is visible in the type system via the `IExplicitlyCloneable` marker interface.

## ph-code-quality

**XJC argument:** `-Xph-code-quality`

Fixes code quality issues in the generated code to suppress compiler warnings:
* Makes all `QName` constants in `ObjectFactory` classes `public` (they are package-private by default)
* Adds `final` modifier to parameters of `ObjectFactory` `JAXBElement<...> create...()` methods
* Adds JavaDoc to `ObjectFactory` create methods

No additional runtime dependencies required.

## ph-csu

**XJC argument:** `-Xph-csu`

Adds the `@CodingStyleguideUnaware` annotation to all generated classes and inner classes.
This is useful to exclude generated code from coding style checks.
The generated code requires [ph-commons](https://github.com/phax/ph-commons) at runtime.

**Example:**

```java
@CodingStyleguideUnaware
public class AddressType {
    @CodingStyleguideUnaware
    public static class InnerType {
        // ...
    }
}
```

## ph-default-locale

**XJC argument:** `-Xph-default-locale locale`

Sets the JVM default locale during XJC code generation. This does **not** modify the generated code - it only affects the XJC compilation process itself.

**Example usage:**
```xml
<arg>-Xph-default-locale</arg>
<arg>en_US</arg>
```

## ph-equalshashcode

**XJC argument:** `-Xph-equalshashcode`

Auto-generates `equals()` and `hashCode()` methods using ph-commons helpers.
The generated code requires [ph-commons](https://github.com/phax/ph-commons) at runtime.

The plugin correctly handles:
* Inheritance hierarchies (calls `super.equals()` / includes super hashCode)
* `JAXBElement` fields (compared via `JAXBHelper`)
* `List` fields (compared via `CollectionEqualsHelper`)
* `Object` fields (compared via `EqualsHelper`, which handles DOM nodes correctly)
* Primitive and regular object fields

**Example** - generated code:

```java
@Override
public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !getClass().equals(o.getClass())) return false;
    AddressType rhs = ((AddressType) o);
    if (!EqualsHelper.equals(street, rhs.street)) return false;
    if (!EqualsHelper.equals(city, rhs.city)) return false;
    return true;
}

@Override
public int hashCode() {
    return new HashCodeGenerator(this)
        .append(street)
        .append(city)
        .getHashCode();
}
```

## ph-fields-private

**XJC argument:** `-Xph-fields-private`

Changes the visibility of all generated fields from `protected` (JAXB default) to `private`.
No additional runtime dependencies required.

**Before:**
```java
protected String name;
```

**After:**
```java
private String name;
```

## ph-implements

**XJC argument:** `-Xph-implements fullyQualifiedInterfaceName[,otherInterfaceName]`

Makes all generated classes and enums implement one or more specified interfaces.
Multiple interfaces can be separated by commas or semicolons.
The interface is only added to root classes in an inheritance hierarchy (subclasses inherit it automatically).
For enums, `java.io.Serializable` is automatically skipped (enums are already serializable).

**Example usage:**
```xml
<arg>-Xph-implements</arg>
<arg>java.io.Serializable</arg>
```

Generates:
```java
public class AddressType implements Serializable {
    // ...
}
```

## ph-list-extension

**XJC argument:** `-Xph-list-extension`

Adds convenience methods for all `List`-typed fields. Standard JAXB only generates a single `getXxx()` method that returns the list. This plugin adds methods that make working with lists more ergonomic.

**Methods added for each List field (e.g. a field `items` of type `List<ItemType>`):**

| Method | Description |
|---|---|
| `void setItem(List<ItemType> aList)` | Replace the entire list |
| `boolean hasItemEntries()` | Returns `true` if at least one entry is present |
| `boolean hasNoItemEntries()` | Returns `true` if the list is empty or null |
| `int getItemCount()` | Returns the number of entries (0 if null) |
| `ItemType getItemAtIndex(int index)` | Returns the element at the given index |
| `void addItem(ItemType elem)` | Adds a single entry to the list |

If `getItemCount()` clashes with an existing method, `getItemListCount()` is generated instead.

## ph-namespace-prefix

**XJC argument:** `-Xph-namespace-prefix`

Forces specific namespace prefixes in the generated `@XmlSchema` annotation on `package-info.java`, instead of the auto-generated `ns1`, `ns2`, ... prefixes.
Prefix mappings are defined via customization elements in the XJC bindings file.

No additional runtime dependencies required.

**Bindings file example:**

```xml
<?xml version="1.0"?>
<jxb:bindings version="2.1"
    xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
    xmlns:namespace="http://www.helger.com/namespaces/jaxb/plugin/namespace-prefix">

    <jxb:bindings schemaLocation="myschema.xsd">
        <jxb:schemaBindings>
            <jxb:package name="com.example.schema.v1" />
        </jxb:schemaBindings>
        <jxb:bindings>
            <namespace:prefix name="myns" />
        </jxb:bindings>
    </jxb:bindings>

</jxb:bindings>
```

This generates a `package-info.java` with:
```java
@XmlSchema(xmlns = {
    @XmlNs(prefix = "myns", namespaceURI = "http://example.com/schema/v1")
})
package com.example.schema.v1;
```

## ph-offset-dt-extension

**XJC argument:** `-Xph-offset-dt-extension`

Adds convenience getter and setter methods for `Offset*` date/time types (`OffsetDate`, `OffsetTime`, `OffsetDateTime` and their `XMLOffset*` variants) that accept and return `Local*` counterparts.

**Methods added for each Offset date/time field (e.g. `OffsetDateTime created`):**

| Method | Description |
|---|---|
| `LocalDateTime getCreatedLocal()` | Returns the local part of the offset value (or `null` if not set) |
| `void setCreated(LocalDateTime value)` | Sets the field by converting from local to offset type |

The same pattern applies for `OffsetDate`/`LocalDate` and `OffsetTime`/`LocalTime`.

## ph-package-null-marked

**XJC argument:** `-Xph-package-null-marked`

Adds the `@org.jspecify.annotations.NullMarked` annotation to all generated `package-info.java` files.
This declares that all types in the package use JSpecify null-safety by default.

Since v5.1.1.

## ph-tostring

**XJC argument:** `-Xph-tostring`

Auto-generates `toString()` methods using ph-commons `ToStringGenerator`.
The generated code requires [ph-commons](https://github.com/phax/ph-commons) at runtime.

Handles inheritance hierarchies correctly by using a derived `ToStringGenerator` for subclasses.

**Example** - generated code:

```java
@Override
public String toString() {
    return new ToStringGenerator(this)
        .append("street", street)
        .append("city", city)
        .getToString();
}
```

## ph-value-extender

**XJC argument:** `-Xph-value-extender`

Creates additional constructors and methods for accessing the "value" field that is common in UBL/CII type systems where XSD types wrap a simple value with optional attributes.

**What is added:**
* A default no-arg constructor (if not already present)
* A constructor taking the value as a parameter: `ClassName(valueType)`
* Typed getter/setter for the value, e.g. `getStringValue()` / `setStringValue(String)`
* For boolean values: `isValue()` instead of `getValue()`
* For `Offset*` date/time values: additional `get...ValueLocal()` methods (if `ph-offset-dt-extension` is also active)

**Example** - for a generated class wrapping a `BigDecimal` value:

```java
public class AmountType {
    // Default no-arg constructor
    public AmountType() {}

    // Value constructor
    public AmountType(BigDecimal value) {
        setValue(value);
    }

    // Typed getter
    public BigDecimal getBigDecimalValue() {
        return getValue();
    }

    // Typed setter
    public void setBigDecimalValue(BigDecimal value) {
        setValue(value);
    }
}
```

# Comparison with highsource/jaxb-tools

The other well-known XJC plugin library is [highsource/jaxb-tools](https://github.com/highsource/jaxb-tools) (`org.jvnet.jaxb:jaxb-plugins`).

## Project overview

| | ph-jaxb-plugin | highsource jaxb-tools |
|---|---|---|
| **Type** | XJC plugin library | Maven plugin + XJC plugin library |
| **Coordinates** | `com.helger:ph-jaxb-plugin` | `org.jvnet.jaxb:jaxb-maven-plugin` + `org.jvnet.jaxb:jaxb-plugins` |
| **JAXB version** | JAXB 4.x (Jakarta) | JAXB 4.x (Jakarta) |
| **Java baseline** | Java 17+ | Java 11+ |
| **XJC plugins** | 17 plugins | 25+ plugins |

## Feature comparison

| Feature | ph-jaxb-plugin | jaxb-tools |
|---|---|---|
| **equals/hashCode** | Via ph-commons `EqualsHelper` / `HashCodeGenerator` | Two variants: "simple" (self-contained) or "strategic" (pluggable `EqualsStrategy` / `HashCodeStrategy`) |
| **toString** | Via ph-commons `ToStringGenerator` | "Strategic" (pluggable `ToStringStrategy`) or via Apache Commons Lang 3 |
| **Deep clone/copy** | `clone()` + `cloneTo()` via `Cloneable` or `IExplicitlyCloneable` | `copyTo()` via pluggable `CopyStrategy` |
| **Merge two objects** | -- | `mergeFrom()` via `MergeStrategy` |
| **Bean validation** | JSR 303 + JSR 349 annotations from XSD facets (`@NotNull`, `@Size`, `@Pattern`, `@DecimalMin/Max`, `@Digits`, `@Valid`) | -- |
| **Nullability annotations** | JSpecify `@NonNull` / `@Nullable` on getters, setters, ObjectFactory | -- |
| **Package @NullMarked** | Yes | -- |
| **Fields private** | Yes | -- |
| **Implements interfaces** | Yes (any interface, on classes + enums) | Yes (`-Xinheritance` + `-XautoInheritance`, supports extends too) |
| **List convenience methods** | `set`, `has...Entries`, `hasNo...Entries`, `get...Count`, `get...AtIndex`, `add` | `set` only (`-Xsetters`) |
| **Fluent API** | -- | Yes (chained setters returning `this`) |
| **Namespace prefix** | Yes (`@XmlNs` via bindings) | Yes (similar approach) |
| **Value constructor** | Yes (value field constructor + typed getters/setters, designed for UBL/CII) | Yes (all-args constructor) |
| **Offset date/time helpers** | Yes (Local/Offset conversion) | -- |
| **Default values from XSD** | -- | Yes (`-Xdefault-value`) |
| **Code quality fixes** | Yes (public QName, final params, JavaDoc on ObjectFactory) | -- |
| **@CodingStyleguideUnaware** | Yes | -- |
| **Annotation manipulation** | -- | Yes (add/remove arbitrary annotations via bindings) |
| **Property simplification** | -- | Yes (simplify `aOrBOrC` choice properties) |
| **Enum value interface** | -- | Yes (`EnumValue<T>`) |
| **Parent pointer** | -- | Yes (child to parent navigation) |
| **Property change listeners** | -- | Yes |

## What makes ph-jaxb-plugin stand out

* **Bean Validation from XSD** -- Automatically derives `@NotNull`, `@Size`, `@Pattern`, `@DecimalMin/Max`, `@Digits`, and `@Valid` annotations directly from XSD facets. jaxb-tools has no equivalent.
* **JSpecify null-safety** -- Automatic `@NonNull`/`@Nullable` annotations on getters, setters, and ObjectFactory methods, plus `@NullMarked` on packages. Makes generated code compatible with modern null-safety tooling (NullAway, Error Prone, etc.).
* **Rich List API** -- Six convenience methods per list field vs. just a setter in jaxb-tools. Methods like `hasEntries()`, `getCount()`, `getAtIndex()`, and `add()` significantly reduce boilerplate when working with JAXB lists.
* **UBL/CII-oriented value extender** -- Purpose-built for document type systems (UBL, CII) where types wrap a simple value with attributes. Generates typed value constructors and getters (e.g., `AmountType(BigDecimal)`, `getBigDecimalValue()`).
* **Offset date/time conversion** -- Automatic Local/Offset date-time conversion helpers, useful when working with XML date types that carry timezone offsets but application logic uses local times.
* **Single JAR, opinionated** -- All 17 plugins in one artifact, no module sprawl. jaxb-tools splits functionality across many modules and offers pluggable strategies (useful for customization, but adds complexity). ph-jaxb-plugin uses ph-commons directly, which is simpler if you already depend on ph-commons.

## Trade-offs

| ph-jaxb-plugin | jaxb-tools |
|---|---|
| Simpler setup (one dependency) | More modular (pick only what you need) |
| Tied to ph-commons runtime | Strategic plugins allow custom strategies; simple plugins have zero runtime deps |
| No fluent API, no merge, no property listeners | Broader feature set for general-purpose use |
| Strongest in validation + null-safety | Strongest in flexibility + annotation manipulation |

# News and noteworthy

v5.1.1 - 2025-11-16
* Added new plugin `ph-package-null-marked`
* Heavily improved for JSpecify annotations to avoid setting invalid ones

v5.1.0 - 2025-11-02
* Updated to ph-commons 12.1.0
* Updated to use JSpecify Nullable/NonNull annotations

v5.0.1 - 2025-09-12
* Added handling for type `Serializable` in `clone` plugin

v5.0.0 - 2025-08-24
* Requires Java 17 as the minimum version
* Updated to ph-commons 12.0.0
* The generated code requires ph-commons 12.0.0

v4.0.3 - 2023-09-20
* If the `ph-list-extension` is used and  `get...Count()` is already present, a `get...ListCount()` is created instead

v4.0.2 - 2023-04-20
* Improved debug logging further
* Fixed consistency error in `ph-value-extender` plugin. See [issue #5](https://github.com/phax/ph-jaxb-plugin/issues/5) - thx @hujian19

v4.0.1 - 2023-04-17
* Improved logging, so that the `-debug` switch of XJC is honoured

v4.0.0 - 2022-09-13
* Updated to JAXB 4.0
* Requires at least Java 11
* Changed the artifact ID from `ph-jaxb22-plugin` to `ph-jaxb-plugin`
* Changed all the internal namespaces from `com.helger.jaxb22.plugin` to `com.helger.jaxb.plugin`
* Added new plugin `-Xph-namespace-prefix` to force a certain prefix via `@XmlNs` annotation

v2.3.3.3 - 2021-05-02
* Requires ph-commons 10.1.0
* Plugin `-Xph-offset-dt-extension` now also supports `XMLOffsetDate`
* Added class `PeriodDuration` as being "not clonable"

v2.3.3.2 - 2021-03-21
* Updated to ph-commons 10
* Added new plugin `-Xph-offset-dt-extension`
* Removed plugin `-Xph-tostring-legacy`

v2.3.3.1 - 2020-10-05
* Cloneable plugins now create a "HashMap" instead of a "CommonsHashMap"

v2.3.3.0 - 2020-09-17
* Updated to Jakarta JAXB 2.3.3

v2.3.2.6 - 2020-04-29
* Fixed an error in cloning if an enumeration from an episode was referenced

v2.3.2.5 - 2020-04-24
* Ignoring static fields if the global binding `fixedAttributeAsConstantProperty="true"` is used

v2.3.2.4 - 2019-12-12
* Added class `DataHandler` as being "not clonable"

v2.3.2.3 - 2019-10-04
* Fixed a missing `@Valid` annotation for anonymous nested types ([issue #2](https://github.com/phax/ph-jaxb22-plugin/issues/2)) 

v2.3.2.2 - 2019-05-07
* Using unbounded version instead of limiting to Java 12.x

v2.3.2.1 - 2019-05-06
* Version number reflects latest JAXB version in use
* Updated to ph-commons 9.3.3 with Java 12 support

v2.3.1.5 - 2019-05-05
* Started Java 12 support 

v2.3.1.4 - 2019-01-26
* Fixed JavaDoc error in created code when constructor parameter is a List 

v2.3.1.3 - 2019-01-25
* Integrated `ph-ubl-value` from ph-ubl as as `ph-value-extender` into this project. 

v2.3.1.2 - 2018-12-01
* Added creation of bean validation `@Valid` annotation. Cascading bean validation now works properly.

v2.3.1.1 - 2018-11-22
* Added support for JAXB 2.3.1 when using Java 9 or higher - still works with JAXB 2.2.11 for Java 8
* Updated to ph-commons 9.2.0 
* Created code requires at least ph-commons 9.2.0

v2.2.11.13 - 2018-10-31
* Added `QName` as an immutable type for cloning

v2.2.11.12 - 2018-10-31
* Added option `-Xph-cloneable2` to implement `Cloneable` based on the `com.helger.commons.lang.IExplicitlyCloneable` interface. That means that created code requires at least ph-commons 9.1.8.

v2.2.11.11 - 2018-03-13
* Fixed a problem in `cloneTo` with `null` `List` values 

v2.2.11.10 - 2017-11-05
* Updated to ph-commons 9.0.0

v2.2.11.9 - 2017-02-16
* Added option `-Xph-tostring` requires ph-commons >= 8.6.2 
* Added option `-Xph-tostring-legacy`

v2.2.11.8 - 2016-07-27
* Fixed bug in cloning of abstract class

v2.2.11.7 - 2016-06-10
* JDK8 is required
* Added generic cloning 
* the `ph-csu` settings is now also applied on nested generated classes

v2.2.11.6 - 2015-07-21
* Fixed error in `getXXXCount` method name

v2.2.11.5 - 2015-07-01
* Extended `ph-list-extension` with the `add` method
* Updated to ph-commons 6.0.0

v2.2.11.4 - 2015-03-31
* Disabled the parameter renaming in the PluginCodeQuality so that JavaDocs can be generated with Java 8

v2.2.11.3 - 2015-03-11

v2.2.11.2 - 2015-02-06
* Extended `ph-csu` for all enums as well

v2.2.11.1 - 2015-02-06
* Added new option `ph-csu` to add the CodingStyleguideUnaware annotation to all classes

v2.2.11 - 2014-12-02
* linked against JAXB 2.2.11

v2.2.7 - 2014-08-24
* linked against JAXB 2.2.7

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.