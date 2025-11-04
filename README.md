# Apache NetBeans installer builds

This repository contains the configuration and build system for the
Friends of Apache NetBeans installers of [Apache NetBeans](https://netbeans.apache.org).

## Build

Links and hashes of Apache NetBeans, the bundled JDK runtimes, and the NBPackage
installer build tool are configured in the `build.properties` file. This file
also contains the version number to write into each installer. Other configuration
for individual packages is included in the `config` files passed in to NBPackage.

Installers are built using the workflow at `.github/workflows/build.yaml`. This
workflow is triggered manually from the `Actions/Build` page using the
`Run workflow` button and selecting the required installers.

The workflow uses the `Exec.java` single source file to download and verify the
required packages, as well as to configure and execute NBPackage.

At the end of the workflow run, each built installer can be found in a zip file
on the workflow output page.

## Current build limitations

### Windows 
The Windows installer is currently signed in the build step with a test certificate.
To test the windows installer, import the test certificate in Windows first.

[Test certificate in PEM format](FoAN-test.pem) [Test certificate in CER format](FoAN-test.cer) 

### MacOS

The macOS packages will be code signed in the build if the relevant secrets and
ID variables are added to the GitHub repository. They must be downloaded,
notarized and stapled before distribution.

### Linux 

The linux installers are usable without issue.

## Legal

These packages are provided without warranty, and under the licenses and terms of
the bundled software. Please make sure you're familiar with all terms before downloading.
Apache NetBeans is provided under the terms of the
[Apache Software License](https://github.com/apache/netbeans/blob/master/LICENSE).
Some of the packages include an [Azul Zulu](https://www.azul.com/downloads/) build of OpenJDK.
Please see terms and licenses linked for the relevant JDK.

Apache, Apache NetBeans and the Apache NetBeans logo are trademarks or registered
trademarks of the Apache Software Foundation. Azul and Azul Zulu are trademarks or
registered trademarks of Azul Systems, Inc. Java and OpenJDK are registered trademarks
of Oracle and/or its affiliates. All other trademarks are the property of their
respective holders and used here only for identification purposes.
