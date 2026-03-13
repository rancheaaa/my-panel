@echo off
setlocal enabledelayedexpansion

:: ==============================================================================
:: My-Panel-Admin Start/Stop Script for Windows
:: ==============================================================================

:: --- UI Helpers ---
set "INFO_PREFIX=[INFO]"
set "SUCCESS_PREFIX=[SUCCESS]"
set "WARN_PREFIX=[WARN]"
set "ERROR_PREFIX=[ERROR]"

:: --- Configuration ---
set "APP_NAME=my-panel-admin"
set "JAR_NAME_PATTERN=my-panel-admin*.jar"
set "BIN_DIR=%~dp0"
set "ROOT_DIR=%BIN_DIR%.."
set "NGINX_PORT=8888"
if exist "%ROOT_DIR%\config\application.yml" (
    for /f "tokens=2 delims=: " %%i in ('findstr /r /c:"^[ ]*nginx-port:" "%ROOT_DIR%\config\application.yml"') do (
        set "NGINX_PORT=%%i"
    )
)
set "JDK_DIR=%ROOT_DIR%\jdk"
set "LOG_DIR=%ROOT_DIR%\logs"
set "NGINX_DIR=%ROOT_DIR%\nginx"
set "DATA_DIR=%ROOT_DIR%\data"
set "PID_FILE=%ROOT_DIR%\%APP_NAME%.pid"
:: JDK & Nginx Package Names (for easy modification)
set "JDK_PKG_NAME=jdk-21-windows.zip"
set "NGINX_PKG_NAME=nginx-1.24.0.zip"
set "JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC"
set "WINDOW_TITLE=MyPanelAdminServer"

:: --- Find JAR ---
set "JAR_PATH="
if exist "%ROOT_DIR%\libs\%JAR_NAME_PATTERN%" (
    for %%F in ("%ROOT_DIR%\libs\%JAR_NAME_PATTERN%") do set "JAR_PATH=%%F"
)
if not defined JAR_PATH (
    if exist "%ROOT_DIR%\%JAR_NAME_PATTERN%" (
        for %%F in ("%ROOT_DIR%\%JAR_NAME_PATTERN%") do set "JAR_PATH=%%F"
    )
)

if not defined JAR_PATH (
    echo %ERROR_PREFIX% %JAR_NAME_PATTERN% not found in root or libs directory.
    pause
    exit /b 1
)

:: --- Main ---
set "TARGET=all"
set "ACTION=%~1"

if /i "%ACTION%"=="app" (
    set "TARGET=app"
    set "ACTION=%~2"
) else if /i "%ACTION%"=="nginx" (
    set "TARGET=nginx"
    set "ACTION=%~2"
)

if "%ACTION%"=="" set "ACTION=install"

if /i "%ACTION%"=="install" (
    call :install
    goto :eof
)

:: --- Pre-Check Installation ---
call :check_installed
if %errorlevel% neq 0 (
    if /i "%TARGET%"=="app" (
        echo %ERROR_PREFIX% Java dependency is not installed.
    ) else if /i "%TARGET%"=="nginx" (
        echo %ERROR_PREFIX% Nginx dependency is not installed.
    ) else (
        echo %ERROR_PREFIX% Dependencies are not installed.
    )
    echo %INFO_PREFIX% Please run: %~nx0 install
    exit /b 1
)
:: Ensure Nginx configuration is initialized
if /i "%TARGET%"=="nginx" call :InitNginxConf
if /i "%TARGET%"=="all" call :InitNginxConf

if /i "%ACTION%"=="start" (
    goto :start
)
if /i "%ACTION%"=="stop" (
    call :stop
    goto :eof
)
if /i "%ACTION%"=="restart" (
    goto :restart
)
if /i "%ACTION%"=="status" (
    call :status
    goto :eof
)

echo %ERROR_PREFIX% Unknown action: "%ACTION%"
echo Usage: %~nx0 [app^|nginx] {install^|start^|stop^|restart^|status}
echo.
echo Example:
echo   %~nx0 app start      # Only start the Java application
echo   %~nx0 nginx status   # Only check Nginx status
echo   %~nx0 start          # Start both application and Nginx
echo.
echo Actions:
echo   install  Check/Install dependencies (JDK and Nginx). Default action.
echo   start    Start the targeted service(s)
echo   stop     Stop the targeted service(s)
echo   restart  Restart the targeted service(s)
echo   status   Check the status of the targeted service(s)
exit /b 1

