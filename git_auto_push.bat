@echo off
chcp 65001 >nul

echo Starting Git operations...

REM 更可靠地获取当前日期（YYYY-MM-DD格式）
for /f "tokens=1-3 delims=/" %%a in ('date /t') do (
    set current_date=%%c-%%a-%%b
)

REM 如果上面的方法不工作，尝试其他方法
if "%current_date%"=="" (
    for /f "tokens=1-3 delims=/- " %%a in ('echo %date%') do (
        set current_date=%%c-%%a-%%b
    )
)

REM 最终备用方法：使用WMIC获取标准格式日期
if "%current_date%"=="" (
    for /f "skip=1 tokens=1-3 delims=/" %%a in ('wmic path win32_localtime get day^,month^,year /format:table') do (
        if not "%%c"=="" (
            set current_date=%%c-%%a-%%b
            goto :date_set
        )
    )
)
:date_set

echo Current date: %current_date%

REM Add all changes
echo Adding all changes...
git add .

REM Commit changes with date message
echo Committing changes...
git commit -m "Update %current_date%"

REM Execute git push
echo Executing git push...
git push
if %errorlevel% neq 0 (
    echo git push failed!
    pause
    exit /b 1
)

echo.
echo All operations completed successfully!
echo Commit message: Update %current_date%
pause