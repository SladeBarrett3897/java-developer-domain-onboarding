#!/usr/bin/env sh
set -eu

classes_dir="${TMPDIR:-/tmp}/developer-domain-onboarding-classes"
mkdir -p "$classes_dir"
javac -d "$classes_dir" $(find src/main/java -name '*.java')
java -cp "$classes_dir" dev.learning.domain.DeveloperDomainSetup
