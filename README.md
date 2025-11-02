# ph-jaxb-plugin

JAXB 4.0 plugin that adds some commonly needed functionality.

Version 5.x requires Java 17 and builds on ph-commons v12.

Version 4.0.3 was the last version requiring Java 11 and builds on ph-commons v11.

The old version for JAXB 2.2 was called `ph-jaxb22-plugin`

This project is licensed under the Apache 2 license.

# Maven usage

Add something **like** the following to your pom.xml to use this artifact:

```xml
<plugin>
  <groupId>org.jvnet.jaxb</groupId>
  <artifactId>jaxb-maven-plugin</artifactId>
  <version>4.0.9</version>
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
      <version>5.1.0</version>
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

* `ph-annotate` - Create `@javax.annotation.Nonnull`/`@javax.annotation.Nullable` annotations in all bean generated objects as well as in the `ObjectFactory` classes
* `ph-bean-validation10` - inject Bean validation 1.0 annotations (JSR 303)
* `ph-bean-validation11` - inject Bean validation 1.1 annotations (JSR 349)
* `ph-cloneable` (since 2.2.11.7) - implement `clone()` of `Cloneable` interface and `cloneTo(target)`. This requires the created code to depend on [ph-commons](https://github.com/phax/ph-commons).
* `ph-cloneable2` (since 2.2.11.12) - implement `clone()` of `Cloneable` using `com.helger.commons.lang.IExplicitlyCloneable` interface and `cloneTo(target)`. This requires the created code to depend on [ph-commons &ge; 9.1.8](https://github.com/phax/ph-commons).
* `ph-code-quality` - fix some issues that cause warnings in the generated code.
    * All `ObjectFactory` `QName` members are made public.
    * Adding JavaDocs to all `ObjectFactory` `JAXBElement<...> create...` methods
* `ph-csu` - add `@CodingStyleguideUnaware` annotations to all classes. This requires the created code to depend on [ph-commons](https://github.com/phax/ph-commons).
* `ph-default-locale` `locale` - set Java default locale to the specified parameter. Use e.g. `en_US`
* `ph-equalshashcode` - auto implement `equals` and `hashCode` using `com.helger.commons.equals.EqualsHelper` and `com.helger.commons.hashcode.HashCodeGenerator`. This requires the created code to depend on [ph-commons](https://github.com/phax/ph-commons). 
* `ph-fields-private` - mark all fields as private
* `ph-implements` `fullyQualifiedInterfaceName[,otherInterfaceName]` - implement 1-n interfaces in all classes/enums (e.g. `java.io.Serializable`)
* `ph-list-extension` - add additional methods for `List` types:
    * `void set...(List)` - set a new `List`
    * `boolean has...Entries()` - returns `true` if at least one entry is present
    * `boolean hasNo...Entries()` - returns `true` if no entry is present
    * `int get...Count()` (or `get...ListCount`) - returns the number of contained entries
    * `T get...AtIndex(int)` - get the element at the specified index
    * `void add...(T)` - add a new entry to the list
* `ph-offset-dt-extension` (since 2.3.3.2) - add additional methods for Offset* date time types using their `Local` counterparts
* `ph-tostring` - auto implement `toString` using `com.helger.commons.string.ToStringGenerator.getToString()`. This requires the created code to depend on [ph-commons >= 8.6.2](https://github.com/phax/ph-commons). 
* `ph-value-extender` (since 2.3.1.3) - create additional constructors with the 'value' as argument as well as getter and setter for the value

# News and noteworthy

v5.1.0 - 2025-11-02
* Updated to ph-commons 12.1.0
* Updated to use jspecify Nullable/NonNull annotations

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