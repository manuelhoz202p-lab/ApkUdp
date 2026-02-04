#!/bin/sh
# Gradle Wrapper script for Unix
# ----------------------------------------------------------------------------
APP_NAME="Gradle"
APP_BASE_NAME=\$(basename "\$0")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
warn () { echo "\$*" ; }
die () { echo "\$*" ; exit 1 ; }
cygwin=false; msys=false; os400=false; hpux=false
case "\$(uname)" in
  CYGWIN* ) cygwin=true ;;
  Darwin* ) ;;
  MSYS* | MINGW* ) msys=true ;;
  NonStop* ) ;;
  OS400* ) os400=true ;;
  HP-UX* ) hpux=true ;;
esac
if [ -n "\$JAVA_HOME" ] ; then
    if [ -x "\$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="\$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="\$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi
exec "\$JAVACMD" "\$@" -classpath "\$CLASSPATH" org.gradle.wrapper.GradleWrapperMain
