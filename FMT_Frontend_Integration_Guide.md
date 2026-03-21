# First Million Trade — Frontend Integration Guide

**Production API:** `https://api.firstmilliontrade.com`
**Swagger Docs:** `https://api.firstmilliontrade.com/swagger-ui.html`
**Version:** 1.0 · March 2026

> CONFIDENTIAL — Internal use only. Do not share publicly.

---

## Table of Contents

1. [Setup & Configuration](#1-setup--configuration)
2. [How Authentication Works (Cookies)](#2-how-authentication-works-cookies)
3. [Axios / Fetch Setup](#3-axios--fetch-setup)
4. [Auth State Management](#4-auth-state-management)
5. [Signup Flow (4-Step OTP)](#5-signup-flow-4-step-otp)
6. [Login Flow (2-Step OTP)](#6-login-flow-2-step-otp)
7. [Logout](#7-logout)
8. [Token Management](#8-token-management)
9. [User Profile](#9-user-profile)
10. [Device Management](#10-device-management)
11. [Enquiry Form](#11-enquiry-form)
12. [Error Handling](#12-error-handling)
13. [Complete Endpoint Reference](#13-complete-endpoint-reference)

---

## 1. Setup & Configuration

### API Base URLs

| Environment | URL |
|-------------|-----|
| Production | `https://api.firstmilliontrade.com` |
| Local dev | `http://localhost:8080` |

### CORS — Allowed Frontend Origins

The backend only accepts requests from these origins. Any other origin will get a CORS error.

| Origin | Purpose |
|--------|---------|
| `http://localhost:3000` | Local dev (CRA) |
| `http://localhost:5173` | Local dev (Vite) |
| `https://firstmilliontrade.com` | Production home |
| `https://www.firstmilliontrade.com` | Production home (www) |
| `https://app.firstmilliontrade.com` | Production app (login/signup) |

### Standard Response Envelope

Every single API response — success or error — uses this structure:

```json
{
  "success":   true,
  "message":   "Human readable status message",
  "timestamp": "2026-03-15T21:00:00",
  "data":      { }
}
```

> Always check the `success` field in the body, not just the HTTP status code.

---

## 2. How Authentication Works (Cookies)

This API uses **HttpOnly cookies** — NOT localStorage tokens. After login or signup, the server sets two cookies automatically. The browser stores them and sends them on every subsequent request. JavaScript cannot read these cookies (`HttpOnly` = secure by design).

### The Two Cookies

| Cookie | Path | Lifetime | Purpose |
|--------|------|----------|---------|
| `access_token` | `/` | 6 hours | Authenticates every API request |
| `refresh_token` | `/api/auth/token` | 14 days | Silently refreshes the access token |

> The `refresh_token` cookie has `path=/api/auth/token` — the browser **only** sends it to the refresh/rotate/revoke-all endpoints. This limits its exposure.

### Cookie Flags (Production)

| Flag | Value |
|------|-------|
| `HttpOnly` | true — JS cannot read it |
| `Secure` | true — HTTPS only |
| `SameSite` | Strict — no cross-site sending |
| `Domain` | `.firstmilliontrade.com` |

### How the Frontend Receives Them

After a successful login or signup, the server sets cookies in the response headers:

```
Set-Cookie: access_token=eyJhbGc...; Path=/; Max-Age=21600; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refresh_token=uuid-string; Path=/api/auth/token; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict
```

> You do **NOT** need to read or store these cookies. The browser handles everything automatically.

---

## 3. Axios / Fetch Setup

> `credentials: "include"` (fetch) or `withCredentials: true` (axios) is **MANDATORY**.
> Without it, no cookies are sent and every protected request returns `401`.

### Axios — Recommended Setup

Create once at `src/api/axios.js` and import everywhere:

```js
import axios from "axios";

const api = axios.create({
  baseURL: "https://api.firstmilliontrade.com",
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

// Silent Token Refresh Interceptor
// When access_token expires (6h), auto-refresh and retry the original request
let isRefreshing = false;
let failedQueue  = [];

const processQueue = (error) => {
  failedQueue.forEach(p => error ? p.reject(error) : p.resolve());
  failedQueue = [];
};

api.interceptors.response.use(
  response => response,
  async error => {
    const original = error.config;

    if (error.response?.status === 401 && !original._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(() => api(original)).catch(e => Promise.reject(e));
      }

      original._retry = true;
      isRefreshing    = true;

      try {
        await api.post("/api/auth/token/refresh");  // refresh_token cookie sent automatically
        processQueue(null);
        return api(original);                        // retry original request
      } catch (refreshError) {
        processQueue(refreshError);
        window.location.href = "/login";             // refresh failed — force login
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

### Using the API Instance

```js
import api from "@/api/axios";

const response = await api.post("/api/auth/login", { email, password });
const { success, message, data } = response.data;

if (success) {
  console.log(data);
} else {
  console.error(message);
}
```

### Fetch Alternative

```js
const response = await fetch("https://api.firstmilliontrade.com/api/auth/me", {
  method: "GET",
  credentials: "include",
  headers: { "Content-Type": "application/json" },
});
const data = await response.json();
```

---

## 4. Auth State Management

Since tokens are in HttpOnly cookies (not localStorage), you cannot check login state by reading a token. The pattern is:

- Call `GET /api/auth/me` on app load
- If `200` → user is logged in
- If `401` → not logged in, redirect to login

### Recommended Pattern (React Context)

```jsx
// src/context/AuthContext.jsx
import { createContext, useContext, useEffect, useState } from "react";
import api from "@/api/axios";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser]       = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get("/api/auth/me")
      .then(res => setUser(res.data.data))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  const logout = async () => {
    await api.post("/api/auth/logout");
    setUser(null);
    window.location.href = "/login";
  };

  return (
    <AuthContext.Provider value={{ user, setUser, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
```

### User Object Shape (from `/api/auth/me`)

```json
{
  "id":               "uuid-string",
  "email":            "user@example.com",
  "firstName":        "Yash",
  "lastName":         "Doe",
  "role":             "STUDENT",
  "isEmailVerified":  true,
  "isMobileVerified": true
}
```

`role` values: `STUDENT` | `MENTOR` | `ADMIN`

---

## 5. Signup Flow (4-Step OTP)

All steps must be executed in order:

```
Step 1: POST /api/auth/signup/send-email-otp    → sends OTP to email
Step 2: POST /api/auth/signup/verify-email-otp  → verifies email OTP
Step 3: POST /api/auth/signup/send-mobile-otp   → sends OTP to mobile (SMS)
Step 4: POST /api/auth/signup/verify-mobile-otp → verifies mobile OTP, creates account, sets cookies
```

---

### Step 1 — `POST /api/auth/signup/send-email-otp` `PUBLIC`

Sends a 6-digit OTP to the provided email. OTP expires in 5 minutes.

**Request Body:**
```json
{
  "firstName":   "Yash",
  "lastName":    "Doe",
  "email":       "yash@example.com",
  "password":    "Test@1234",
  "phoneNumber": "+919876543210",
  "gender":      "MALE",
  "city":        "Hyderabad",
  "state":       "Telangana",
  "country":     "India",
  "postalCode":  "500001"
}
```

| Field | Required | Rules |
|-------|----------|-------|
| `firstName` | Yes | 2–100 characters |
| `lastName` | Yes | 1–100 characters — single character allowed (e.g. "A") |
| `email` | Yes | Valid email format |
| `password` | Yes | Min 8 chars, must have 1 uppercase, 1 lowercase, 1 digit, 1 special character (`@#$%^&+=!`), no spaces |
| `phoneNumber` | No | Format: `+[country][number]` e.g. `+919876543210` |
| `gender` | No | `MALE` / `FEMALE` / `OTHER` |
| `city`, `state`, `country`, `postalCode` | No | — |

**Success Response (200):**
```json
{
  "success": true,
  "message": "OTP sent to your email. Please verify to continue.",
  "data": "yash@example.com"
}
```

**Error Responses:**

| HTTP | Message | Cause |
|------|---------|-------|
| 400 | `"Email is already registered"` | Duplicate account |
| 400 | `"Password must contain..."` | Password validation failed |
| 400 | `"First name is required"` | Missing required field |

---

### Step 2 — `POST /api/auth/signup/verify-email-otp` `PUBLIC`

Verify the OTP received in email.

**Query Parameters:**
```
?email=yash@example.com&otp=482910
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Email verified successfully.",
  "data": { "email": "yash@example.com", "emailVerified": true }
}
```

**Error Responses:**

| HTTP | Message | Cause |
|------|---------|-------|
| 400 | `"Invalid or expired OTP"` | Wrong OTP or OTP expired |
| 400 | `"Too many attempts. Please request a new OTP."` | Exceeded 3 attempts |

---

### Step 3 — `POST /api/auth/signup/send-mobile-otp` `PUBLIC`

Sends OTP via SMS to the mobile number.

**Query Parameters:**
```
?email=yash@example.com&mobile=+919876543210
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "OTP sent to your mobile. Please verify to complete registration.",
  "data": "+919876543210"
}
```

---

### Step 4 — `POST /api/auth/signup/verify-mobile-otp` `PUBLIC`

Final step — verifies mobile OTP, creates the account, and sets auth cookies.

**Request:**
```
URL: POST /api/auth/signup/verify-mobile-otp?otp=482910
Body: Same full object as Step 1 (re-send all fields)
```

**Success Response (200) + Sets Cookies:**
```
Set-Cookie: access_token=eyJ...; Path=/; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refresh_token=uuid; Path=/api/auth/token; HttpOnly; Secure; SameSite=Strict
```
```json
{
  "success": true,
  "message": "Registration successful! Welcome to First Million Trade.",
  "data": {
    "userId":    "uuid",
    "email":     "yash@example.com",
    "firstName": "Yash",
    "lastName":  "Doe",
    "role":      "STUDENT",
    "sessionId": "uuid",
    "expiresIn": 21600
  }
}
```

> After this call succeeds: store `userId`, `email`, `firstName`, `role` in your auth context. Cookies are already set by the browser automatically.

---

## 6. Login Flow (2-Step OTP)

```
Step 1: POST /api/auth/login             → validates password, sends OTP to email + mobile
Step 2: POST /api/auth/login/verify-otp  → verifies OTP (either email or mobile), sets cookies
```

---

### Step 1 — `POST /api/auth/login` `PUBLIC`

Validates credentials and sends OTP to both email and mobile. Does **not** return any token yet.

**Request Body:**
```json
{
  "email":    "yash@example.com",
  "password": "Test@1234"
}
```

**Success Response (200) — No cookies yet:**
```json
{
  "success": true,
  "message": "OTP sent to your email and mobile. Enter any one to login.",
  "data": {
    "email":         "yash@example.com",
    "requiresOtp":   true,
    "expiryMinutes": 5
  }
}
```

**Error Responses:**

| HTTP | Message | Cause |
|------|---------|-------|
| 400 | `"Invalid email or password"` | Wrong credentials |
| 400 | `"Account is locked until..."` | Too many failed attempts |
| 400 | `"Email not verified"` | Account not fully set up |

---

### Step 2 — `POST /api/auth/login/verify-otp` `PUBLIC`

Verifies OTP and completes login. Either the email OTP or the SMS OTP is accepted.

**Query Parameters:**
```
?email=yash@example.com&otp=482910
```

**Success Response (200) + Sets Cookies:**
```
Set-Cookie: access_token=eyJ...; Path=/; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refresh_token=uuid; Path=/api/auth/token; HttpOnly; Secure; SameSite=Strict
```
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "userId":    "uuid",
    "email":     "yash@example.com",
    "firstName": "Yash",
    "lastName":  "Doe",
    "role":      "STUDENT",
    "sessionId": "uuid",
    "expiresIn": 21600
  }
}
```

**Error Responses:**

| HTTP | Message | Cause |
|------|---------|-------|
| 400 | `"Invalid or expired OTP"` | Wrong or timed-out OTP |
| 400 | `"Too many attempts. Please request a new OTP."` | Exceeded 3 attempts |

---

## 7. Logout

### `POST /api/auth/logout` `PROTECTED`

Logs out the current device only. Revokes its tokens/session and clears cookies.

**Request:** No body, no params.

**Success Response (200) + Clears Cookies:**
```
Set-Cookie: access_token=; Max-Age=0; Path=/
Set-Cookie: refresh_token=; Max-Age=0; Path=/api/auth/token
```
```json
{ "success": true, "message": "Logout successful" }
```

**Frontend logout handler:**
```js
const logout = async () => {
  await api.post("/api/auth/logout");
  setUser(null);
  window.location.href = "/login";
};
```

---

### `POST /api/auth/token/revoke-all` `PROTECTED`

Logout from **all devices** — revokes every session. Use for a "Sign out everywhere" feature.

**Request:** No body. Reads `access_token` cookie automatically.

**Success Response (200) + Clears Cookies:**
```json
{
  "success": true,
  "message": "All sessions revoked. You have been logged out from all devices."
}
```

---

## 8. Token Management

> The Axios interceptor in [Section 3](#3-axios--fetch-setup) handles token refresh automatically. You only need these endpoints if building custom logic.

### `POST /api/auth/token/refresh` `PUBLIC`

Get a new `access_token` when the current one expires. This endpoint is **public** — call it even when the `access_token` is expired.

The `refresh_token` cookie is sent automatically (its path `/api/auth/token` matches this URL). No request body needed.

**Success Response (200) — Updates `access_token` cookie:**
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": { "sessionId": "uuid", "expiresIn": 21600 }
}
```

**Error Responses:**

| HTTP | Message | Cause |
|------|---------|-------|
| 401 | `"Refresh token expired or invalid"` | User must log in again |
| 401 | `"Session not found"` | Session was revoked |

---

### `GET /api/auth/token/validate` `PROTECTED`

Debug endpoint — inspect the current token claims. Useful during development.

**Success Response (200):**
```json
{
  "success": true,
  "data": {
    "valid":     true,
    "email":     "yash@example.com",
    "userId":    "uuid",
    "sessionId": "uuid",
    "role":      "STUDENT",
    "deviceId":  "uuid",
    "expired":   false
  }
}
```

---

## 9. User Profile

### `GET /api/auth/me` `PROTECTED`

Get the currently authenticated user profile. **Call this on app load** to check login state.

**Request:** No body, no params.

**Success Response (200):**
```json
{
  "success": true,
  "message": "User info",
  "data": {
    "id":               "uuid",
    "email":            "yash@example.com",
    "firstName":        "Yash",
    "lastName":         "Doe",
    "role":             "STUDENT",
    "isEmailVerified":  true,
    "isMobileVerified": true
  }
}
```

**Error Response (401):**
```json
{ "success": false, "message": "Authentication required" }
```

> A `401` here means the user is not logged in (no cookie or expired). Redirect to login page.

---

## 10. Device Management

Each user can have a maximum of **2 active devices**. A device is registered automatically on each login. Use these endpoints to build an "Active Sessions" page.

### `GET /api/devices` `PROTECTED`

List all active devices for the current user.

**Success Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "deviceId":        "uuid",
      "deviceName":      "Chrome on Windows 10",
      "ipAddress":       "192.168.1.***",
      "lastActive":      "2026-03-15T21:00:00",
      "firstSeen":       "2026-03-01T08:00:00",
      "isStreaming":     false,
      "userAgent":       "Mozilla/5.0 (Windows NT...",
      "isCurrentDevice": true
    }
  ]
}
```

---

### `DELETE /api/devices/{deviceId}` `PROTECTED`

Revoke a specific device — force logout on that device.

**Path param:** `deviceId` — UUID from the devices list

**Success Response (200):**
```json
{ "success": true, "message": "Device revoked successfully" }
```

---

### `POST /api/devices/streaming/start` `PROTECTED`

Mark a device as streaming before playing a course video. Max **1 device** can stream at a time.

**Query param:** `?deviceId=uuid`

**Success Response (200):**
```json
{ "success": true, "message": "Streaming started" }
```

**Error (400) — Already streaming on another device:**
```json
{
  "success": false,
  "message": "Streaming already active on: Chrome on Windows 10 (192.168.1.***). Stop streaming there first."
}
```

---

### `POST /api/devices/streaming/stop` `PROTECTED`

Mark device as no longer streaming. Call this when the video ends or the user leaves the page.

**Query param:** `?deviceId=uuid`

**Success Response (200):**
```json
{ "success": true, "message": "Streaming stopped" }
```

### Streaming Guard — Frontend Pattern

```js
const deviceId = "uuid-from-get-devices-response";

// Before playing video
const res = await api.post(`/api/devices/streaming/start?deviceId=${deviceId}`);
if (!res.data.success) {
  alert(res.data.message);   // "Streaming already active on: ..."
  return;
}
startVideoPlayer();

// When video ends or user leaves
await api.post(`/api/devices/streaming/stop?deviceId=${deviceId}`);
```

---

## 11. Enquiry Form

### `POST /api/enquiry/submit` `PUBLIC`

Submit an enquiry from the public home page. Sends an email notification to the admin.

**Request Body:**
```json
{
  "name":            "Ravi Kumar",
  "mobile":          "+919876543210",
  "city":            "Hyderabad",
  "experienceLevel": "Beginner",
  "areaOfInterest":  "Stock Market",
  "message":         "I want to learn trading..."
}
```

| Field | Required |
|-------|----------|
| `name` | Yes |
| `mobile` | Yes |
| `city`, `experienceLevel`, `areaOfInterest`, `message` | No |

**Success Response (200):**
```json
{ "success": true, "message": "Enquiry submitted successfully" }
```

---

## 12. Error Handling

### HTTP Status Code Reference

| Status | Meaning | What to do |
|--------|---------|-----------|
| `200` | Success | Check `success` field in body |
| `400` | Validation / business error | Show `message` to user |
| `401` | Not authenticated | Redirect to `/login` |
| `403` | Wrong role / access denied | Redirect to `/unauthorized` |
| `500` | Server error | Show generic error message |

### Global Error Handler

```js
api.interceptors.response.use(
  response => response,
  error => {
    const status  = error.response?.status;
    const message = error.response?.data?.message || "Something went wrong";

    if (status === 400) {
      toast.error(message);
    } else if (status === 403) {
      window.location.href = "/unauthorized";
    } else if (status === 500) {
      toast.error("Server error. Please try again later.");
    }

    return Promise.reject(error);
  }
);
```

### Validation Error Shape (400)

```json
{
  "success": false,
  "message": "Password must contain: 1 digit, 1 lowercase, 1 uppercase, 1 special character"
}
```

> Show the `message` field directly to the user — it is always human-readable.

---

## 13. Complete Endpoint Reference

### Auth Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/signup/send-email-otp` | Public | Step 1 — send email OTP |
| POST | `/api/auth/signup/verify-email-otp` | Public | Step 2 — verify email OTP |
| POST | `/api/auth/signup/send-mobile-otp` | Public | Step 3 — send mobile OTP |
| POST | `/api/auth/signup/verify-mobile-otp` | Public | Step 4 — verify mobile OTP, sets cookies |
| POST | `/api/auth/login` | Public | Step 1 — validate credentials, send OTP |
| POST | `/api/auth/login/verify-otp` | Public | Step 2 — verify OTP, sets cookies |
| POST | `/api/auth/logout` | Protected | Logout current device |
| GET | `/api/auth/me` | Protected | Get current user profile |
| POST | `/api/auth/token/refresh` | Public | Refresh access token |
| GET | `/api/auth/token/validate` | Protected | Debug — inspect token claims |
| POST | `/api/auth/token/revoke-all` | Protected | Logout all devices |

### Device Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/devices` | Protected | List active devices |
| DELETE | `/api/devices/{deviceId}` | Protected | Revoke a device |
| POST | `/api/devices/streaming/start?deviceId=` | Protected | Start streaming |
| POST | `/api/devices/streaming/stop?deviceId=` | Protected | Stop streaming |

### Public Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/enquiry/submit` | Public | Submit enquiry form |

---

## Key Reminders

- `withCredentials: true` (axios) or `credentials: "include"` (fetch) on **every** request
- Never store tokens in localStorage — cookies are set and managed automatically by the browser
- Call `GET /api/auth/me` on app load to check if user is already logged in
- Use the Axios interceptor (Section 3) for automatic silent token refresh
- The `refresh_token` cookie auto-sends only to `/api/auth/token/*` paths
- A `401` from `/api/auth/token/refresh` means the user must log in again
- Get `deviceId` from `GET /api/devices` — use it for streaming start/stop calls
- `role` field: `STUDENT` | `MENTOR` | `ADMIN` — use for route guards
