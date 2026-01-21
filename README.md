# Axis (Apache eXtensible Interaction System)

Welcome to Axis!  You'll find documentation in the docs/ directory.

# License

Axis is licensed under the Apache License 2.0. See the files called LICENSE and NOTICE for more information.

# About this repo

This repository is a fork of Axis 1.4 with the latest svn changes and security fixes.

It works with JDK17 and includes a wsdl2java helper script you can run from the repo root:

```
./bin/wsdl2java <WSDL-URI> [options]
```

Notes: you must remove jws references from service-config.wsdd . 


# Local development (install to ~/.m2)

To build and install all modules into your local Maven repository:

```
mvn install
```

To build a specific module and its dependencies:

```
mvn -pl :axis-codegen -am install
```



# Original Repository

[Link to the original apache sources at github](https://github.com/apache/axis-axis1-java)

