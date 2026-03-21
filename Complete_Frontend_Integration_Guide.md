# First Million Trade — Complete Frontend Integration Guide

**Production API:** `https://api.firstmilliontrade.com`
**Swagger (dev):** `http://localhost:8080/swagger-ui.html`
**Version:** 1.0 · March 2026

> CONFIDENTIAL — Internal use only. Do not share publicly.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Setup & Axios Configuration](#2-setup--axios-configuration)
3. [How Authentication Works](#3-how-authentication-works)
4. [JWT — What's Inside the Token](#4-jwt--whats-inside-the-token)
5. [Auth State Management](#5-auth-state-management)
6. [Signup Flow (4-Step OTP)](#6-signup-flow-4-step-otp)
7. [Login Flow (2-Step OTP)](#7-login-flow-2-step-otp)
8. [Logout](#8-logout)
9. [Token Management](#9-token-management)
10. [User Profile](#10-user-profile)
11. [Device Management](#11-device-management)
12. [Enquiry Form](#12-enquiry-form)
13. [Mentor APIs](#13-mentor-apis)
14. [Student APIs](#14-student-apis)
15. [Error Handling](#15-error-handling)
16. [Role-Based Routing](#16-role-based-routing)
17. [Quick Reference — All Endpoints](#17-quick-reference--all-endpoints)

---

## 1. Project Overview

| Item | Value |
|------|-------|
| Backend | Spring Boot 3, Java 17 |
| Auth | HttpOnly cookies (JWT access token + UUID refresh token) |
| Roles | `STUDENT`, `MENTOR`, `ADMIN` |
| OTP delivery | Email (SendGrid) + SMS (Twilio) |
| Live classes | Zoom (Server-to-Server OAuth) |
| Production URL | `https://api.firstmilliontrade.com` |

### CORS — Allowed Origins

| Origin | Purpose |
|--------|---------|
| `http://localhost:3000` | Local dev (CRA) |
| `http://localhost:5173` | Local dev (Vite) |
| `https://firstmilliontrade.com` | Production home |
| `https://www.firstmilliontrade.com` | Production home (www) |
| `https://app.firstmilliontrade.com` | Production app |

### Universal Response Format

Every response — success or error — uses this envelope:

```json
{
  "success":   true,
  "message":   "Human-readable status message",
  "timestamp": "2026-03-21T10:00:00",
  "data":      { }
}
```

> Always check `response.data.success` before using `response.data.data`. Null fields are omitted from the response (not sent as `null`).

---

## 2. Setup & Axios Configuration

### Install

```bash
npm install axios
```

### Create axios instance — `src/api/axios.js`

Create once, import everywhere. This handles cookies and silent token refresh automatically.

```js
import axios from "axios";

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || "http://localhost:8080",
  withCredentials: true,   // MANDATORY — sends HttpOnly cookies on every request
  headers: {
    "Content-Type": "application/json",
  },
});

// Silent token refresh interceptor
// When access_token expires (6h), auto-refreshes and retries the original request
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
        return api(original);                        // retry the original request
      } catch (refreshError) {
        processQueue(refreshError);
        window.location.href = "/login";             // refresh failed — force login
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    if (error.response?.status === 403) {
      window.location.href = "/unauthorized";
    }

    return Promise.reject(error);
  }
);

export default api;
```

### Basic usage

```js
import api from "@/api/axios";

const res = await api.post("/api/auth/login", { email, password });
const { success, message, data } = res.data;
```

---

## 3. How Authentication Works

After login or signup, the server sets **two HttpOnly cookies** in the response headers. The browser stores and sends them automatically on every request. JavaScript cannot read these cookies — that is intentional (security).

### The Two Cookies

| Cookie | Path | Lifetime | Purpose |
|--------|------|----------|---------|
| `access_token` | `/` | 6 hours | Authenticates every API request |
| `refresh_token` | `/api/auth/token` | 14 days | Issues new access tokens silently |

> `refresh_token` has `path=/api/auth/token` — the browser only sends it to the `/api/auth/token/*` endpoints. It is never exposed to other paths.

### Cookie flags (production)

| Flag | Value |
|------|-------|
| `HttpOnly` | `true` — JS cannot read it |
| `Secure` | `true` — HTTPS only |
| `SameSite` | `Strict` — no cross-site sending |
| `Domain` | `.firstmilliontrade.com` |

### What happens on every authenticated request

```
Browser → sends Cookie: access_token=<jwt>
             ↓
JwtAuthenticationFilter
  1. Reads access_token cookie
  2. Falls back to Authorization: Bearer header (Swagger only)
  3. Verifies JWT signature
  4. Extracts email → loads User from DB
  5. Sets Spring Security context
             ↓
Request reaches the controller
```

> The JWT itself is stateless — no DB lookup on the token. The DB is only hit to load the User object.

### You do NOT need to

- Store tokens in `localStorage` or `sessionStorage`
- Manually attach tokens to headers
- Track token expiry — the axios interceptor handles refresh silently

---

## 4. JWT — What's Inside the Token

The access token is a signed JWT. It is **not encrypted** — anyone can decode it, but nobody can forge one without the server's secret key.

### JWT Payload (claims)

```json
{
  "sub":       "user@email.com",
  "userId":    "uuid-string",
  "sessionId": "uuid-string",
  "role":      "STUDENT",
  "deviceId":  "uuid-string",
  "iat":       1234567890,
  "exp":       1234567890
}
```

| Claim | Type | Description |
|-------|------|-------------|
| `sub` | string | User's email address |
| `userId` | UUID | User's database ID |
| `sessionId` | UUID | Current session ID (created at login) |
| `role` | string | `STUDENT` / `MENTOR` / `ADMIN` |
| `deviceId` | UUID | Device fingerprint ID (browser + IP) |
| `iat` | unix timestamp | Issued at |
| `exp` | unix timestamp | Expires at (iat + 6 hours) |

### What happens after token generation (on login/signup)

1. A `DeviceEntity` is created — fingerprint = `MD5(UserAgent + IP)`
2. A `UserSession` is created — new `sessionId` saved to `user_sessions` table
3. `access_token` JWT is signed with server secret, expires in 6 hours
4. `refresh_token` is a random UUID string saved to `refresh_tokens` table
5. Both set as HttpOnly cookies in the response
6. Response body returns: `userId`, `email`, `firstName`, `lastName`, `role`, `sessionId`

### Token refresh vs token rotation

| | `POST /api/auth/token/refresh` | `POST /api/auth/token/rotate` |
|-|-------------------------------|------------------------------|
| Issues new `access_token` | Yes | Yes |
| Issues new `refresh_token` | No (reuses existing) | Yes (old one revoked) |
| Use case | Standard silent refresh | Extended sessions / higher security |

> **Important:** Even after `revoke-all`, an existing access token continues to pass JWT verification for up to 6 hours (it is stateless). Revocation prevents new tokens being issued via refresh — it does not immediately invalidate existing access tokens.

---

## 5. Auth State Management

Since cookies are HttpOnly, you cannot check login state by reading a token from JS. The correct pattern:

- Call `GET /api/auth/me` on **app load**
- `200` → user is logged in → set user in state
- `401` → not logged in → show login page

### React Context — `src/context/AuthContext.jsx`

```jsx
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
      {!loading && children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
```

### User object shape

```json
{
  "id":               "uuid",
  "email":            "user@example.com",
  "firstName":        "Yash",
  "lastName":         "Doe",
  "role":             "STUDENT",
  "isEmailVerified":  true,
  "isMobileVerified": true
}
```

---

## 6. Signup Flow (4-Step OTP)

Steps must run in order. Store form data in component state across steps — each step is a separate API call.

```
Step 1 → POST /api/auth/signup/send-email-otp    → OTP sent to email
Step 2 → POST /api/auth/signup/verify-email-otp  → email OTP verified
Step 3 → POST /api/auth/signup/send-mobile-otp   → OTP sent to mobile (SMS)
Step 4 → POST /api/auth/signup/verify-mobile-otp → mobile OTP verified → account created → cookies set
```

---

### Step 1 — `POST /api/auth/signup/send-email-otp` `PUBLIC`

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

**Field validation:**

| Field | Required | Rules |
|-------|----------|-------|
| `firstName` | Yes | 2–100 characters |
| `lastName` | Yes | 1–100 characters |
| `email` | Yes | Valid email format |
| `password` | Yes | Min 8 chars · 1 digit · 1 lowercase · 1 uppercase · 1 special char (`@#$%^&+=!`) · no spaces |
| `phoneNumber` | No | Format: `+919876543210` (10–15 digits, optional `+`) |
| `gender` | No | `MALE` / `FEMALE` / `OTHER` |
| `city`, `state`, `country`, `postalCode` | No | Free text |

**Success Response `200`:**
```json
{
  "success": true,
  "message": "OTP sent to your email. Please verify to continue.",
  "data": "yash@example.com"
}
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Email is already registered"` | Duplicate account |
| `"First name is required"` | Missing field |
| `"Password must contain: 1 digit, 1 lowercase, 1 uppercase, 1 special character"` | Weak password |
| `"Invalid phone number"` | Wrong phone format |

---

### Step 2 — `POST /api/auth/signup/verify-email-otp` `PUBLIC`

**Query Params:**
```
?email=yash@example.com&otp=482910
```

**No request body.**

**Success Response `200`:**
```json
{
  "success": true,
  "message": "Email verified successfully.",
  "data": { "email": "yash@example.com", "emailVerified": true }
}
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Invalid or expired OTP"` | Wrong OTP or 5-min expiry passed |
| `"Too many attempts. Please request a new OTP."` | Exceeded 3 attempts |

---

### Step 3 — `POST /api/auth/signup/send-mobile-otp` `PUBLIC`

**Query Params:**
```
?email=yash@example.com&mobile=+919876543210
```

**No request body.**

**Success Response `200`:**
```json
{
  "success": true,
  "message": "OTP sent to your mobile. Please verify to complete registration.",
  "data": "+919876543210"
}
```

---

### Step 4 — `POST /api/auth/signup/verify-mobile-otp` `PUBLIC`

**Query Param + Body:**
```
URL: POST /api/auth/signup/verify-mobile-otp?otp=738291
Body: same full SignUpRequest object as Step 1 (re-send all fields)
```

**Success Response `200` — sets cookies:**

Response Headers:
```
Set-Cookie: access_token=eyJ...; Path=/; Max-Age=21600; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refresh_token=uuid; Path=/api/auth/token; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict
```

Response Body:
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

> After this call: store `userId`, `email`, `firstName`, `role` in auth context. Redirect based on `role`.

---

## 7. Login Flow (2-Step OTP)

```
Step 1 → POST /api/auth/login             → credentials validated → OTP sent
Step 2 → POST /api/auth/login/verify-otp  → OTP verified → cookies set
```

---

### Step 1 — `POST /api/auth/login` `PUBLIC`

**Request Body:**
```json
{
  "email":    "yash@example.com",
  "password": "Test@1234"
}
```

**Success Response `200` — no cookies yet:**
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

| Message | Cause |
|---------|-------|
| `"Invalid email or password"` | Wrong credentials |
| `"Account is locked until 2026-03-21T10:15:00"` | 5 failed attempts → 15 min lock |
| `"Email not verified"` | Account not fully set up |

---

### Step 2 — `POST /api/auth/login/verify-otp` `PUBLIC`

Either the email OTP or the SMS OTP is accepted — user can use whichever arrives first.

**Query Params:**
```
?email=yash@example.com&otp=482910
```

**No request body.**

**Success Response `200` — sets cookies:**

Response Headers:
```
Set-Cookie: access_token=eyJ...; Path=/; Max-Age=21600; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refresh_token=uuid; Path=/api/auth/token; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict
```

Response Body:
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

| Message | Cause |
|---------|-------|
| `"Invalid or expired OTP"` | Wrong or timed-out OTP |
| `"Too many attempts. Please request a new OTP."` | Exceeded 3 attempts |

---

## 8. Logout

### `POST /api/auth/logout` `PROTECTED`

Logs out current device only. Other devices stay logged in.

**Request:** No body, no params.

**Response Headers:**
```
Set-Cookie: access_token=; Max-Age=0; Path=/
Set-Cookie: refresh_token=; Max-Age=0; Path=/api/auth/token
```

**Response Body `200`:**
```json
{ "success": true, "message": "Logout successful" }
```

**Frontend:**
```js
const logout = async () => {
  await api.post("/api/auth/logout");
  setUser(null);
  window.location.href = "/login";
};
```

---

### `POST /api/auth/token/revoke-all` `PROTECTED`

Logout from **all devices** — "Sign out everywhere" button.

**Request:** No body. Token read from cookie.

**Response Headers:** Same clear-cookie headers as logout.

**Response Body `200`:**
```json
{
  "success": true,
  "message": "All sessions revoked. You have been logged out from all devices."
}
```

---

## 9. Token Management

> The axios interceptor in Section 2 handles refresh automatically. These endpoints are documented for completeness.

### `POST /api/auth/token/refresh` `PUBLIC`

Issues a new `access_token`. Call this when you get a 401 on any protected request. The `refresh_token` cookie is sent automatically (its path matches `/api/auth/token`).

**Request:** No body, no params.

**Response `200` — updates `access_token` cookie:**
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": { "sessionId": "uuid", "expiresIn": 21600 }
}
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Refresh token expired or invalid"` | 14-day refresh token expired — user must login again |
| `"Session not found"` | Session was revoked (e.g. by revoke-all) |

---

### `POST /api/auth/token/rotate` `PUBLIC`

Issues a new `access_token` **and** a new `refresh_token` (revokes old refresh token). Use for extended sessions.

**Request:** No body.

**Response `200` — updates both cookies:**
```json
{
  "success": true,
  "message": "Token rotated successfully",
  "data": { "sessionId": "uuid", "expiresIn": 21600 }
}
```

---

### `GET /api/auth/token/validate` `PROTECTED`

Debug endpoint — inspect current token claims.

**Request:** No body, no params.

**Response `200`:**
```json
{
  "success": true,
  "message": "Token validated",
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

## 10. User Profile

### `GET /api/auth/me` `PROTECTED`

Get the authenticated user's profile. Call on app load to check login state.

**Request:** No body, no params.

**Response `200`:**
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

**Response `401`:**
```json
{ "success": false, "message": "Authentication required" }
```

> `401` here = no cookie or expired. Redirect to login.

---

## 11. Device Management

Max **2 active devices** per user. Registered automatically on login.

### `GET /api/devices` `PROTECTED`

**Request:** No body.

**Response `200`:**
```json
{
  "success": true,
  "message": "Devices retrieved",
  "data": [
    {
      "deviceId":        "uuid",
      "deviceName":      "Chrome on Windows 10",
      "ipAddress":       "192.168.1.***",
      "lastActive":      "2026-03-21T10:00:00",
      "firstSeen":       "2026-03-01T08:00:00",
      "isStreaming":     false,
      "userAgent":       "Mozilla/5.0 (Windows NT 10...",
      "isCurrentDevice": true
    }
  ]
}
```

---

### `DELETE /api/devices/{deviceId}` `PROTECTED`

Force-revoke a device. That device gets logged out immediately.

**Path Param:** `deviceId` — UUID from the devices list

**Response `200`:**
```json
{ "success": true, "message": "Device revoked successfully" }
```

---

### `POST /api/devices/check-limit` `PROTECTED`

Check if user is at the 2-device limit.

**Request:** No body.

**Response — within limit:**
```json
{ "success": true, "message": "Within device limit", "data": null }
```

**Response — over limit:**
```json
{
  "success": true,
  "message": "You have 3 active devices. Maximum allowed is 2.",
  "data": [
    { "deviceId": "uuid", "deviceName": "Chrome on Windows 10", "lastActive": "...", "ipAddress": "..." }
  ]
}
```

---

### `POST /api/devices/disconnect?deviceId={uuid}` `PROTECTED`

User-initiated disconnect of a specific device.

**Query Param:** `?deviceId=uuid`

**Response `200`:**
```json
{ "success": true, "message": "Device disconnected. You now have 1 active device(s)." }
```

---

### `POST /api/devices/streaming/start?deviceId={uuid}` `PROTECTED`

Mark device as streaming before playing a video. Max **1 device** can stream at a time.

**Query Param:** `?deviceId=uuid`

**Response `200`:**
```json
{ "success": true, "message": "Streaming started" }
```

**Response `400` — already streaming:**
```json
{
  "success": false,
  "message": "Streaming already active on: Chrome on Windows 10 (192.168.1.***). Stop streaming there first."
}
```

---

### `POST /api/devices/streaming/stop?deviceId={uuid}` `PROTECTED`

Mark device as no longer streaming. Call when video ends or user leaves the page.

**Query Param:** `?deviceId=uuid`

**Response `200`:**
```json
{ "success": true, "message": "Streaming stopped" }
```

### Streaming guard pattern

```js
const deviceId = "uuid-from-GET-/api/devices";

// Before playing video
const res = await api.post(`/api/devices/streaming/start?deviceId=${deviceId}`);
if (!res.data.success) {
  alert(res.data.message);   // "Streaming already active on: ..."
  return;
}
startVideoPlayer();

// When video ends / page unmounts
await api.post(`/api/devices/streaming/stop?deviceId=${deviceId}`);
```

---

## 12. Enquiry Form

### `POST /api/enquiry/submit` `PUBLIC`

Submit enquiry from the public home page. Sends email to admin.

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

**Response `200`:**
```json
{ "success": true, "message": "Enquiry submitted successfully" }
```

---

## 13. Mentor APIs

All `/api/mentor/**` endpoints require role `MENTOR`. Returns `403` otherwise.

### Enums

| Enum | Values |
|------|--------|
| `BatchStatus` | `UPCOMING` · `ACTIVE` · `COMPLETED` · `CANCELLED` |
| `MeetingStatus` | `UPCOMING` · `LIVE` · `ENDED` · `CANCELLED` |

---

### `GET /api/mentor/dashboard` `PROTECTED`

**Response `200`:**
```json
{
  "success": true,
  "message": "Dashboard loaded",
  "data": {
    "totalCourses":   3,
    "totalBatches":   7,
    "totalClasses":   42,
    "totalStudents":  128,
    "recentBatches":  [ ],
    "upcomingClasses": [ ]
  }
}
```

`recentBatches` → last 5 BatchResponse objects
`upcomingClasses` → next 5 MeetingResponse objects with `startUrl` included

---

### Courses

#### `GET /api/mentor/courses`

**Response `200` — array of CourseResponse:**
```json
[
  {
    "id":          "uuid",
    "title":       "Trading Fundamentals",
    "description": "Learn stock market basics",
    "price":       4999.00,
    "isActive":    true,
    "mentorName":  "Yash Reddy",
    "createdAt":   "2026-03-15T10:00:00"
  }
]
```

> Use this list to populate the **course dropdown** when creating a batch.

---

#### `POST /api/mentor/courses`

**Request Body:**
```json
{
  "title":       "Advanced Options Trading",
  "description": "Deep dive into options strategies",
  "price":       9999.00
}
```

| Field | Required | Validation |
|-------|----------|-----------|
| `title` | Yes | Not blank |
| `description` | No | — |
| `price` | No | Decimal number |

**Response `200`:** Single CourseResponse object.

---

#### `GET /api/mentor/courses/{courseId}`

**Response `200`:** Single CourseResponse object.

---

#### `GET /api/mentor/courses/{courseId}/batches`

Returns all batches under a specific course. Use on the **course detail page**.

**Response `200` — array of BatchResponse:**
```json
[
  {
    "id":            "uuid",
    "name":          "Batch 1 - March 2026",
    "courseId":      "uuid",
    "courseName":    "Trading Fundamentals",
    "startDate":     "2026-03-21",
    "endDate":       "2026-04-21",
    "maxStudents":   30,
    "enrolledCount": 12,
    "status":        "ACTIVE",
    "description":   "Morning batch, 10AM–12PM",
    "createdAt":     "2026-03-15T10:00:00"
  }
]
```

Returns empty `[]` if no batches yet.

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Course not found"` | Invalid courseId |
| `"Access denied"` | Course belongs to another mentor |

**UI Pattern:**
```js
// Load course info + its batches in parallel on the course detail page
const [course, setCourse] = useState(null);
const [batches, setBatches] = useState([]);

useEffect(() => {
  Promise.all([
    api.get(`/api/mentor/courses/${courseId}`),
    api.get(`/api/mentor/courses/${courseId}/batches`),
  ]).then(([courseRes, batchRes]) => {
    if (courseRes.data.success) setCourse(courseRes.data.data);
    if (batchRes.data.success) setBatches(batchRes.data.data);
  });
}, [courseId]);
```

---

### Batches

#### `GET /api/mentor/batches`

Returns all batches across all courses. Use to populate the **batch dropdown** when creating a class.

**Response `200`:** Array of BatchResponse (same shape as above).

---

#### `GET /api/mentor/batches/{batchId}`

**Response `200`:** Single BatchResponse object.

---

#### `POST /api/mentor/batches`

**UI Flow:** First call `GET /api/mentor/courses` to load the course dropdown. Use the selected course's `id` as `courseId`.

**Request Body:**
```json
{
  "name":        "Batch 2 - April 2026",
  "courseId":    "uuid",
  "startDate":   "2026-04-01",
  "endDate":     "2026-04-30",
  "maxStudents": 25,
  "description": "Evening batch, 6PM–8PM"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `name` | Yes | Not blank |
| `courseId` | Yes | From course dropdown — must be mentor's own course |
| `startDate` | No | `YYYY-MM-DD` |
| `endDate` | No | `YYYY-MM-DD` |
| `maxStudents` | No | Defaults to `30` |
| `description` | No | — |

**Response `200`:** BatchResponse object.

---

#### `PUT /api/mentor/batches/{batchId}/status`

Move a batch through its lifecycle.

**Request Body:**
```json
{ "status": "ACTIVE" }
```

| Status | When to use |
|--------|------------|
| `UPCOMING` | Created but not started (default) |
| `ACTIVE` | Running — students can join classes |
| `COMPLETED` | All classes done |
| `CANCELLED` | Batch cancelled |

**Response `200`:** Updated BatchResponse.

---

### Classes (Meetings)

#### `GET /api/mentor/classes`

All meetings across all batches. Includes `startUrl`.

**Response `200` — array of MeetingResponse:**
```json
[
  {
    "id":           "uuid",
    "zoomMeetingId":"84374937493",
    "topic":        "Day 1 - Market Intro",
    "batchId":      "uuid",
    "batchName":    "Batch 1 - March 2026",
    "status":       "UPCOMING",
    "scheduledAt":  "2026-03-21T10:00:00",
    "durationMins": 90,
    "createdAt":    "2026-03-20T10:00:00",
    "startUrl":     "https://zoom.us/s/84374937493?zak=...",
    "joinUrl":      "https://zoom.us/j/84374937493"
  }
]
```

---

#### `GET /api/mentor/batches/{batchId}/classes`

All meetings for a specific batch. Includes `startUrl`.

**Response `200`:** Array of MeetingResponse (same shape).

---

#### `POST /api/mentor/classes`

Creates a real Zoom meeting via Zoom API. Returns `startUrl` (host) and `joinUrl` (participants).

**UI Flow:** First call `GET /api/mentor/batches` to load batch dropdown. Use selected batch's `id` as `batchId`.

**Request Body:**
```json
{
  "batchId":      "uuid",
  "topic":        "Day 1 - Market Intro",
  "durationMins": 90,
  "scheduledAt":  "2026-03-21T10:00:00"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `batchId` | Yes | From batch dropdown |
| `topic` | Yes | Becomes Zoom meeting title |
| `durationMins` | No | Defaults to `120` |
| `scheduledAt` | No | `YYYY-MM-DDTHH:mm:ss` |

**Response `200`:**
```json
{
  "id":           "uuid",
  "zoomMeetingId":"84374937493",
  "topic":        "Day 1 - Market Intro",
  "batchId":      "uuid",
  "batchName":    "Batch 1 - March 2026",
  "status":       "UPCOMING",
  "scheduledAt":  "2026-03-21T10:00:00",
  "durationMins": 90,
  "createdAt":    "2026-03-20T10:00:00",
  "startUrl":     "https://zoom.us/s/84374937493?zak=...",
  "joinUrl":      "https://zoom.us/j/84374937493"
}
```

> Show `startUrl` as **"Start Class"** button → `window.open(startUrl, '_blank')`
> Show `joinUrl` as **"Copy Join Link"** — share with students

---

### Student Management

#### `GET /api/mentor/students/search?q={query}`

Autocomplete search — min 2 characters. Searches by name or email (partial, case-insensitive).

**Query Param:** `?q=raj`

**Response `200` — array of StudentSummaryResponse:**
```json
[
  {
    "id":          "uuid",
    "firstName":   "Raj",
    "lastName":    "Kumar",
    "email":       "raj@gmail.com",
    "phoneNumber": "9876543210",
    "enrolledAt":  null
  }
]
```

Returns `[]` if query is less than 2 characters.

**Usage:**
```js
// Debounced autocomplete
useEffect(() => {
  if (query.length < 2) { setResults([]); return; }
  const timer = setTimeout(async () => {
    const res = await api.get(`/api/mentor/students/search?q=${encodeURIComponent(query)}`);
    if (res.data.success) setResults(res.data.data);
  }, 300);
  return () => clearTimeout(timer);
}, [query]);
```

---

#### `POST /api/mentor/students/enroll`

**Request Body:**
```json
{
  "batchId":   "uuid",
  "studentId": "uuid"
}
```

| Field | Where to get it |
|-------|----------------|
| `batchId` | From URL param (if on batch page) or dropdown |
| `studentId` | From student search result (`student.id`) |

**Response `200`:**
```json
{ "success": true, "message": "Student enrolled successfully", "data": null }
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Student is already enrolled in this batch"` | Duplicate |
| `"Batch is full"` | `enrolledCount >= maxStudents` |
| `"User is not a student"` | Selected user is MENTOR/ADMIN |
| `"Student not found"` | Invalid studentId |

---

#### `GET /api/mentor/batches/{batchId}/students`

**Response `200` — array of StudentSummaryResponse:**
```json
[
  {
    "id":          "uuid",
    "firstName":   "Raj",
    "lastName":    "Kumar",
    "email":       "raj@gmail.com",
    "phoneNumber": "9876543210",
    "enrolledAt":  "2026-03-15T10:00:00"
  }
]
```

> `enrolledAt` is populated here — show it as enrollment date in the students table.

---

#### `DELETE /api/mentor/batches/{batchId}/students/{studentId}`

Soft-removes student from batch (data preserved, enrollment marked inactive).

**Path Params:** `batchId` + `studentId` — both UUIDs from the students list.

**Response `200`:**
```json
{ "success": true, "message": "Student unenrolled successfully", "data": null }
```

---

## 14. Student APIs

All `/api/student/**` endpoints require role `STUDENT`. Returns `403` otherwise.

---

### `GET /api/student/dashboard` `PROTECTED`

**Response `200`:**
```json
{
  "success": true,
  "message": "Dashboard loaded",
  "data": {
    "enrolledCoursesCount": 2,
    "enrolledBatches":      [ ],
    "upcomingClasses":      [ ]
  }
}
```

`upcomingClasses` → next 5 MeetingResponse objects. **No `startUrl`** — never present in student responses.

**MeetingResponse for students:**
```json
{
  "id":           "uuid",
  "topic":        "Day 1 - Market Intro",
  "batchId":      "uuid",
  "batchName":    "Batch 1 - March 2026",
  "status":       "UPCOMING",
  "scheduledAt":  "2026-03-21T10:00:00",
  "durationMins": 90,
  "createdAt":    "2026-03-20T10:00:00",
  "joinUrl":      "https://zoom.us/j/84374937493"
}
```

---

### `GET /api/student/courses` `PROTECTED`

All batches the student is enrolled in.

**Response `200` — array of BatchResponse:**
```json
[
  {
    "id":            "uuid",
    "name":          "Batch 1 - March 2026",
    "courseId":      "uuid",
    "courseName":    "Trading Fundamentals",
    "startDate":     "2026-03-21",
    "endDate":       "2026-04-21",
    "maxStudents":   30,
    "enrolledCount": 12,
    "status":        "ACTIVE",
    "description":   "Morning batch",
    "createdAt":     "2026-03-15T10:00:00"
  }
]
```

---

### `GET /api/student/schedule` `PROTECTED`

All upcoming classes across all enrolled batches. No `startUrl`.

**Response `200`:** Array of MeetingResponse (student view).

---

### `GET /api/student/batches/{batchId}/classes` `PROTECTED`

All classes (all statuses) for a specific batch. Student must be enrolled.

**Path Param:** `batchId` — from enrolled batches list

**Response `200`:** Array of MeetingResponse (no `startUrl`), newest first.

**Error:** `"You are not enrolled in this batch"` if not enrolled.

---

### `GET /api/student/classes/{meetingId}/join` `PROTECTED`

Get the Zoom join link for a specific class.

**Path Param:** `meetingId` — `id` UUID from the batch classes list

**Response `200`:**
```json
{
  "id":           "uuid",
  "topic":        "Day 1 - Market Intro",
  "batchId":      "uuid",
  "batchName":    "Batch 1 - March 2026",
  "status":       "LIVE",
  "scheduledAt":  "2026-03-21T10:00:00",
  "durationMins": 90,
  "joinUrl":      "https://zoom.us/j/84374937493"
}
```

**Error:** `"You are not enrolled in this batch"` if not enrolled.

**Usage:**
```js
const joinClass = async (meetingId) => {
  const res = await api.get(`/api/student/classes/${meetingId}/join`);
  if (!res.data.success) { toast.error(res.data.message); return; }

  const { joinUrl, status, scheduledAt } = res.data.data;

  if (status === "LIVE") {
    window.open(joinUrl, "_blank");
  } else if (status === "UPCOMING") {
    toast.info(`Class starts at ${new Date(scheduledAt).toLocaleString("en-IN", { timeZone: "Asia/Kolkata" })}`);
  } else if (status === "ENDED") {
    toast.warn("This class has already ended.");
  }
};
```

---

## 15. Error Handling

### HTTP Status Codes

| Status | Meaning | Action |
|--------|---------|--------|
| `200` | Success | Check `success` field |
| `400` | Validation / business error | Show `message` to user |
| `401` | Not authenticated | Redirect to `/login` |
| `403` | Wrong role / forbidden | Redirect to `/unauthorized` |
| `500` | Server error | Show generic message |

### Common Error Messages

| Message | Endpoint | Cause |
|---------|----------|-------|
| `"Email is already registered"` | Signup step 1 | Duplicate account |
| `"Invalid or expired OTP"` | Signup/Login OTP | Wrong OTP or expired |
| `"Invalid email or password"` | Login step 1 | Wrong credentials |
| `"Account is locked until..."` | Login step 1 | 5 failed attempts |
| `"Course not found"` | Batch/class endpoints | Invalid courseId |
| `"Batch not found"` | Class/enrollment endpoints | Invalid batchId |
| `"Student is already enrolled in this batch"` | Enroll | Duplicate |
| `"Batch is full"` | Enroll | Capacity reached |
| `"You are not enrolled in this batch"` | Student join | Not enrolled |
| `"Access denied"` | Mentor batch endpoints | Wrong mentor |
| `"Streaming already active on: ..."` | Streaming start | Already streaming elsewhere |

### Global error handler (add to axios interceptor)

```js
api.interceptors.response.use(
  response => response,
  error => {
    const status  = error.response?.status;
    const message = error.response?.data?.message || "Something went wrong";

    if (status === 400) toast.error(message);
    else if (status === 403) window.location.href = "/unauthorized";
    else if (status >= 500) toast.error("Server error. Please try again later.");

    return Promise.reject(error);
  }
);
```

---

## 16. Role-Based Routing

### Redirect after login

```js
const { role } = loginResponse.data;
switch (role) {
  case "MENTOR":  navigate("/mentor/dashboard"); break;
  case "STUDENT": navigate("/student/dashboard"); break;
  case "ADMIN":   navigate("/admin/dashboard"); break;
}
```

### Protected Route component

```jsx
const ProtectedRoute = ({ allowedRoles, children }) => {
  const { user, loading } = useAuth();

  if (loading) return <Spinner />;
  if (!user) return <Navigate to="/login" />;
  if (!allowedRoles.includes(user.role)) return <Navigate to="/unauthorized" />;

  return children;
};

// Router usage
<Route path="/mentor/dashboard" element={
  <ProtectedRoute allowedRoles={["MENTOR"]}><MentorDashboard /></ProtectedRoute>
} />
<Route path="/student/dashboard" element={
  <ProtectedRoute allowedRoles={["STUDENT"]}><StudentDashboard /></ProtectedRoute>
} />
```

---

## 17. Quick Reference — All Endpoints

### Auth `PUBLIC`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/signup/send-email-otp` | Signup step 1 — send email OTP |
| POST | `/api/auth/signup/verify-email-otp` | Signup step 2 — verify email OTP |
| POST | `/api/auth/signup/send-mobile-otp` | Signup step 3 — send mobile OTP |
| POST | `/api/auth/signup/verify-mobile-otp` | Signup step 4 — verify, create account, set cookies |
| POST | `/api/auth/login` | Login step 1 — validate credentials, send OTP |
| POST | `/api/auth/login/verify-otp` | Login step 2 — verify OTP, set cookies |
| POST | `/api/auth/token/refresh` | Refresh access token silently |
| POST | `/api/auth/token/rotate` | Rotate both tokens |

### Auth `PROTECTED`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/logout` | Logout current device |
| POST | `/api/auth/token/revoke-all` | Logout all devices |
| GET | `/api/auth/me` | Get current user profile |
| GET | `/api/auth/token/validate` | Debug — inspect token claims |

### Devices `PROTECTED`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/devices` | List active devices |
| DELETE | `/api/devices/{deviceId}` | Revoke a device |
| POST | `/api/devices/check-limit` | Check device limit |
| POST | `/api/devices/disconnect?deviceId=` | Disconnect a device |
| POST | `/api/devices/streaming/start?deviceId=` | Start streaming |
| POST | `/api/devices/streaming/stop?deviceId=` | Stop streaming |

### Public

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/enquiry/submit` | Submit enquiry form |

### Mentor `PROTECTED · MENTOR role`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/mentor/dashboard` | Dashboard stats |
| GET | `/api/mentor/courses` | List all courses |
| POST | `/api/mentor/courses` | Create course |
| GET | `/api/mentor/courses/{courseId}` | Get course by ID |
| GET | `/api/mentor/courses/{courseId}/batches` | All batches for a course |
| GET | `/api/mentor/batches` | List all batches |
| POST | `/api/mentor/batches` | Create batch |
| GET | `/api/mentor/batches/{batchId}` | Get batch by ID |
| PUT | `/api/mentor/batches/{batchId}/status` | Update batch status |
| GET | `/api/mentor/batches/{batchId}/classes` | All classes in a batch |
| GET | `/api/mentor/batches/{batchId}/students` | Students in a batch (with IDs) |
| GET | `/api/mentor/classes` | All classes across all batches |
| POST | `/api/mentor/classes` | Create class — calls Zoom API |
| GET | `/api/mentor/students/search?q=` | Search students by name/email |
| POST | `/api/mentor/students/enroll` | Enroll student in a batch |
| DELETE | `/api/mentor/batches/{batchId}/students/{studentId}` | Unenroll student |

### Student `PROTECTED · STUDENT role`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/student/dashboard` | Dashboard |
| GET | `/api/student/courses` | My enrolled batches |
| GET | `/api/student/schedule` | Upcoming classes |
| GET | `/api/student/batches/{batchId}/classes` | Classes in an enrolled batch |
| GET | `/api/student/classes/{meetingId}/join` | Get join URL for a class |

---

## Key Reminders

- `withCredentials: true` on **every** axios request — never skip this
- Never store tokens in `localStorage` — cookies are managed by the browser
- Call `GET /api/auth/me` on app load to restore login state
- `startUrl` is **only** in mentor responses — backend never sends it to students
- Meeting status `UPCOMING → LIVE → ENDED` updates automatically via Zoom webhooks
- Get `deviceId` from `GET /api/devices` — use it for streaming start/stop
- All datetimes are IST (`Asia/Kolkata`) without timezone suffix — format with `toLocaleString("en-IN", { timeZone: "Asia/Kolkata" })`
- `role` field: `STUDENT` · `MENTOR` · `ADMIN` — use for route guards
- A `401` from `/api/auth/token/refresh` = session fully expired — redirect to login
