@echo off
set SERVICE_NAME=PrintService
set CURRENT_PATH=%~dp0

REM 停止服务如果已存在
sc stop %SERVICE_NAME%
sc delete %SERVICE_NAME%

REM 注册新服务
%CURRENT_PATH%winsw.exe install

echo 服务安装完成
pause