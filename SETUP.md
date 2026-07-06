# TranslationApp Setup Guide

## Prerequisites

- Java 17+, Maven 3.9+
- Node.js 18+
- MySQL 8.0+ (local install on port 3306; Docker optional)

## 1. MySQL Database

### Option A: Docker Compose

```powershell
cd D:\workspace\TranslationApp
docker compose up -d mysql
```

This creates database `translation_app` and runs `schema.sql` + `seed.sql` automatically.


### Option C: Local MySQL service (Windows, port 3306)

Ensure MySQL is running (e.g. service **MySQL84**). Default app credentials match local dev when using env vars or updated pplication.yml default password.

`powershell
$env:Path = "D:\software\MySQL\MySQL Server 8.4\bin;" + $env:Path
.\scripts\init-db.ps1 -Password 1234qwer -Host localhost -Port 3306
`


### Option B: Manual MySQL (local install)

Default client path on this machine:

`D:\software\MySQL\MySQL Server 8.4\bin\mysql.exe`

```powershell
$MySql = "D:\software\MySQL\MySQL Server 8.4\bin\mysql.exe"
& $MySql -u root -p1234qwer -e "CREATE DATABASE IF NOT EXISTS translation_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
Get-Content backend\src\main\resources\db\schema.sql -Raw -Encoding UTF8 | & $MySql -u root -p1234qwer --default-character-set=utf8mb4 translation_app
Get-Content backend\src\main\resources\db\seed.sql -Raw -Encoding UTF8 | & $MySql -u root -p1234qwer --default-character-set=utf8mb4 translation_app
```

Or use the helper script (uses MySQL 8.4 bin path by default):

```powershell
.\scripts\init-db.ps1
# or override password / client:
.\scripts\init-db.ps1 -Password 1234qwer -MySqlBin "D:\software\MySQL\MySQL Server 8.4\bin\mysql.exe"
```

## 2. Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_HOST` | `localhost` | MySQL host |
| `MYSQL_PORT` | `3306` | MySQL port |
| `MYSQL_DATABASE` | `translation_app` | Database name |
| `MYSQL_USER` | `root` | DB username |
| `MYSQL_PASSWORD` | `1234qwer` | DB password |
| `DEEPL_API_KEY` | — | DeepL API key for EN→ZH translation (Free keys end with `:fx`) |
| `DEEPL_API_URL` | `https://api-free.deepl.com/v2/translate` | DeepL endpoint (`:fx` keys → Free URL; Pro keys → `https://api.deepl.com/v2/translate`; auto-corrected if mismatched) |
| `TRANSLATION_ON_CRAWL` | `true` | Translate pages during crawl |

See [docs/DOCUMENT_TRANSLATION.md](docs/DOCUMENT_TRANSLATION.md) for architecture, data flow, and how translation is triggered.
| `SEED_DEFAULT_USERS` | `true` | Seed admin/user on startup if tables empty |
| `DEFAULT_ADMIN_PASSWORD` | `admin123` | Admin password (runtime seed) |
| `JPA_DDL_AUTO` | `validate` | Set to `update` for dev schema sync |
| `SPRING_PROFILES_ACTIVE` | `default` | Use `h2` for file-based H2 without MySQL |

Example (PowerShell):

```powershell
$env:MYSQL_PASSWORD = "1234qwer"
$env:DEEPL_API_KEY = "your-deepl-key"
$env:TRANSLATION_ON_CRAWL = "true"
```

## 2b. IM Infrastructure (Redis, MinIO)

Required for the IM module (chat, attachments). MySQL is usually already running from step 1.

```powershell
# Recommended: skips Docker Hub pull when images exist locally
.\scripts\start-infra.ps1

# Or manually (no pull if images cached):
docker compose up -d --no-build redis minio minio-init

# Full stack including MySQL + WebRTC TURN:
.\scripts\start-infra.ps1 -IncludeMysql -IncludeCoturn
```

If `docker compose pull` times out (Docker Hub blocked/slow), configure a registry mirror in **Docker Desktop → Settings → Docker Engine**:

```json
{
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://docker.m.daocloud.io"
  ]
}
```

Apply & restart Docker Desktop, then re-run. If `redis:7-alpine` / `minio/minio` already exist locally (`docker images`), use `.\scripts\start-infra.ps1` or `docker compose up -d --no-build` without pull.

## 3. Start Services

```powershell
.\scripts\start-all.ps1
```

Or individually:

