#!/bin/bash

version=0.1.4

mvn clean install || exit 1
for module in runtime processor mocks ; do
  mvn install:install-file -f $module/pom.xml \
      -Dfile=target/ctdynamo-$module-$version.jar \
      -DgroupId=ai.phast \
      -Dversion=$version \
      -DartifactId=ctdynamo-$module \
      -Dpackaging=jar || exit 1
done
