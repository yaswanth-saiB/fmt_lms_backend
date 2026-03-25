# fmt-backend — CLAUDE.md

## Project Overview
Spring Boot backend for **First Million Trade** — an online learning platform where a mentor teaches trading courses. Students join online, watch recordings, and manage accounts. Zoom Pro is used for meetings/recordings (stored in Zoom Cloud).

**Team:**
- Yash (backend — Java, Spring Boot, PostgreSQL)
- Friend 1 (frontend — React, deployed on Vercel)
- Friend 2 (mentor whose courses we are building the platform for)

**Deployment:**
- Backend: AWS EC2 (Ubuntu, free tier) — `api.firstmilliontrade.com`
- Frontend: Vercel — `https://firstmilliontrade.com` (home) + `https://app.firstmilliontrade.com` (login/signup)
- DB: PostgreSQL on same EC2 instance (local, not RDS — plan to migrate to RDS later)
- Domain: Hostinger → DNS pointed to EC2 Elastic IP
- SSL: Let's Encrypt via Certbot (auto-renews)
- Process manager: systemd (`fmt-backend.service`) — NOT nohup

---

## Tech Stack
- **Java 17**, Spring Boot 3.x
- **PostgreSQL** (via JPA/Hibernate, `ddl-auto: update`)
- **JWT** (jjwt library)
- **SendGrid** — transactional email (OTP, welcome, promo, support, admin)
- **Twilio** — SMS OTP
- **Swagger / SpringDoc** — API docs at `/swagger-ui.html`
- **Lombok** — boilerplate reduction
- **Nginx** — reverse proxy (port 443/80 → 8080)

---

## Architecture — Auth Flow

### Cookie-Based JWT (what the frontend expects)
After login the server sets **two HttpOnly cookies**:

| Cookie | Path | Max-Age | Purpose |
|--------|------|---------|---------|
| `access_token` | `/` | 6 hours | JWT for API auth |
| `refresh_token` | `/api/auth/token` | 14 days | Rotate access token |

- The browser stores cookies automatically and sends them on every request.
- **No tokens in response body** after login — only user info.
- CSRF protection: `SameSite=Strict` on both cookies.
- Production: `COOKIE_SECURE=true`, `COOKIE_DOMAIN=.firstmilliontrade.com`

### JWT Claims
Every access token contains:
```json
{
  "sub": "user@email.com",
  "userId": "uuid-string",
  "sessionId": "uuid-string",
  "role": "STUDENT | MENTOR | ADMIN",
  "deviceId": "uuid-string",
  "iat": ...,
  "exp": ...
}
```

### Filter Order (JwtAuthenticationFilter)
1. Look for `access_token` cookie → use if found
2. Fallback to `Authorization: Bearer <token>` header → for Swagger testing
3. After JWT signature validation: check `user.getIsActive()` → 401 JSON if deactivated
4. Check `userSessionRepository.findBySessionIdAndActiveTrue(sessionId)` → 401 JSON if session not found/revoked

> This makes session revocation **immediate** — revoking a device kicks the user out on next request, not after token expiry.
> Error JSON: `{"success":false,"message":"Session expired. Please log in again."}` or `{"success":false,"message":"Account is deactivated"}`

### Cookie API (CookieService)
- `buildAuthCookieHeaders(accessToken, refreshToken)` → returns `HttpHeaders`
- `buildClearCookieHeaders()` → returns `HttpHeaders` with Max-Age=0
- Attach to ResponseEntity: `ResponseEntity.ok().headers(cookieService.buildAuthCookieHeaders(...)).body(result)`
- **Do NOT** call `HttpServletResponse.addHeader(SET_COOKIE, ...)` directly — Spring Security's
  response wrapper chain in MVC 6.x swallows those headers before the response is committed.

---

## User Roles
| Role | Description |
|------|-------------|
| `STUDENT` | Default. Can watch courses, join meetings |
| `MENTOR` | The teacher. Can create/manage courses |
| `ADMIN` | Full access. Receives enquiry emails |

---

## Login Flow (2-step)
```
POST /api/auth/login              { email, password }
    → validates credentials
    → sends OTP to email (+ mobile if phone exists — skips SMS if null)
    → returns { requiresOtp: true }

POST /api/auth/login/verify-otp  { email, otp }
    → validates OTP (email OR mobile OTP accepted)
    → checks isActive — 400 if deactivated
    → Sets cookies: access_token, refresh_token
    → Returns: { userId, email, firstName, lastName, role, mustChangePassword, sessionId, expiresIn }
```