:check_installed
    set "INSTALLED_JAVA="
    set "INSTALLED_NGINX="
    
    :: Check Java if needed
    if /i "%TARGET%"=="app" set "NEED_JAVA=1"
    if /i "%TARGET%"=="all" set "NEED_JAVA=1"
    
    if defined NEED_JAVA (
        :: Check System Java
        java -version 2>&1 | findstr "21" >nul
        if %errorlevel% == 0 set "INSTALLED_JAVA=java"
        
        :: Check Bundled JDK
        if not defined INSTALLED_JAVA (
            if exist "%JDK_DIR%" (
                for /d %%D in ("%JDK_DIR%\jdk-21*") do (
                    if exist "%%D\bin\java.exe" set "INSTALLED_JAVA=%%D\bin\java.exe"
                )
            )
        )
        if not defined INSTALLED_JAVA exit /b 1
        set "JAVA_CMD=%INSTALLED_JAVA%"
    )

    :: Check Nginx if needed
    if /i "%TARGET%"=="nginx" set "NEED_NGINX=1"
    if /i "%TARGET%"=="all" set "NEED_NGINX=1"
    
    if defined NEED_NGINX (
        if exist "%NGINX_DIR%" (
            for /d %%D in ("%NGINX_DIR%\nginx*") do (
                if exist "%%D\nginx.exe" set "INSTALLED_NGINX=%%D\nginx.exe"
            )
        )
        if not defined INSTALLED_NGINX exit /b 1
        set "NGINX_EXE=%INSTALLED_NGINX%"
        for /f "delims=" %%i in ("%INSTALLED_NGINX%") do set "NGINX_HOME=%%~dpi"
        if "!NGINX_HOME:~-1!"=="\" set "NGINX_HOME=!NGINX_HOME:~0,-1!"
    )

    exit /b 0

:install
    echo %INFO_PREFIX% Checking dependencies for %TARGET%...
    
    :: 1. Java Detection/Installation
    if /i "%TARGET%"=="app" set "DO_JAVA=1"
    if /i "%TARGET%"=="all" set "DO_JAVA=1"
    
    if defined DO_JAVA (
        set "JAVA_CMD="
        set "JAVA_SOURCE="
        :: Check System Java
        java -version 2>&1 | findstr "21" >nul
        if %errorlevel% == 0 (
            set "JAVA_CMD=java"
            set "JAVA_SOURCE=System Installed Java"
        )
        :: Check Bundled JDK
        if not defined JAVA_CMD (
            if exist "%JDK_DIR%" (
                for /d %%D in ("%JDK_DIR%\jdk-21*") do (
                    if exist "%%D\bin\java.exe" (
                        set "JAVA_CMD=%%D\bin\java.exe"
                        set "JAVA_HOME=%%D"
                        set "JAVA_SOURCE=Bundled JDK (Internal)"
                    )
                )
                if not defined JAVA_CMD (
                    if exist "%JDK_DIR%\%JDK_PKG_NAME%" (
                        echo %INFO_PREFIX% Extracting JDK...
                        powershell -Command "Expand-Archive -Path '%JDK_DIR%\%JDK_PKG_NAME%' -DestinationPath '%JDK_DIR%' -Force"
                        for /d %%D in ("%JDK_DIR%\jdk-21*") do (
                            if exist "%%D\bin\java.exe" (
                                set "JAVA_CMD=%%D\bin\java.exe"
                                set "JAVA_HOME=%%D"
                                set "JAVA_SOURCE=Bundled JDK (Internal)"
                            )
                        )
                    )
                )
            )
        )
        if not defined JAVA_CMD (
            echo %ERROR_PREFIX% JDK 21 not found.
            pause
            exit /b 1
        )
        echo %INFO_PREFIX% Java Source:  %JAVA_SOURCE%
        echo %INFO_PREFIX% Java Path:    %JAVA_CMD%
    )

    :: 2. Nginx Detection/Installation
    if /i "%TARGET%"=="nginx" set "DO_NGINX=1"
    if /i "%TARGET%"=="all" set "DO_NGINX=1"
    
    if defined DO_NGINX (
        set "NGINX_EXE="
        set "NGINX_HOME="
        if exist "%NGINX_DIR%" (
            for /d %%D in ("%NGINX_DIR%\nginx*") do (
                if exist "%%D\nginx.exe" (
                    set "NGINX_EXE=%%D\nginx.exe"
                    set "NGINX_HOME=%%D"
                )
            )
            if not defined NGINX_EXE (
                set "NGINX_ZIP="
                if exist "%NGINX_DIR%\%NGINX_PKG_NAME%" set "NGINX_ZIP=%NGINX_DIR%\%NGINX_PKG_NAME%"
                if not defined NGINX_ZIP (
                    for %%Z in ("%NGINX_DIR%\nginx-*.zip") do set "NGINX_ZIP=%%Z"
                )
                if defined NGINX_ZIP (
                    echo %INFO_PREFIX% Extracting Nginx...
                    powershell -Command "Expand-Archive -Path '%NGINX_ZIP%' -DestinationPath '%NGINX_DIR%' -Force"
                    for /d %%D in ("%NGINX_DIR%\nginx*") do (
                        if exist "%%D\nginx.exe" (
                            set "NGINX_EXE=%%D\nginx.exe"
                            set "NGINX_HOME=%%D"
                        )
                    )
                )
            )
        )
        if defined NGINX_EXE (
            echo %INFO_PREFIX% Initializing Nginx configuration...
            call :InitNginxConf
            echo %INFO_PREFIX% Nginx Path:   %NGINX_EXE%
        ) else (
            echo %WARN_PREFIX% Embedded Nginx not found.
        )
    )

    if /i "%TARGET%"=="app" echo %SUCCESS_PREFIX% Found JAR: %JAR_PATH%
    if /i "%TARGET%"=="all" echo %SUCCESS_PREFIX% Found JAR: %JAR_PATH%
    echo.
    echo %SUCCESS_PREFIX% Installation/Check complete for %TARGET%.
    goto :eof

