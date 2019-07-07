@echo off

rem Remember current working directory to revert to it after processing
set old_cd=%CD%

rem Change directory to project root
cd /d "%~dp0\.."

rem Update parent version of child modules
call mvn org.codehaus.mojo:versions-maven-plugin:update-child-modules -DgenerateBackupPoms=false -DallowSnapshots=true

rem Update version properties
call mvn org.codehaus.mojo:versions-maven-plugin:update-properties -DgenerateBackupPoms=false -Dmaven.version.rules="file:///%old_cd%/rules.xml"

rem Update version tags
call mvn org.codehaus.mojo:versions-maven-plugin:use-latest-releases -DgenerateBackupPoms=false -Dmaven.version.rules="file:///%old_cd%/rules.xml"

rem Revert working directory and pause
cd /d "%old_cd%"

rem Keep results visible
pause