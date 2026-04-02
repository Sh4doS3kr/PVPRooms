@echo off
title PvPRoomsPro - Build and Push

echo ---------------------------------------------------
echo   PvPRoomsPro - Compilar y Subir a GitHub
echo ---------------------------------------------------
echo.

set "MVN=C:\Users\alex\.m2\wrapper\dists\apache-maven-3.8.6-bin\1ks0nkde5v1pk9vtc31i9d0lcd\apache-maven-3.8.6\bin\mvn.cmd"

if not exist "%MVN%" (
    echo [ERROR] No se encontro Maven en: %MVN%
    pause
    exit /b 1
)

echo --- COMPILANDO ---
echo.
call "%MVN%" clean package -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] La compilacion fallo.
    pause
    exit /b 1
)

echo.
echo [OK] Compilacion exitosa!
echo.

:: ── 5. Git push ─────────────────────────────────────
echo --- SUBIENDO A GITHUB ---
echo.

where git >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Git no encontrado.
    pause
    exit /b 1
)

:: Asegurarse de estar en rama main (no detached HEAD)
git checkout main 2>nul
if %errorlevel% neq 0 (
    git checkout -b main 2>nul
)

git add -A

set "TIMESTAMP=%date:~6,4%-%date:~3,2%-%date:~0,2%"
git commit -m "Update %TIMESTAMP%"
if %errorlevel% neq 0 (
    echo [WARNING] Sin cambios para commit.
)

git push origin main
if %errorlevel% neq 0 (
    echo [ERROR] Fallo el push. Revisa tu conexion o credenciales.
    pause
    exit /b 1
)

echo.
echo ---------------------------------------------------
echo [OK] JAR listo: %OUTPUT_JAR%
echo [OK] Cambios subidos a GitHub
echo ---------------------------------------------------
echo.
pause
endlocal
