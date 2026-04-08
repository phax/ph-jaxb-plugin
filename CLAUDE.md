# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`ph-jaxb-plugin` is a JAXB 4.x XJC plugin library that enhances JAXB-generated Java classes with additional functionality (equals/hashCode, toString, cloneable, bean validation annotations, etc.). It is a single-module Maven project targeting Java 17+.

## Build Commands

```bash
mvn clean install              # Full build with tests
mvn test                       # Run tests only
mvn test -Dtest=XJCLoaderFuncTest  # Run a single test class
```

The parent POM (`com.helger:parent-pom:3.0.3`) provides all plugin and profile configuration.

## Architecture

### Plugin System

All plugins extend `AbstractPlugin` (which extends `com.sun.tools.xjc.Plugin`) and are registered via SPI in `src/main/resources/META-INF/services/com.sun.tools.xjc.Plugin`. Each plugin manipulates the JAXB code model (`com.sun.codemodel`) during XJC code generation.

Key base classes:
- `AbstractPlugin` - common field iteration, logging, and option name handling
- `AbstractPluginBeanValidation` - shared logic for bean validation 1.0/1.1 plugins
- `AbstractPluginCloneable` - shared logic for cloneable/explicitly-cloneable plugins
- `CJAXB` - constants and utility methods used across plugins

### Plugin Lifecycle

Plugins hook into XJC's code generation pipeline. They receive `ClassOutline` objects representing generated classes and modify them by adding methods, annotations, or interfaces via the `JDefinedClass` code model API.

### Tests

Functional tests (suffixed `FuncTest`) invoke the XJC compiler programmatically with test XSD schemas from `src/test/resources/external/xsd/` and verify the generated code compiles and behaves correctly. `SPITest` validates that all plugins are properly registered in the SPI services file.

## Key Dependencies

- `com.helger.commons` family (`ph-graph`, `ph-jaxb`, `ph-jaxb-adapter`) - core utilities, collection types, annotations
- `com.sun.xml.bind:jaxb-xjc` (provided scope) - XJC compiler API that plugins extend
- `jakarta.validation:jakarta.validation-api` - bean validation annotations
- JUnit 4 for tests
