#!/bin/sh
set -eu

rm -rf target/classes target/test-classes
mkdir -p target/classes target/test-classes
find src/main/java -name '*.java' -print > target/main-sources.txt
javac --release 17 -d target/classes @target/main-sources.txt
find src/test/java -name '*.java' -print > target/test-sources.txt
javac --release 17 -cp target/classes -d target/test-classes @target/test-sources.txt
java -cp target/classes:target/test-classes dev.streamledger.delivery.CreatorDeliveryServiceTest
