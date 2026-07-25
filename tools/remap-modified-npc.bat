@echo off
REM 把构建产物的官方 CustomNPCs 包名重写为魔改版包名。
REM 默认只处理 -srg 产物（可直接装进游戏的那个）。
REM 直接双击运行，或带参数调用：remap-modified-npc.bat --force -v
setlocal
python "%~dp0remap_noppes.py" %*
set EXITCODE=%ERRORLEVEL%
if not "%1"=="" goto :end
echo.
pause
:end
exit /b %EXITCODE%
