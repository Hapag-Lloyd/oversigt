@echo off

rem Remember current working directory to revert to it after processing
set old_cd=%CD%

rem Change directory to project root
cd /d "%~dp0\.."

rem Update version properties
call mvn org.codehaus.mojo:versions-maven-plugin:update-properties -DgenerateBackupPoms=false

rem Update version tags
call mvn org.codehaus.mojo:versions-maven-plugin:use-latest-releases -DgenerateBackupPoms=false

rem Revert working directory and pause
cd /d "%old_cd%"

rem Keep results visible
pause