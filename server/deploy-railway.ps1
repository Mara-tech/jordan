# Deploy Jordan Server to Railway
# Usage: .\deploy-railway.ps1

Write-Host "🚀 Jordan Server - Railway Deployment" -ForegroundColor Cyan

# Check if railway is installed
if (-not (Get-Command railway -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Railway CLI not found. Install with: npm install -g @railway/cli" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Railway CLI found`n" -ForegroundColor Green

# Check if in server directory
if (-not (Test-Path "Dockerfile")) {
    Write-Host "❌ Dockerfile not found. Run this script from the server/ directory" -ForegroundColor Red
    exit 1
}

# Menu
Write-Host "What would you like to do?`n" -ForegroundColor Yellow
Write-Host "1. Initialize new Railway project"
Write-Host "2. Deploy to existing Railway project"
Write-Host "3. View deployment status"
Write-Host "4. Add/update environment variables"
Write-Host "5. View logs`n"

$choice = Read-Host "Enter your choice (1-5)"

switch ($choice) {
    "1" {
        Write-Host "`n🔧 Initializing new Railway project..." -ForegroundColor Cyan
        railway init
        Write-Host "`n📝 Next steps:" -ForegroundColor Yellow
        Write-Host "1. Set up RedisCloud at https://app.rediscloud.com, TLS enabled"
        Write-Host "2. Run: railway variable set REDIS_HOST=your-host"
        Write-Host "3. Run: railway variable set REDIS_PORT=your-port"
        Write-Host "4. Run: railway variable set REDIS_PASSWORD=your-password"
        Write-Host "5. Run: railway variable set REDIS_SSL=true  # only if the plan offers TLS"
        Write-Host "6. Run: railway variable set JORDAN_ADMIN_TOKEN=<random hex>  # or JORDAN_ADMIN_USERS"
        Write-Host "7. Run: railway variable set JORDAN_REGISTRATION_KEY=<random hex>"
        Write-Host "8. Run: railway up"
        Write-Host "`nWithout step 6 every /jordan/admin/* request is refused; without step 7"
        Write-Host "anyone reaching the URL can register a client. Checklist before going"
        Write-Host "public: RAILWAY_DEPLOYMENT.md" -ForegroundColor Yellow
    }
    "2" {
        Write-Host "`n📦 Deploying to Railway..." -ForegroundColor Cyan
        railway up
        Write-Host "`n✅ Deployment started! View it at:" -ForegroundColor Green
        railway open
    }
    "3" {
        Write-Host "`n📊 Deployment status:" -ForegroundColor Cyan
        railway status
    }
    "4" {
        Write-Host "`n🔐 Current environment variables:" -ForegroundColor Cyan
        railway variable list
        Write-Host "`nEnter variable to add/update (format: KEY=VALUE):" -ForegroundColor Yellow
        $var = Read-Host "Variable"
        if ($var) {
            $parts = $var.Split("=")
            if ($parts.Count -eq 2) {
                railway variable add "$($parts[0])=$($parts[1])"
                Write-Host "✅ Variable updated" -ForegroundColor Green
            } else {
                Write-Host "❌ Invalid format. Use: KEY=VALUE" -ForegroundColor Red
            }
        }
    }
    "5" {
        Write-Host "`n📋 Recent logs:" -ForegroundColor Cyan
        railway logs --tail 50
    }
    default {
        Write-Host "❌ Invalid choice" -ForegroundColor Red
    }
}

Write-Host "`n✨ Done!" -ForegroundColor Green