> `mustChangePassword: true` means frontend must redirect to change-password page before allowing other navigation.

## Signup Flow (4-step with OTP)
```
POST /api/auth/signup/send-email-otp
POST /api/auth/signup/verify-email-otp
POST /api/auth/signup/send-mobile-otp
POST /api/auth/signup/verify-mobile-otp  ← sets cookies on success
```

## Quick Test Signup (no OTP)
```
POST /api/auth/signup/simple   — FOR TESTING ONLY — sets cookies
```
> ⚠️ Must be disabled before go-live. Gate with env var `SIMPLE_SIGNUP_ENABLED=false`.

---

## Key Entities
| Entity | Table | Notes |
|--------|-------|-------|
| `User` | `users` | UUID PK, roles: STUDENT/MENTOR/ADMIN. Has `mustChangePassword` flag. |
| `DeviceEntity` | `devices` | One per browser/device. Fingerprint = UUID_v3(MD5(UA+IP), UTF-8) |
| `UserSession` | `user_sessions` | Created at login. `sessionId` embedded in JWT |
| `RefreshTokenEntity` | `refresh_tokens` | Linked to device + session. Has `sessionId` column |
| `OtpEntity` | `otps` | Email/mobile OTP records |
| `Enquiry` | `enquiries` | Public enquiry form submissions. Status: NEW → CONTACTED → CLOSED |

### User.mustChangePassword
- `true` for all admin-created users (via `POST /api/admin/users` or OTP-verified flow)
- `false` for self-registered users (signup flow)
- Cleared to `false` automatically when user calls `PUT /api/auth/change-password`
- Returned in `/api/auth/me` and login verify-otp response
- Frontend must redirect to change-password screen if `mustChangePassword === true`

---

## Device Management
- Fingerprint: `UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8))` where raw = `trim(UserAgent) + "|" + clientIP`
- Fingerprint changes intentionally on IP change (VPN switch, mobile network) = new device
- Max 2 active devices per user — enforced automatically on new login (oldest device auto-revoked)
- Max 1 streaming session at a time per user
- `revokeDevice()` revokes both `refresh_tokens` AND `user_sessions` for that device
- `cleanupInactiveDevices()` runs at 2:30 AM daily via `@Scheduled` — marks devices inactive if not seen in 30 days
- `@EnableScheduling` is on `SecurityConfig`

---

## Token Endpoints
| Endpoint | Auth | Notes |
|----------|------|-------|
| `POST /api/auth/token/refresh` | Public | Must be public — access_token may be expired |
| `POST /api/auth/token/rotate`  | Public | Must be public — access_token may be expired |
| `GET  /api/auth/token/validate`| Yes    | Debug — inspect JWT claims |
| `POST /api/auth/token/revoke-all` | Yes | Logout all devices — revokes all DB tokens + sessions |

> `revoke-all` extracts `userId` from JWT via `jwtService.extractUserId(token)`, loads User, calls
> `tokenService.revokeAllUserTokens(user)` then clears cookies.

---

## Scheduled Jobs
| Job | Schedule | What it does |
|-----|----------|--------------|
| `TokenService.cleanupExpiredTokens()` | 2:00 AM daily | Deletes expired/revoked refresh tokens + inactive sessions |
| `DeviceService.cleanupInactiveDevices()` | 2:30 AM daily | Marks devices inactive + revokes tokens/sessions if not seen in 30 days |

