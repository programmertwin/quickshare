@echo off
:: Gradle startup script for Windows

if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
    if exist "%JAVA_EXE%" goto init
    echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
    exit /b 1
) else (
    set JAVA_EXE=java
)

:init
"%JAVA_EXE%" -version >NUL 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found in your PATH or JAVA_HOME.
    exit /b 1
)

set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set DEFAULT_JVM_OPTS=
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
