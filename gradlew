#!/bin/sh
# Gradle wrapper launcher (POSIX). Requires gradle/wrapper/gradle-wrapper.jar.
# If missing, run `gradle wrapper` once or open the project in Android Studio.
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
