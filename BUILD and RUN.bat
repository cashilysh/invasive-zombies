@echo off

echo.
echo Starting build for Minecraft 1.21.6...
call gradlew build -PtargetVersion=1.21.6
if errorlevel 1 goto error


call gradlew runclient

:error
echo.
echo Build failed! Check the error messages above.
pause