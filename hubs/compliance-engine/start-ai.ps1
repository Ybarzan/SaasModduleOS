Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Lancement de l'IA IncoCalc" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Vérification de l'environnement..." -ForegroundColor Yellow

# Vérifications
try { java -version 2>&1 | Select-Object -First 1 } catch { Write-Host "❌ Java non trouvé" -ForegroundColor Red }
try { mvn -version 2>&1 | Select-Object -First 1 } catch { Write-Host "❌ Maven non trouvé" -ForegroundColor Red }
try { node -version 2>&1 } catch { Write-Host "❌ Node.js non trouvé" -ForegroundColor Red }

Write-Host ""
Write-Host "✅ Environnement prêt !" -ForegroundColor Green
