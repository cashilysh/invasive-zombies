@echo off

echo.
echo Starting build for lastest Minecraft version...
call gradlew build
if errorlevel 1 goto error


call gradlew runclient

:error
echo.
echo Build failed! Check the error messages above.
pause