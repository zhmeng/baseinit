@echo off

rem ##########################################################################
rem # WELCOME TO WIEIXN SERVER
rem ##########################################################################

set p=%~dp0
set p=%p:\=/%
set _SCALA_HOME=%p:\=/%
set PLAY_CLAS=%p%conf;%p%htdocs
set PLAY_OPTS=-Dhttp.port=9100
set JAVA_OPTS=-Dfile.encoding=UTF-8 -Xmx1024M -Xms64M -Xss1M -XX:MaxPermSize=256M -XX:+UseParallelGC %PLAY_OPTS%


:another_param

rem Use "%~1" to handle spaces in paths. See http://ss64.com/nt/syntax-args.html
if "%~1"=="-toolcp" (
    set _LINE_TOOLCP=%~2
	shift
	shift
	goto another_param
)

set _LINE_PARAMS=%1
:param_loop
shift
if [%1]==[] goto param_afterloop
set _LINE_PARAMS=%_LINE_PARAMS% %1
goto param_loop
:param_afterloop
if "%OS%" NEQ "Windows_NT" (
  echo "Warning, your version of Windows is not supported.  Attempting to start scala anyway."
)

@setlocal

rem We use the value of the JAVACMD environment variable if defined
set _JAVACMD=%JAVACMD%

if not defined _JAVACMD (
  if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem We use the value of the JAVA_OPTS environment variable if defined
set _JAVA_OPTS=%JAVA_OPTS%
if not defined _JAVA_OPTS set _JAVA_OPTS=-Xmx256M -Xms32M

set _TOOL_CLASSPATH=
if "%_TOOL_CLASSPATH%"=="" (
  for %%f in ("%_SCALA_HOME%lib\*") do call :add_cpath "%%f"
  for /d %%f in ("%_SCALA_HOME%lib\*") do call :add_cpath "%%f"
)
set _TOOL_CLASSPATH=%PLAY_CLAS%;%_TOOL_CLASSPATH%

if not "%_LINE_TOOLCP%"=="" call :add_cpath "%_LINE_TOOLCP%"

set _PROPS=-Dweixin.home="%_SCALA_HOME%"

rem echo "%_JAVACMD%" %_JAVA_OPTS% %_PROPS% -cp "%_TOOL_CLASSPATH%" scala.tools.nsc.MainGenericRunner  %*
"%_JAVACMD%" %_JAVA_OPTS% %_PROPS% -cp "%_TOOL_CLASSPATH%" com.zwxpay.core.WplatformServer %*
goto end

rem ##########################################################################
rem # subroutines

:add_cpath
  if "%_TOOL_CLASSPATH%"=="" (
    set _TOOL_CLASSPATH=%~1
  ) else (
    set _TOOL_CLASSPATH=%_TOOL_CLASSPATH%;%~1
  )
goto :eof

:end
@endlocal

REM exit code fix, see http://stackoverflow.com/questions/4632891/exiting-batch-with-exit-b-x-where-x-1-acts-as-if-command-completed-successfu
@%COMSPEC% /C exit %errorlevel% >nul