> `@EnableScheduling` must be present (it's on `SecurityConfig`). The `scheduling.enabled: true` in
> application.yaml is a custom property and does NOT enable scheduling by itself.

---

## Email (SendGrid)
All transactional email goes via SendGrid API (not SMTP). The 5 Hostinger mailboxes are SMTP
configs used as sender identities — all actual delivery is via SendGrid.

| EmailType | Sender | BCC Archive | Used For |
|-----------|--------|-------------|----------|
| OTP | noreply-otp@firstmilliontrade.com | No | Login + signup OTPs |
| WELCOME | noreply-info@firstmilliontrade.com | Yes | After successful registration |
| PROMO | noreply-info@firstmilliontrade.com | Yes | Marketing |
| SUPPORT | help@firstmilliontrade.com | No | Support replies |
| ADMIN | admin@firstmilliontrade.com | Yes | Internal notifications |
| ENQUIRY | admin@firstmilliontrade.com | No | Enquiry form → admin |

> All email methods are `@Async` — they do not block the request thread.

---

## Security Config Key Rules
```
Public (no token):
  OPTIONS /**                         — preflight
  /api/auth/login
  /api/auth/login/verify-otp
  /api/auth/signup/**
  /api/auth/token/refresh             ← must be public (access_token may be expired)
  /api/auth/token/rotate              ← must be public
  /api/auth/forgot-password
  /api/auth/reset-password
  /api/enquiry/submit
  /swagger-ui/**, /v3/api-docs/**
  /actuator/**
  /api/test/**, /api/public/**

Protected (require valid access_token):
  /api/auth/logout
  /api/auth/me
  /api/auth/change-password
  /api/auth/token/**                  (validate + revoke-all)
  /api/devices/**
  /api/user/**
  /api/admin/**   → ADMIN role only
  /api/mentor/**  → MENTOR role only
  /api/student/** → STUDENT role only
```

---

## CORS Allowed Origins
```
http://localhost:3000               — dev (CRA)
http://localhost:5173               — dev (Vite)
https://firstmilliontrade.com       — production home
https://www.firstmilliontrade.com   — production home (www)
https://app.firstmilliontrade.com   — production login/signup app
https://www.app.firstmilliontrade.com
https://api.firstmilliontrade.com   — self-reference
```
> `PATCH` is NOT in allowed methods. Add it to SecurityConfig if ever needed.

---

## Environment Variables (required)
```
# Database
DATABASE_URL                        (jdbc:postgresql://localhost:5432/fmt in prod)
DATABASE_USERNAME                   (fmtuser in prod)
DATABASE_PASSWORD

# JWT
JWT_SECRET                          (Base64-encoded — generate: openssl rand -base64 32)
JWT_ACCESS_TOKEN_EXPIRATION         (ms, default 21600000 = 6h)
JWT_REFRESH_TOKEN_EXPIRATION        (ms, default 1209600000 = 14d)

# Cookie
COOKIE_SECURE                       (true in production, false in dev)
COOKIE_DOMAIN                       (blank for localhost, .firstmilliontrade.com in prod)

# App
APP_BASE_URL                        (https://api.firstmilliontrade.com in prod)
APP_FRONTEND_URL                    (https://firstmilliontrade.com in prod)
APP_ENVIRONMENT                     (development | production)

# SendGrid
SENDGRID_API_KEY
SENDGRID_ENABLED                    (default true)
SENDGRID_ARCHIVE_ENABLED            (default true)

# Twilio
TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_PHONE_NUMBER

# Email — 5 Hostinger mailboxes (each: HOST, PORT=465, USERNAME, PASSWORD, FROM)
OTP_EMAIL_*     → noreply-otp@firstmilliontrade.com
INFO_EMAIL_*    → noreply-info@firstmilliontrade.com
HELP_EMAIL_*    → help@firstmilliontrade.com
ARCHIVE_EMAIL_* → archive@firstmilliontrade.com
ADMIN_EMAIL_*   → admin@firstmilliontrade.com

# Admin DataSeeder (first-boot bootstrap — creates default ADMIN if none exists)
ADMIN_SEED_EMAIL                    (default: admin@firstmilliontrade.com)
ADMIN_SEED_PASSWORD                 (default: Admin@1234 — CHANGE IN PRODUCTION)
ADMIN_SEED_FIRST_NAME               (default: Admin)
ADMIN_SEED_LAST_NAME                (default: FMT)

# Zoom (required — app fails to start if missing)
ZOOM_ACCOUNT_ID
ZOOM_CLIENT_ID
ZOOM_CLIENT_SECRET
```
> Local .env file is at project root. **Never commit it.**
> Production .env is at `/home/ubuntu/.env` on EC2 (chmod 600).
> **⚠️ ZOOM_* vars are required even if not using Zoom features — missing them causes startup failure.**

---

## Build & Run
```bash
./mvnw spring-boot:run              # local dev
./mvnw clean package -DskipTests   # build JAR
java -jar target/fmt-backend-*.jar  # run JAR directly
```
Swagger UI: http://localhost:8080/swagger-ui.html

---

## Production Deployment (EC2)

### Server Details
- EC2 Ubuntu, Elastic IP assigned (domain won't break on restart)
- Java 17, PostgreSQL 16, Nginx installed
- App runs as systemd service: `sudo systemctl status fmt-backend`

### Nginx Config (`/etc/nginx/sites-enabled/default`)
```nginx
# HTTP → HTTPS redirect
server {
    listen 80;
    server_name api.firstmilliontrade.com;
    return 301 https://$host$request_uri;
}

# HTTPS + proxy to Spring Boot
server {
    listen 443 ssl;
    server_name api.firstmilliontrade.com;

    ssl_certificate     /etc/letsencrypt/live/api.firstmilliontrade.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.firstmilliontrade.com/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    location / {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;   # required for cookie Secure flag
    }
}
```
> `X-Forwarded-Proto` is critical — without it device fingerprinting breaks (all users get same IP).

### systemd Service (`/etc/systemd/system/fmt-backend.service`)
```ini
[Unit]
Description=FMT Backend
After=network.target postgresql.service

[Service]
User=ubuntu
EnvironmentFile=/home/ubuntu/.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /home/ubuntu/fmt-backend.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### Deploy Commands (run from local machine)
```bash
# 1. Build
./mvnw clean package -DskipTests

# 2. Copy JAR
scp -i fmt-backend-key.pem \
    target/fmt-backend-0.0.1-SNAPSHOT.jar \
    ubuntu@<EC2-IP>:/home/ubuntu/fmt-backend.jar

# 3. Restart
ssh -i fmt-backend-key.pem ubuntu@<EC2-IP> \
    "sudo systemctl restart fmt-backend"

# 4. Watch logs
ssh -i fmt-backend-key.pem ubuntu@<EC2-IP> \
    "sudo journalctl -u fmt-backend -f"
```

### Useful EC2 Commands
```bash
sudo systemctl status fmt-backend      # check status
sudo systemctl restart fmt-backend     # restart
sudo journalctl -u fmt-backend -f      # live logs
sudo journalctl -u fmt-backend -n 100  # last 100 lines
sudo nginx -t                          # test nginx config
sudo systemctl reload nginx            # reload nginx
```

### PostgreSQL (local on EC2)
```
DB name:   fmt
DB user:   fmtuser
Host:      localhost:5432
```
Connect: `psql -U fmtuser -d fmt -h localhost`

If tables are missing/corrupt (fresh deploy): connect as postgres superuser and run:
```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO fmtuser;
GRANT CREATE ON SCHEMA public TO fmtuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO fmtuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO fmtuser;
```
Then restart the app — Hibernate recreates all tables via `ddl-auto: update`.

---

## Swagger Testing Guide
The Swagger UI is at `/swagger-ui.html`.
1. Call `POST /api/auth/signup/simple` — sets cookies
2. Call `GET /api/auth/token/validate` — copy `accessToken` value
3. Click **Authorize** in Swagger → paste as `Bearer <token>`
4. Test protected endpoints

---

## Known Issues / Pre-Production TODO
- [ ] Disable `/api/auth/signup/simple` before go-live (gate with `SIMPLE_SIGNUP_ENABLED` env var)
- [ ] Change `ddl-auto: update` → `validate` before go-live (or add Flyway)
- [ ] Set `show-sql: false` in production application.yaml
- [ ] Fix logging package: `com.tradingapp` → `com.fmt.fmt_backend` in application.yaml
- [ ] Move DB from EC2 local → AWS RDS (currently on same EC2 instance — no automated backups)
- [ ] Add `PATCH` to CORS allowed methods if any future endpoint needs it
- [ ] Change `ADMIN_SEED_PASSWORD` default before go-live (currently `Admin@1234`)

---

## Decisions & Constraints
- UUID primary keys on all entities — prevents enumeration attacks, safe for distributed use
- Device fingerprint changes on IP change (VPN/mobile network switch) — intentional, treated as new device
- OTP sent to BOTH email and mobile on login; user can use either one; **SMS is skipped if phone is null/blank**
- `revokeDevice()` must call both `refreshTokenRepository` AND `userSessionRepository` — calling only one leaves stale records
- `/api/auth/token/refresh` and `/api/auth/token/rotate` must be `permitAll` in SecurityConfig — access_token may be expired when these are called
- Cookie headers must be set via `ResponseEntity.headers()` (not `HttpServletResponse.addHeader()`) — Spring MVC 6.x swallows raw addHeader calls
- Frontend must use `credentials: 'include'` (fetch) or `withCredentials: true` (axios) — without this no cookies are sent and every request gets 401
- `JwtAuthenticationFilter` now validates DB session per request (`userSessionRepository.findBySessionIdAndActiveTrue`) — session revocation is immediate, not delayed until token expiry
- Admin bypasses mentor ownership by reading mentorId from the batch/course entity itself, then calling existing service methods with that mentorId — no duplicate logic, no ownership check bypass
- Admin delete user cascade order (FK constraints): refresh_tokens → user_sessions → devices → batch_enrollments → otps → email_verification_tokens → users. MENTOR deletion blocked if they have courses.
- `mustChangePassword` is set `true` for all admin-created users; `false` for self-registered; cleared on `PUT /api/auth/change-password`
- DataSeeder (`ApplicationRunner`) creates default ADMIN on first startup only if no ADMIN exists — safe to leave enabled permanently
- Admin OTP registration: full user form + OTPs submitted in one request (`POST /api/admin/users/verify-and-create`) — no temp token needed
- `Map.of()` returns an unmodifiable map — always use `new HashMap<>()` when the map needs fields added later (e.g., in auth response building)

---

## Admin Module Endpoints (`/api/admin/**` — ADMIN role only)

### Dashboard
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/dashboard` | Stats + recent users + upcoming classes |

### User Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | All users (optional `?role=STUDENT\|MENTOR\|ADMIN`) |
| GET | `/api/admin/users/{id}` | Single user |
| POST | `/api/admin/users` | Create user (password auto-set, `mustChangePassword=true`) |
| PUT | `/api/admin/users/{id}/role` | Change role |
| PUT | `/api/admin/users/{id}/status` | Activate/deactivate |
| DELETE | `/api/admin/users/{id}` | Delete user (cascading — blocks MENTOR with courses) |
| POST | `/api/admin/users/{id}/reset-password` | Force reset password |
| POST | `/api/admin/users/send-otp` | Send email+mobile OTP before OTP-verified creation |
| POST | `/api/admin/users/verify-and-create` | Verify OTPs + create user (`mustChangePassword=true`) |

### Course Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/courses` | All courses |
| POST | `/api/admin/courses` | Create course (pass `mentorId`) |
| PUT | `/api/admin/courses/{id}` | Update course |
| PUT | `/api/admin/courses/{id}/toggle-active` | Toggle active status |

### Batch Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/batches` | All batches |
| POST | `/api/admin/batches` | Create batch (mentorId derived from course) |
| PUT | `/api/admin/batches/{id}/status` | Update batch status |

### Enrollment
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/students/search` | Search students by name/email (`?query=`) |
| GET | `/api/admin/batches/{id}/students` | Students in a batch |
| POST | `/api/admin/batches/{id}/enroll` | Enroll student (`{ studentId }`) |
| DELETE | `/api/admin/batches/{id}/students/{studentId}` | Unenroll student |

### Meetings
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/classes` | All meetings |
| POST | `/api/admin/meetings` | Create meeting (mentorId derived from batch) |
| GET | `/api/admin/batches/{id}/meetings` | Meetings for a batch |

### Recordings
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/recordings` | All recordings |

### Enquiries
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/enquiries` | All enquiries (optional `?status=NEW\|CONTACTED\|CLOSED`) |
| GET | `/api/admin/enquiries/{id}` | Single enquiry |
| PUT | `/api/admin/enquiries/{id}/status` | Update enquiry status |

---

## Mentor Module Enquiry Endpoints (`/api/mentor/**` — MENTOR role only)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/mentor/enquiries` | All enquiries (optional `?status=`) |
| GET | `/api/mentor/enquiries/{id}` | Single enquiry |
| PUT | `/api/mentor/enquiries/{id}/status` | Update enquiry status |

---

## Admin Bootstrap (DataSeeder)
- `DataSeeder` implements `ApplicationRunner` — runs on every startup
- Checks `userRepository.countByUserRole(ADMIN)` — if > 0, skips (idempotent)
- Creates admin from env vars `ADMIN_SEED_*` (see Environment Variables section)
- Default credentials: `admin@firstmilliontrade.com` / `Admin@1234`
- **Change `ADMIN_SEED_PASSWORD` in EC2 `.env` before first production deploy**

---

## Documents Generated (in project root)
- `JWT_Token_Flow.docx` — JWT, access/refresh token, signup/login/logout explained
- `Device_Management.docx` — device fingerprinting, limits, streaming, API reference
- `API_Reference_Frontend.docx` — all endpoints with payloads and responses
- `FMT_Frontend_Integration_Guide.docx` — complete frontend integration guide (share this with frontend team)
- `Enquiry_Module_Frontend_Integration.md` — enquiry management endpoints for admin + mentor
- `Admin_Content_Management_Frontend_Integration.md` — admin courses/batches/enrollment/meetings endpoints
- `Admin_Module_Frontend_Integration.md` — full admin module reference including delete user
