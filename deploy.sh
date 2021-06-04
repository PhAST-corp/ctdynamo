#!/bin/bash

MODULES=". processor runtime mocks"

for module in $MODULES ; do
  rm -rf $module/target/mvn-repo
  mkdir -p $module/target/mvn-repo
  cp -r ~/src/mvn/* $module/target/mvn-repo || exit 1
  mvn deploy || exit 1
  cp -r $module/target/mvn-repo/* ~/src/mvn || exit 1
done
