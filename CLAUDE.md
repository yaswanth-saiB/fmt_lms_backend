# fmt-backend — CLAUDE.md

## Project Overview
Spring Boot backend for **First Million Trade** — an online learning platform where a mentor teaches trading courses. Students join online, watch recordings, and manage accounts. Zoom Pro is used for meetings/recordings (stored in Zoom Cloud).

**Team:**
- Yash (backend — Java, Spring Boot, PostgreSQL)
- Friend 1 (frontend — React, deployed on Vercel)
- Friend 2 (mentor whose courses we are building the platform for)

**Deployment:** Backend on AWS (free tier), Frontend on Vercel, DB on AWS RDS / Railway PostgreSQL.

---

## Tech Stack
- **Java 17**, Spring Boot 3.x
- **PostgreSQL** (via JPA/Hibernate, `ddl-auto: update`)
- **JWT** (jjwt library)
- **SendGrid** — transactional email
- **Twilio** — SMS OTP
- **Swagger / SpringDoc** — API docs at `/swagger-ui.html`
- **Lombok** — boilerplate reduction

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
- Production: set env var `COOKIE_SECURE=true` so cookies are HTTPS-only.

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
    → sends OTP to email + mobile
    → returns { requiresOtp: true }

POST /api/auth/login/verify-otp  { email, otp }
    → validates OTP
    → Sets cookies: access_token, refresh_token
    → Returns: { userId, email, firstName, lastName, role, sessionId, expiresIn }
```

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

---

## Key Entities
| Entity | Table | Notes |
|--------|-------|-------|
| `User` | `users` | UUID PK, roles: STUDENT/MENTOR/ADMIN |
| `DeviceEntity` | `devices` | One per browser/device. Fingerprint = hash(UA+IP) |
| `UserSession` | `user_sessions` | Created at login. `sessionId` embedded in JWT |
| `RefreshTokenEntity` | `refresh_tokens` | Linked to device + session |
| `OtpEntity` | `otps` | Email/mobile OTP records |
| `Enquiry` | `enquiries` | Public enquiry form submissions |

---

## Environment Variables (required)
```
# Database
DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD

# JWT
JWT_SECRET                          (Base64-encoded, min 256-bit)
JWT_ACCESS_TOKEN_EXPIRATION         (ms, default 21600000 = 6h)
JWT_REFRESH_TOKEN_EXPIRATION        (ms, default 1209600000 = 14d)

# Email (5 mailboxes — each has HOST, PORT, USERNAME, PASSWORD, FROM)
OTP_EMAIL_HOST, OTP_EMAIL_PORT, OTP_EMAIL_USERNAME, OTP_EMAIL_PASSWORD, OTP_EMAIL_FROM
INFO_EMAIL_HOST, INFO_EMAIL_PORT, INFO_EMAIL_USERNAME, INFO_EMAIL_PASSWORD, INFO_EMAIL_FROM
HELP_EMAIL_HOST, HELP_EMAIL_PORT, HELP_EMAIL_USERNAME, HELP_EMAIL_PASSWORD, HELP_EMAIL_FROM
ARCHIVE_EMAIL_HOST, ARCHIVE_EMAIL_PORT, ARCHIVE_EMAIL_USERNAME, ARCHIVE_EMAIL_PASSWORD, ARCHIVE_EMAIL_FROM
ADMIN_EMAIL_HOST, ADMIN_EMAIL_PORT, ADMIN_EMAIL_USERNAME, ADMIN_EMAIL_PASSWORD, ADMIN_EMAIL_FROM

# SendGrid
SENDGRID_API_KEY
SENDGRID_ENABLED                    (default true)
SENDGRID_ARCHIVE_ENABLED            (default true)

# Twilio
TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_PHONE_NUMBER

# App
APP_BASE_URL                        (default https://api.firstmilliontrade.com)
APP_FRONTEND_URL                    (default http://localhost:3000)
APP_ENVIRONMENT                     (development | production)

# Cookie
COOKIE_SECURE                       (true in production, false in dev)
COOKIE_DOMAIN                       (blank for localhost, .firstmilliontrade.com in prod)

# Port
PORT                                (injected by Railway/AWS, default 8080)
```

---

## Build & Run
```bash
./mvnw spring-boot:run          # local dev
./mvnw clean package -DskipTests
java -jar target/fmt-backend-*.jar
```
Swagger UI: http://localhost:8080/swagger-ui.html

---

## Swagger Testing Guide (see testing plan below)
The Swagger UI is at `/swagger-ui.html`. For protected endpoints:
1. Call `POST /api/auth/signup/simple` or `POST /api/auth/login/verify-otp`
2. Copy the `accessToken` from browser DevTools → Cookies OR... the filter also accepts `Authorization: Bearer <token>`.
3. Click **Authorize** in Swagger → paste the token → test protected endpoints.

---

## Decisions & Constraints
- `ddl-auto: update` — Hibernate manages schema. Fine for early dev; switch to Flyway before go-live.
- Device fingerprint = `MD5(userAgent + IP)` — changes on VPN/IP change (intentional).
- OTP sent to BOTH email and mobile on login; user can use either one.
- Max 2 active sessions per user (`device.max-sessions-per-user=2`).
- Max 1 streaming session at a time (`device.max-streaming-sessions=1`).
- Cookie headers must be set via `ResponseEntity.headers()` (not `HttpServletResponse.addHeader()`).
  Spring MVC 6.x / Spring Security's response wrapper chain swallows raw `addHeader` calls
  before the response is committed. Use `CookieService.buildAuthCookieHeaders()` and attach
  to `ResponseEntity`.
