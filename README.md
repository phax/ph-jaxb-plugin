#ph-jaxb22-plugin

JAXB 2.2 plugin that adds some commonly needed functionality.
The version 2.2.7 is linked against JAXB 2.2.7.
The current version 2.2.11.6 is linked against JAXB 2.2.11.

Versions <= 2.2.11.4 are compatible with ph-commons < 6.0.
Versions >= 2.2.11.5 are compatible with ph-commons >= 6.0.
Versions >= 2.2.11.7 required JDK 8.

#Maven usage
Add the following to your pom.xml to use this artifact:
```
<dependency>
  <groupId>com.helger</groupId>
  <artifactId>ph-jaxb22-plugin</artifactId>
  <version>2.2.11.6</version>
</dependency>
```

#JAXB Plugins

  * `ph-annotate` - Create `@Nonnull`/`@Nullable` annotations in all bean generated objects as well as in the `ObjectFactory` classes
  * `ph-bean-validation10` - inject Bean validation 1.0 annotations (JSR 303)
  * `ph-bean-validation11` - inject Bean validation 1.1 annotations (JSR 349)
  * `ph-cloneable` (since 2.2.11.7) - implement clone() of Cloneable interface and cloneTo(target). This requires the created code to depend on [ph-commons](https://github.com/phax/ph-commons).
  * `ph-code-quality` - fix some issues that cause warnings in the generated code
  * `ph-csu` - add `@CodingStyleguideUnaware` annotations to all classes
  * `ph-default-locale` `locale` - set Java default locale to the specified parameter. Use e.g. `en_US`
  * `ph-equalshashcode` - auto implement equals and hashCode using `com.helger.commons.equals.EqualsHelper` and `com.helger.commons.hashcode.HashCodeGenerator`. This requires the created code to depend on [ph-commons](https://github.com/phax/ph-commons). 
  * `ph-fields-private` - mark all fields as private
  * `ph-implements` `fullyQualifiedInterfaceName[,otherInterfaceName]` - implement 1-n interfaces in all classes/enums (e.g. `java.io.Serializable`)
  * `ph-list-extension` - add additional methods for `List` types:
    * `void set...(List)` - set a new `List`
    * `boolean has...Entries()` - returns `true` if at least one entry is present
    * `boolean hasNo...Entries()` - returns `true` if no entry is present
    * `int get...Count()` - returns the number of contained entries
    * `T get...AtIndex(int)` - get the element at the specified index
    * `void add...(T)` - add a new entry to the list
  * `ph-tostring` - auto implement `toString` using `com.helger.commons.string.ToStringGenerator`. This requires the created code to depend on [ph-commons](https://github.com/phax/ph-commons). 

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodeingStyleguide.md) |
On Twitter: <a href="https://twitter.com/philiphelger">@philiphelger</a>