:restart
    echo Restarting %TARGET%...
    call :stop
    timeout /t 2 /nobreak >nul
    goto :start

:start
    echo ^>^>^> Starting Services (%TARGET%)...
    
    if /i "%TARGET%"=="nginx" goto :start_nginx_section
    
    :: Start App Section
    echo %INFO_PREFIX% Starting %APP_NAME%...
    if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
    if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
    
    :: Check if App already running
    set "IS_RUNNING=0"
    if exist "%JAVA_HOME%\bin\jps.exe" (
        "%JAVA_HOME%\bin\jps" -l | findstr "my-panel-admin" >nul
        if !errorlevel! == 0 set "IS_RUNNING=1"
    )
    if "!IS_RUNNING!"=="0" (
        if exist "%PID_FILE%" del /f /q "%PID_FILE%"
        wmic process where "Name='java.exe' and CommandLine like '%%my-panel-admin%%'" get ProcessId /value 2>nul | findstr "ProcessId" >nul
        if !errorlevel! == 0 set "IS_RUNNING=1"
    )
    
    if "!IS_RUNNING!"=="1" (
        echo %WARN_PREFIX% %APP_NAME% is already running.
        goto :start_nginx_check
    )

    :: Start App
    pushd "%ROOT_DIR%"
    start "%WINDOW_TITLE%" /b "%JAVA_CMD%" %JAVA_OPTS% -jar "%JAR_PATH%"
    popd

    :: Get PID and write to file
    timeout /t 2 /nobreak >nul
    for /f "tokens=2 delims==" %%i in ('wmic process where "Name='java.exe' and CommandLine like '%%my-panel-admin%%'" get ProcessId /value 2^>nul ^| findstr ProcessId') do (
        echo %%i > "%PID_FILE%"
    )
    
    echo %INFO_PREFIX% Waiting for %APP_NAME% to start...
    set "MAX_WAIT=90"
    set "COUNT=0"
    set "SUCCESS=0"

:check_loop
    set "LISTENING=0"
    set "CURRENT_PID="
    if exist "%JAVA_HOME%\bin\jps.exe" (
        for /f "tokens=1" %%i in ('""%JAVA_HOME%\bin\jps" -l | findstr "my-panel-admin""') do set "CURRENT_PID=%%i"
    )
    if not defined CURRENT_PID (
        for /f "tokens=2 delims==" %%i in ('wmic process where "Name='java.exe' and CommandLine like '%%my-panel-admin%%'" get ProcessId /value 2^>nul ^| findstr ProcessId') do set "CURRENT_PID=%%i"
    )

    if defined CURRENT_PID (
        netstat -ano | findstr "LISTENING" | findstr " !CURRENT_PID!$" >nul
        if !errorlevel! == 0 set "LISTENING=1"
    ) else (
        echo.
        echo %ERROR_PREFIX% %APP_NAME% failed to start.
        goto :start_nginx_check
    )

    if "!LISTENING!"=="1" (
        set "SUCCESS=1"
        goto :check_done
    )

    set /a COUNT+=1
    if %COUNT% geq %MAX_WAIT% goto :check_done
    <nul set /p=.
    timeout /t 1 /nobreak >nul
    goto :check_loop

