#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

LIBDIR="$DIR/../lib"

if [ -d "$LIBDIR" ]; then
   ARTIFACT=`ls -1 $DIR/../lib/*.jar`
else
#development startup
   ARTIFACT=`ls -1 $DIR/../../../target/ratis-examples-*.jar | grep -v test | grep -v javadoc | grep -v sources`
   echo $ARTIFACT
   if [ ! -f "$ARTIFACT" ]; then
      echo "Jar file is missing. Please do a full build (mvn clean package) first."
      exit -1
   fi
fi