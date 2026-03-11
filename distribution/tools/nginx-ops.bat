@echo off
setlocal enabledelayedexpansion

:: ==============================================================================
:: Nginx Operations Tool for Windows
:: ==============================================================================

set "TOOLS_DIR=%~dp0"
set "ROOT_DIR=%TOOLS_DIR%.."
set "NGINX_DIR=%ROOT_DIR%\nginx"

# --- Nginx Detection ---
set "NGINX_EXE="
set "NGINX_HOME="

if exist "%NGINX_DIR%" (
    :: Check for extracted Nginx in embedded directory only
    for /d %%D in ("%NGINX_DIR%\nginx*") do (
        if exist "%%D\nginx.exe" (
            set "NGINX_EXE=%%D\nginx.exe"
            set "NGINX_HOME=%%D"
            goto :FoundNginx
        )
    )
)

:FoundNginx
if not defined NGINX_EXE (
    echo Error: Embedded Nginx not found in %NGINX_DIR%.
    pause
    exit /b 1
)

echo Found Embedded Nginx: %NGINX_EXE%
echo.

if "%1"=="start" goto :start
if "%1"=="stop" goto :stop
if "%1"=="restart" goto :restart
if "%1"=="status" goto :status
if "%1"=="reload" goto :reload
if "%1"=="check" goto :check
if "%1"=="info" goto :info

echo Usage: %0 {start|stop|restart|status|reload|check|info}
goto :eof

:info
    set "CONF_FILE=%NGINX_HOME%\conf\nginx.conf"
    echo [INFO] Configuration File: %CONF_FILE%
    
    tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL
    if "%ERRORLEVEL%"=="0" (
        :: Try to extract ports from config
        set "PORTS="
        for /f "tokens=2" %%i in ('findstr /r "listen.*[0-9]" "%CONF_FILE%"') do (
            set "val=%%i"
            set "val=!val:;=!"
            set "PORTS=!PORTS!!val! "
        )
        echo [INFO] Listening Ports: !PORTS!
    ) else (
        echo [WARN] Nginx is not running. Cannot detect active ports.
    )
    goto :eof

:reload
    echo Checking Nginx config before reload...
    "%NGINX_EXE%" -t -p "%NGINX_HOME%"
    if !errorlevel! neq 0 (
        echo Error: Nginx config invalid. Reload aborted.
        exit /b 1
    )
    echo Reloading Nginx configuration (Zero Downtime)...
    "%NGINX_EXE%" -p "%NGINX_HOME%" -s reload
    if !errorlevel! == 0 (
        echo Nginx reloaded successfully.
    ) else (
        echo Error: Failed to reload Nginx.
    )
    goto :eof

:check
    echo Testing Nginx configuration...
    "%NGINX_EXE%" -t -p "%NGINX_HOME%"
    if !errorlevel! == 0 (
        echo Configuration is valid.
    ) else (
        echo Configuration is invalid.
    )
    goto :eof

:start
    tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL
    if "%ERRORLEVEL%"=="0" (
        echo Nginx is already running.
    ) else (
        echo Checking Nginx config...
        "%NGINX_EXE%" -t -p "%NGINX_HOME%"
        if !errorlevel! neq 0 (
            echo Error: Nginx config invalid.
            exit /b 1
        )
        
        echo Starting Nginx...
        pushd "%NGINX_HOME%"
        :: Start Nginx in background. Using 'start' to avoid blocking.
        start "" "nginx.exe"
        popd
        
        :: Wait a bit and check if it's running
        timeout /t 3 /nobreak >nul
        tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL
        if !errorlevel! == 0 (
            echo Nginx started.
        ) else (
            echo Error: Nginx failed to start. Check logs at %NGINX_HOME%\logs\error.log
            exit /b 1
        )
    )
    goto :eof

:stop
    echo Stopping Nginx...
    :: Use taskkill to kill all nginx processes
    taskkill /F /IM nginx.exe /T >nul 2>&1
    :: Also try the official way if taskkill missed something
    if exist "%NGINX_EXE%" (
        "%NGINX_EXE%" -p "%NGINX_HOME%" -s stop >nul 2>&1
    )
    echo Nginx stopped.
    goto :eof

:restart
    call :stop
    timeout /t 2 /nobreak >nul
    call :start
    goto :eof

:status
    tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL
    if "%ERRORLEVEL%"=="0" (
        echo Nginx is running.
        call :info
    ) else (
        echo Nginx is not running.
    )
    goto :eof