:check_done
    echo.
    if "%SUCCESS%"=="1" (
        echo %SUCCESS_PREFIX% %APP_NAME% started successfully.
    ) else (
        echo %ERROR_PREFIX% Timeout: %APP_NAME% failed to start within %MAX_WAIT%s.
    )

:start_nginx_check
    if /i "%TARGET%"=="app" goto :start_complete
    
:start_nginx_section
    if exist "%ROOT_DIR%\tools\nginx-ops.bat" (
        call "%ROOT_DIR%\tools\nginx-ops.bat" start
    )

:start_complete
    echo ^>^>^> Start Complete for %TARGET%.
    goto :eof

:stop
    echo ^<^<^< Stopping Services (%TARGET%)...
    
    if /i "%TARGET%"=="nginx" goto :stop_nginx_section
    
    :: Stop App Section
    echo %INFO_PREFIX% Stopping %APP_NAME%...
    set "STOP_SUCCESS=0"
    
    set "FOUND_PIDS="
    for /f "tokens=2 delims==" %%P in ('wmic process where "Name='java.exe' and CommandLine like '%%my-panel-admin%%'" get ProcessId /value 2^>nul ^| findstr ProcessId') do (
        if not "%%P"=="" set "FOUND_PIDS=!FOUND_PIDS! %%P"
    )
    if exist "%JAVA_HOME%\bin\jps.exe" (
        for /f "tokens=1" %%i in ('""%JAVA_HOME%\bin\jps" -l | findstr "my-panel-admin""') do (
            set "FOUND_PIDS=!FOUND_PIDS! %%i"
        )
    )

    if "!FOUND_PIDS!"=="" (
        echo %APP_NAME% is not running.
        goto :stop_nginx_check
    )

    for %%P in (!FOUND_PIDS!) do (
        set "TARGET_PID=%%P"
        echo Found PID: !TARGET_PID!
        taskkill /PID !TARGET_PID! >nul 2>&1
        set "RETRY_COUNT=0"
        :stop_loop_inner
        tasklist /FI "PID eq !TARGET_PID!" 2>nul | findstr "!TARGET_PID!" >nul
        if !errorlevel! neq 0 (
            set "STOP_SUCCESS=1"
        ) else (
            set /a RETRY_COUNT+=1
            if !RETRY_COUNT! lss 5 (
                <nul set /p=.
                timeout /t 1 /nobreak >nul
                goto :stop_loop_inner
            )
            echo.
            echo Force killing PID !TARGET_PID!...
            taskkill /F /T /PID !TARGET_PID! >nul 2>&1
            timeout /t 1 /nobreak >nul
            tasklist /FI "PID eq !TARGET_PID!" 2>nul | findstr "!TARGET_PID!" >nul
            if !errorlevel! neq 0 (
                set "STOP_SUCCESS=1"
            ) else (
                echo %ERROR_PREFIX% Failed to kill PID !TARGET_PID!.
            )
        )
    )
    if "%STOP_SUCCESS%"=="1" (
        echo %SUCCESS_PREFIX% %APP_NAME% stopped successfully.
        if exist "%PID_FILE%" del /f /q "%PID_FILE%"
    )

:stop_nginx_check
    if /i "%TARGET%"=="app" goto :stop_complete

:stop_nginx_section
    if exist "%ROOT_DIR%\tools\nginx-ops.bat" (
        call "%ROOT_DIR%\tools\nginx-ops.bat" stop
    )

:stop_complete
    echo ^<^<^< Stop Complete for %TARGET%.
    goto :eof

:status
    echo === Service Status (%TARGET%) ===
    
    if /i "%TARGET%"=="nginx" goto :status_nginx_section
    
    :: Status App Section
    set "IS_RUNNING=0"
    if exist "%JAVA_HOME%\bin\jps.exe" (
        "%JAVA_HOME%\bin\jps" -l | findstr "my-panel-admin" >nul
        if !errorlevel! == 0 set "IS_RUNNING=1"
    )
    if "!IS_RUNNING!"=="0" (
        wmic process where "Name='java.exe' and CommandLine like '%%my-panel-admin%%'" get ProcessId /value 2>nul | findstr "ProcessId" >nul
        if !errorlevel! == 0 set "IS_RUNNING=1"
    )
    if "!IS_RUNNING!"=="1" (
        echo %APP_NAME% is RUNNING.
    ) else (
        echo %APP_NAME% is STOPPED.
    )