```powershell
# Terminal 1 - crawler (port 3000)
cd crawler-service; npm start

# Terminal 2 - backend (port 8080)
cd backend; mvn spring-boot:run -DskipTests

# Terminal 3 - frontend (port 5173)
cd frontend; npm run dev
```

## 4. Login

Open http://localhost:5173

- **admin** / **admin123** (from seed.sql or DataInitializer)
- **user** / **user123**

### Registration

Open http://localhost:5173/register

- **账号注册**: username + password (min 8 chars, letter + digit)
- **手机注册**: phone + SMS code (dev mock code: **123456**, set `SMS_MOCK_CODE`)
- **第三方**: GitHub / 微信 / QQ / 企业微信 (see OAuth section below)

After registration, JWT is issued automatically (same as login).

## 4b. OAuth Setup

Configure third-party login in `application.yml` or environment variables.

| Provider | Env vars | Notes |
|----------|----------|-------|
| GitHub | `GITHUB_OAUTH_ENABLED=true`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` | [GitHub OAuth Apps](https://github.com/settings/developers) — callback: `http://localhost:8080/api/auth/oauth/github/callback` |
| 微信 | `WECHAT_OAUTH_ENABLED`, `WECHAT_APP_ID`, `WECHAT_APP_SECRET`, `WECHAT_REDIRECT_URI` | [微信开放平台](https://open.weixin.qq.com/) 网站应用 |
| QQ | `QQ_OAUTH_ENABLED`, `QQ_APP_ID`, `QQ_APP_KEY`, `QQ_REDIRECT_URI` | [QQ 互联](https://connect.qq.com/) |
| 企业微信 | `WECOM_OAUTH_ENABLED`, `WECOM_CORP_ID`, `WECOM_CORP_SECRET`, `WECOM_AGENT_ID`, `WECOM_REDIRECT_URI` | [企业微信管理后台](https://work.weixin.qq.com/) |

Common:

| Variable | Default | Description |
|----------|---------|-------------|
| `OAUTH_FRONTEND_CALLBACK` | `http://localhost:5173/oauth/callback` | Frontend URL after OAuth success |
| `OAUTH_DEMO_MODE` | `false` | When `true`, unconfigured providers use demo mock login |
| `SMS_MOCK_ENABLED` | `true` | Use mock SMS instead of real provider |
| `SMS_MOCK_CODE` | `123456` | Dev verification code |

Example GitHub (PowerShell):

```powershell
$env:GITHUB_OAUTH_ENABLED = "true"
$env:GITHUB_CLIENT_ID = "your_github_client_id"
$env:GITHUB_CLIENT_SECRET = "your_github_client_secret"
$env:GITHUB_REDIRECT_URI = "http://localhost:8080/api/auth/oauth/github/callback"
```

WeChat / QQ / WeCom: authorize URLs and callback routes are wired; complete token exchange in `OAuthService` when credentials are available. Without credentials, buttons show **配置后可使用** (or enable `OAUTH_DEMO_MODE` for demo).

### Database migration (existing DB)

If upgrading an existing database:

```powershell
Get-Content backend\src\main\resources\db\migration-auth-registration.sql -Raw -Encoding UTF8 | mysql -u root -p translation_app
```

## 5. Crawl Any Documentation URL

1. Enter a base URL (e.g. `https://docs.spring.io/spring-boot/reference/` or any doc site)
2. Choose task type **文档**
3. Set max pages
4. Click **开始爬取**

The crawler auto-detects navigation (sidebar, TOC, Antora/Spring patterns) and preserves HTML structure. Content is translated to Chinese when `DEEPL_API_KEY` is set.

## SQL Script Locations

- Schema: `backend/src/main/resources/db/schema.sql`
- Seed: `backend/src/main/resources/db/seed.sql`

## 6. Incremental Database Export

Export all tables into date-named folders (one folder per day):

```powershell
.\scripts\export-db.ps1
```

Output layout:

```
data-exports/incremental/2026-07-04/
  metadata.json      # export time, table list, row counts
  users.sql
  crawl_tasks.sql
  documents.sql
  ...
```

Re-running on the same day overwrites that day's folder. To keep multiple exports per day:

```powershell
.\scripts\export-db.ps1 -SameDayMode timestamp
```

See `scripts/README.md` for all options (`-IncludeSchema`, custom `-MySqlBin`, etc.).

Exports are gitignored under `data-exports/`.

## H2 Fallback (no MySQL)

```powershell
cd backend
mvn spring-boot:run -DskipTests -Dspring-boot.run.profiles=h2
```

Uses file DB at `./data/translationdb`.


