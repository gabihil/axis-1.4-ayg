# Axis (Apache eXtensible Interaction System)

Welcome to Axis! You'll find documentation in the docs/ directory.

---

## License

Axis is licensed under the Apache License 2.0. See the files called LICENSE and NOTICE for more information.

---

## About This Repository

This repository is a maintained fork of **Apache Axis 1.4**, based on the final SVN-era sources, with additional **security patches and compatibility fixes**.

The fork provides **limited functionality**, focusing on the core modules only:
- client
- server
- wsdl2java

It has been adapted to **compile and run on JDK 17**.

A helper script for `wsdl2java` is included and can be executed from the repository root:

```bash
./bin/wsdl2java <WSDL-URI> [options]
```

---

## Build Instructions

To use `wsdl2java`, the required modules must first be installed into your **local Maven repository (`~/.m2`)**.

Run the following commands **in order**:

```bash
mvn -pl axis-jaxrpc -am -DskipTests install
mvn -pl axis-model  -am -DskipTests install
mvn -pl axis-tools  -am -DskipTests install
```

Although `-am` (also-make) is used, this order avoids dependency resolution issues when working with snapshot artifacts.

---

## Configuration Notes

- Remove any **JWS references** from `service-config.wsdd`
- JWS-based deployment is **not supported** in this fork

---

## Not Included

The following Axis 1.4 components are intentionally excluded:
- JWS-based services
- Axis admin web application
- Legacy samples and optional extras

---

## Security Fixes

This fork tracks security and compatibility updates. 

The following CVEs are addressed through patches or configuration hardening:

### 1.4-ayg-01
- CVE-2007-2353  
- CVE-2012-5784  
- CVE-2014-3596  

### 1.4-ayg-02
- CVE-2018-8032  

### 1.4-ayg-03
- CVE-2019-0227  

### 1.4-ayg-04
- CVE-2023-40743  

---

## Original Repository

Original Apache Axis 1.x sources:  
https://github.com/apache/axis-axis1-java
