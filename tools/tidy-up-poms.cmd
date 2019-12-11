@echo off

rem Remember current working directory to revert to it after processing
set old_cd=%CD%

rem Change directory to project root
cd /d "%~dp0\.."

rem Tidy up POMs
call mvn org.codehaus.mojo:tidy-maven-plugin:pom

rem Revert working directory
cd /d "%old_cd%"

rem Keep results visible
pause