#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to locate java binary
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
fi

# Verify that Java exists
if [ ! -x "$JAVACMD" ] ; then
    echo "ERROR: Java not found in your PATH or JAVA_HOME" >&2
    exit 1
fi

# Determine the location of this script
APP_HOME=$(cd "$(dirname "$0")"; pwd)

# Locate gradle-wrapper.jar
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Set JVM options
DEFAULT_JVM_OPTS=""

# Execute Gradle Wrapper
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
