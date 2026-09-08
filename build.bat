@echo off
set "JAVA_HOME=C:\Users\sddrk\.jdks\jbr-17.0.14"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Java Home is: %JAVA_HOME%
java -version
call gradlew.bat assembleDebug --no-daemon