:status_nginx_check
    if /i "%TARGET%"=="app" goto :status_complete

:status_nginx_section
    if exist "%ROOT_DIR%\tools\nginx-ops.bat" (
        call "%ROOT_DIR%\tools\nginx-ops.bat" status
    )

:status_complete
    echo ======================
    goto :eof

:InitNginxConf
    if exist "%NGINX_HOME%\conf\nginx.conf" (
        findstr /c:"# MY-PANEL-ADMIN-CONFIG" "%NGINX_HOME%\conf\nginx.conf" >nul
        if !errorlevel! equ 0 (
            echo %INFO_PREFIX% Nginx configuration already exists and is initialized. Skipping.
            goto :eof
        ) else (
            echo %WARN_PREFIX% Found existing Nginx config, but it is not initialized for My-Panel-Admin. Overwriting...
            move "%NGINX_HOME%\conf\nginx.conf" "%NGINX_HOME%\conf\nginx.conf.bak" >nul
        )
    )

    echo Initializing Nginx (Port: %NGINX_PORT%)...
    
    :: Ensure logs directory exists
    if not exist "%NGINX_HOME%\logs" mkdir "%NGINX_HOME%\logs"

    :: Get backend app port from application.yml
    set "APP_PORT=8080"
    if exist "%ROOT_DIR%\config\application.yml" (
        for /f "tokens=2 delims=: " %%i in ('findstr /r /c:"^[ ]*port:" "%ROOT_DIR%\config\application.yml"') do (
            set "APP_PORT=%%i"
        )
    )
    echo Backend app port: !APP_PORT!
    
    :: Create new config
    (
        echo # MY-PANEL-ADMIN-CONFIG
        echo pid %ROOT_DIR:\=/%/nginx.pid;
        echo worker_processes  1;
        echo events {
        echo     worker_connections  1024;
        echo }
        echo http {
        echo     include       mime.types;
        echo     default_type  application/octet-stream;
        echo     sendfile        on;
        echo     keepalive_timeout  65;
        echo     server {
        echo         listen       %NGINX_PORT%;
        echo         server_name  localhost;
        echo         location / {
        echo             root   "%ROOT_DIR:\=/%/pages";
        echo             index  index.html index.htm;
        echo             try_files $uri $uri/ /index.html;
        echo         }
        echo         location /api/ {
        echo             proxy_pass http://localhost:!APP_PORT!/;
        echo             proxy_set_header Host $http_host;
        echo             proxy_set_header X-Real-IP $remote_addr;
        echo             proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        echo             proxy_set_header X-Forwarded-Proto $scheme;
        echo         }
        echo         location /admin/ {
        echo             proxy_pass http://localhost:!APP_PORT!;
        echo             proxy_set_header Host $http_host;
        echo             proxy_set_header X-Real-IP $remote_addr;
        echo             proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        echo             proxy_set_header X-Forwarded-Proto $scheme;
        echo         }
        echo         location /swagger-ui/ {
        echo             proxy_pass http://localhost:!APP_PORT!;
        echo             proxy_set_header Host $http_host;
        echo             proxy_set_header X-Real-IP $remote_addr;
        echo             proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        echo             proxy_set_header X-Forwarded-Proto $scheme;
        echo         }
        echo         location /v3/ {
        echo             proxy_pass http://localhost:!APP_PORT!;
        echo             proxy_set_header Host $http_host;
        echo             proxy_set_header X-Real-IP $remote_addr;
        echo             proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        echo             proxy_set_header X-Forwarded-Proto $scheme;
        echo         }
        echo         location /druid/ {
        echo             proxy_pass http://localhost:!APP_PORT!;
        echo             proxy_set_header Host $http_host;
        echo             proxy_set_header X-Real-IP $remote_addr;
        echo             proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        echo             proxy_set_header X-Forwarded-Proto $scheme;
        echo         }
        echo         location /actuator/ {
        echo             proxy_pass http://localhost:!APP_PORT!;
        echo             proxy_set_header Host $http_host;
        echo             proxy_set_header X-Real-IP $remote_addr;
        echo             proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        echo             proxy_set_header X-Forwarded-Proto $scheme;
        echo         }
        echo         error_page   500 502 503 504  /50x.html;
        echo         location = /50x.html {
        echo             root   html;
        echo         }
        echo     }
        echo }
    ) > "%NGINX_HOME%\conf\nginx.conf"
    
    echo Nginx initialized.
    goto :eof
