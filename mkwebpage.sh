#!/usr/bin/env bash
# get version from properties file
version=$(cat build.properties | grep netbeans.version | cut -d= -f2)

## web page is a very simple markdown file
cat - <<EOF > nb${version}.md
---
layout: release 
sb: nb${version}
---
EOF

# Collect the yaml files as one and put them in site/_data/nb${version}.yaml
mkdir -p site/_data
cat $(find artifacts -type f -name "*.yaml") > site/_data/nb${version}.yaml 
