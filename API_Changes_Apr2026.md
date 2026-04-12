# API Changes — April 2026

**Date:** 2026-04-11  
**Shared with:** Frontend team  
**Reason:** SMS service removed; authentication improvements

---

## Change 1 — SMS OTP Removed (Email OTP Only)

Twilio SMS has been removed. All OTP flows now use **email only**.

---

### 1.1 Login Flow — No Change on Frontend

Login still works the same from frontend perspective.

| Step | Endpoint | Before | After |
|------|----------|--------|-------|
| 1 | `POST /api/auth/login` | OTP sent to email + mobile (if phone exists) | OTP sent to email only |
| 2 | `POST /api/auth/login/verify-otp` | Accepted email OTP or mobile OTP | Accepts email OTP only |

**Frontend action:** None required.

---

### 1.2 Signup Flow — Now 2-Step Instead of 4

The mobile OTP steps have been removed. Signup is now:

**Old flow (4 steps):**
```
POST /api/auth/signup/send-email-otp       → send email OTP
POST /api/auth/signup/verify-email-otp     → verify email OTP
POST /api/auth/signup/send-mobile-otp      → send SMS OTP       ← REMOVED
POST /api/auth/signup/verify-mobile-otp    → verify SMS OTP + register
```

**New flow (3 steps — step 3 is now the registration step):**
```
POST /api/auth/signup/send-email-otp       → send email OTP      (unchanged)
POST /api/auth/signup/verify-email-otp     → verify email OTP    (unchanged)
POST /api/auth/signup/verify-mobile-otp    → complete registration (no mobile OTP needed)
```

> `POST /api/auth/signup/send-mobile-otp` endpoint has been **removed**. Calling it will return 404.

**`POST /api/auth/signup/verify-mobile-otp` — what changed:**

- The `otp` query param is still accepted in the request but **ignored** on the backend
- Mobile OTP is no longer verified
- User is registered immediately after this call
- `isMobileVerified` will be `false` for new users (was `true` before)

**Frontend action:**
- Remove the step that calls `send-mobile-otp`
- After `verify-email-otp` succeeds, call `verify-mobile-otp` directly with the full signup body
- Remove any UI screen that asks for mobile OTP during signup

---

### 1.3 Admin — Create User Flow

Admin creates users via OTP-verified flow (`/api/admin/users/send-otp` + `/api/admin/users/verify-and-create`).

| Step | Endpoint | Before | After |
|------|----------|--------|-------|
| 1 | `POST /api/admin/users/send-otp` | Sent email OTP + SMS OTP if phone provided | Sends email OTP only |
| 2 | `POST /api/admin/users/verify-and-create` | Required `mobileOtp` if phone was provided | `mobileOtp` field is ignored |

**Frontend action:**
- Remove the `mobileOtp` field from the verify-and-create form/request (or leave it — it will be silently ignored)
- Remove any UI that asks admin to enter the mobile OTP

---

## Change 2 — OTP Verification Bug Fixes (Backend Only)

> These are backend-only fixes. No frontend API changes. Listed here so the frontend team understands the new OTP behaviour.

Four bugs were found and fixed in the OTP verification logic:

### Bug 1 — Correct OTP rejected on 3rd attempt (fixed)

**Before:** Attempt count was incremented **before** checking if the OTP was correct. With `maxAttempts=3`, the 3rd attempt was always locked — even if the OTP entered was right. Users only had 2 effective tries.

**After:** Attempts are incremented only on a **wrong** entry. A correct OTP always succeeds as long as the account is not already locked.

### Bug 2 — Re-locked immediately after lockout expired (fixed)

**Before:** After the 10-minute lockout expired, the OTP record still had `attempts >= maxAttempts`. The next attempt would immediately re-lock again, making the lockout permanent until a new OTP was requested.

**After:** Same fix as Bug 1 — since attempts only increment on failure, entering the correct OTP after a lockout expires now works correctly.

> **Note:** If the OTP is locked, the user must **request a new OTP**. The locked record itself cannot be unlocked.

### Bug 3 — Signup cooldown blocked login OTP (fixed)

**Before:** The 60-second OTP cooldown counted all OTPs for the same email, regardless of type. If a user completed signup (email verification OTP) and then tried to login within 60 seconds, the login OTP generation would be blocked with "Please wait 60 seconds".

**After:** Cooldown is now tracked **per OTP type** (EMAIL_VERIFICATION and LOGIN are independent). Signup and login no longer interfere with each other's cooldowns.

### Bug 4 — OTP with trailing space failed (fixed)

**Before:** `otp.getOtpCode().equals(otpCode)` — if the user copy-pasted the OTP with a trailing space, verification would fail silently.

**After:** Both sides are trimmed before comparison.

---

## Change 3 — Authentication Failure Logs (Backend Only)

> This is a backend-only change. No frontend action required. Listed here for awareness.

**Problem observed:** Periodic `Authentication failed` errors appearing in server logs even when no user was active. This is caused by:
1. **SSR / Server Components** making API calls on the Next.js Node.js server — browser cookies are not forwarded to the backend in SSR context
2. **SWR / React Query polling** with `refreshInterval` or `refetchInterval` — polls protected endpoints in the background
3. **Internet bots** probing the API

**Backend fix:** Changed the log from `ERROR` to `WARN` and now logs the method + URI so the source is visible:
```
WARN - 🔐 Authentication failed: GET /api/auth/me
```

**Frontend action (recommended):**

If any API calls are made from Next.js server-side context (SSR, Server Components, `getServerSideProps`), cookies must be forwarded manually:

```js
// getServerSideProps — forward browser cookies to backend
export async function getServerSideProps({ req }) {
  const res = await fetch('https://api.firstmilliontrade.com/api/auth/me', {
    headers: { cookie: req.headers.cookie ?? '' }
  })
}
```

For client-side fetch (SWR, React Query, plain fetch), ensure `credentials: 'include'` is set:

```js
// SWR
const { data } = useSWR('/api/auth/me', (url) =>
  fetch(url, { credentials: 'include' }).then(r => r.json())
)

// React Query
useQuery({
  queryKey: ['me'],
  queryFn: () => fetch('https://api.firstmilliontrade.com/api/auth/me', {
    credentials: 'include'
  }).then(r => r.json())
})

// Axios
axios.get('/api/auth/me', { withCredentials: true })
```

> Without `credentials: 'include'`, cookies are not sent and every request returns 401.

---

## Summary of Frontend Actions (OTP behaviour to be aware of)

The OTP error messages from the backend are now more specific. The frontend should handle these error response messages:

| Scenario | Error message from backend |
|----------|--------------------------|
| Wrong OTP entered | `"Invalid OTP"` |
| Correct OTP after `maxAttempts` wrong tries | `"Too many failed attempts. Please request a new OTP after 10 minutes"` |
| OTP expired (5 min) | `"Invalid or expired OTP"` |
| OTP requested again within 60 seconds | `"Please wait 60 seconds before requesting new OTP"` |

---

---

## Change 4 — Any Mentor Can Take Any Class (DB + API change)

**Background:** Previously, a course was owned by one mentor, and only that mentor could create meetings (classes) for batches under that course. Now any mentor can conduct a class for any batch.

---

### 4.1 DB Change

A new column `mentor_id` has been added to the `meetings` table (auto-applied by Hibernate on next restart). This tracks **who is conducting** the meeting — independent of which mentor owns the course.

```
meetings
├── id
├── batch_id      → still links meeting to a batch
├── mentor_id     ← NEW — the mentor conducting this specific class
├── topic
├── zoom_meeting_id
├── start_url
├── join_url
├── status
├── scheduled_at
├── duration_mins
```

---

### 4.2 Mentor API Changes

#### `GET /api/mentor/courses` — **Now returns ALL courses (not just assigned ones)**

Before: returned only courses assigned to the logged-in mentor.  
After: returns all active courses in the system.

**Frontend action:** No request change. The response shape is identical — update UI labels from "My Courses" → "All Courses" if needed.

---

#### `GET /api/mentor/batches` — **Now returns ALL batches**

Before: returned only batches under the mentor's own courses.  
After: returns all batches in the system (sorted by creation date).

**Frontend action:** No request change. Update UI label from "My Batches" → "All Batches" if needed.

---

#### `GET /api/mentor/courses/{courseId}/batches` — **No ownership check**

Before: threw 403 if the course didn't belong to the requesting mentor.  
After: any mentor can get batches for any course.

**Frontend action:** None.

---

#### `POST /api/mentor/classes` — **Any mentor can create a class for any batch**

Before: only the course's assigned mentor could create a meeting for batches under that course.  
After: any mentor can create a meeting for any batch. The conducting mentor is set from the JWT (no change to request body).

**Request body (unchanged):**
```json
{
  "batchId": "uuid",
  "topic": "string",
  "durationMins": 120,
  "scheduledAt": "2026-04-15T10:00:00"
}
```

**Response body — NEW FIELDS added:**
```json
{
  "id": "uuid",
  "topic": "string",
  "batchId": "uuid",
  "batchName": "string",
  "mentorId": "uuid",       ← NEW — who is conducting
  "mentorName": "string",   ← NEW — display name of conducting mentor
  "status": "UPCOMING",
  "scheduledAt": "...",
  "durationMins": 120,
  "startUrl": "https://...",  ← mentor/admin only
  "joinUrl": "https://...",
  "createdAt": "..."
}
```

**Frontend action:** Update the class creation form to use this exact flow:

1. Load all courses: `GET /api/mentor/courses` → populate course dropdown
2. When mentor selects a course, load only **that course's batches**: `GET /api/mentor/courses/{courseId}/batches` → populate batch dropdown
3. Mentor fills in topic, date, duration and submits: `POST /api/mentor/classes`

> The course → batch relationship is still enforced. `GET /api/mentor/courses/{courseId}/batches` returns **only batches belonging to the selected course**, not all batches. This ensures the mentor always picks a valid batch for the chosen course.

---

#### `GET /api/mentor/classes` — Returns meetings conducted by this mentor

No request change. Now uses `meeting.mentor_id = currentMentor` instead of `meeting.batch.course.mentor_id`. This means a mentor sees classes **they personally conducted**, not all classes for their courses.

**Frontend action:** None — same shape, correct data.

---

#### `GET /api/mentor/batches/{batchId}/classes` — No ownership check

Before: threw 403 if batch didn't belong to the mentor.  
After: any mentor can see meetings for any batch.

**Frontend action:** None.

---

### 4.3 Admin API Changes

#### `POST /api/admin/meetings` — **`mentorId` is now required**

Before: automatically used the course's assigned mentor as the meeting conductor.  
After: admin must explicitly pass which mentor is conducting the class.

**Request body — `mentorId` is now required:**
```json
{
  "batchId": "uuid",
  "topic": "string",
  "mentorId": "uuid",    ← NOW REQUIRED — which mentor is conducting
  "durationMins": 120,
  "scheduledAt": "2026-04-15T10:00:00"
}
```

**Response:** Same shape as mentor response (includes `mentorId`, `mentorName`, `startUrl`).

**Frontend action:** Add mentor selector to the admin "Create Meeting" form. Use `GET /api/admin/users?role=MENTOR` to populate the dropdown.

---

### 4.4 Student API — `mentorName` added to meeting responses

All meeting responses visible to students now include `mentorName` (the display name of the conducting mentor). `mentorId` is **not** included in student responses for security.

**Frontend action:** Optionally display "Conducted by: [mentorName]" on the student's class/meeting cards.

---

## Change 5 — Class Lifecycle Management (Status, Conflicts, Delete, Reschedule)

---

### 5.1 Class Status Flow

```
UPCOMING → LIVE → ENDED
UPCOMING → CANCELLED
```

| Status | Meaning | How it's set |
|--------|---------|-------------|
| `UPCOMING` | Scheduled, not started | Set on creation |
| `LIVE` | Class is in progress | Mentor calls `PUT /classes/{id}/start` |
| `ENDED` | Class finished | Mentor calls `PUT /classes/{id}/end` OR auto-ended by server |
| `CANCELLED` | Class was cancelled | Mentor calls `DELETE /classes/{id}` |

**Auto-end:** The server checks every minute and automatically sets `ENDED` for any meeting where `scheduledAt + durationMins` has passed and the meeting is still `UPCOMING` or `LIVE`. No manual action needed.

---

### 5.2 Time Conflict Validation

When creating or rescheduling a class, the backend checks that no other class for the same batch overlaps in time. If a conflict is detected, a `400` error is returned with the conflicting class details.

```json
{
  "success": false,
  "message": "Time conflict: this batch already has a class scheduled from 2026-04-15T10:00 to 2026-04-15T12:00 (\"Intro to Trading\")"
}
```

**Frontend action:** Show this error message to the mentor when it occurs.

---

### 5.3 New Mentor Endpoints

#### `PUT /api/mentor/classes/{classId}/start`
Mark a class as **LIVE** when the mentor opens the Zoom start link.

- Only the mentor who created the class can call this
- Only works on `UPCOMING` classes
- Sets `status = LIVE` and records `startedAt`

**No request body required.**

**Response:** Full meeting object with `status: "LIVE"`

---

#### `PUT /api/mentor/classes/{classId}/end`
Mark a class as **ENDED** when the session finishes.

- Only the mentor who created the class can call this
- Only works on `LIVE` classes

**No request body required.**

**Response:** Full meeting object with `status: "ENDED"`

---

#### `DELETE /api/mentor/classes/{classId}`
Cancel an **UPCOMING** class.

- Cannot cancel a LIVE or ENDED class
- Sets `status = CANCELLED` (record is kept, not deleted from DB)

**Response:**
```json
{ "success": true, "message": "Class cancelled" }
```

---

#### `PUT /api/mentor/classes/{classId}/reschedule`
Reschedule an **UPCOMING** class.

- Only works on `UPCOMING` classes
- Re-validates for time conflicts at the new time

**Request body:**
```json
{
  "scheduledAt": "2026-04-20T10:00:00",
  "durationMins": 90,
  "topic": "New Topic"
}
```

> `durationMins` and `topic` are optional — existing values are kept if omitted.

**Response:** Full meeting object with updated fields.

---

### 5.4 New Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `PUT` | `/api/admin/meetings/{id}/start` | Mark as LIVE (admin override) |
| `PUT` | `/api/admin/meetings/{id}/end` | Mark as ENDED (admin override) |
| `DELETE` | `/api/admin/meetings/{id}` | Cancel meeting (UPCOMING only) |
| `PUT` | `/api/admin/meetings/{id}/reschedule` | Reschedule (same body as mentor) |

---

### 5.5 Student View — Status-Driven UI

Students see `status` on all meetings. Recommended UI behaviour:

| Status | UI |
|--------|----|
| `UPCOMING` | Show scheduled time + countdown |
| `LIVE` | Highlight card, show "Join Now" button prominently |
| `ENDED` | Greyed out, show recording link if available |
| `CANCELLED` | Show "Cancelled" badge |

---

### 5.6 Recommended Frontend Flow (Mentor Create Class)

1. Pick course → `GET /api/mentor/courses`
2. Pick batch for that course → `GET /api/mentor/courses/{courseId}/batches`
3. Fill in topic, date/time, duration → `POST /api/mentor/classes`
   - On 400 "Time conflict" → show the conflict message and let mentor pick a different time
4. When class starts → `PUT /api/mentor/classes/{id}/start`
5. When class ends → `PUT /api/mentor/classes/{id}/end`

---

## Change 6 — Class Listing: Status Filter + LIVE Bug Fix

---

### 6.1 Bug Fix — LIVE Classes Were Missing from Student Schedule

**The problem:** `GET /api/student/schedule` and the dashboard "upcoming classes" only returned `UPCOMING` meetings. If a class had already gone `LIVE` (mentor clicked Start), it disappeared from the student's view right when the student needed to join.

**The fix:** The query now returns both `UPCOMING` and `LIVE` meetings. Students will see an active class even after it has started.

**Frontend action:** The `GET /api/student/schedule` response already contained `status` — no payload change. Just make sure your UI handles `LIVE` (see status table in section 5.5).

---

### 6.2 New — Optional `?status` Filter on Class Listing Endpoints

All class listing endpoints now accept an optional `?status` query parameter. Pass one or more values (comma-separated) to filter by status. Omit it to get all classes.

#### Affected endpoints

| Role | Endpoint |
|------|----------|
| Mentor | `GET /api/mentor/classes` |
| Mentor | `GET /api/mentor/batches/{batchId}/classes` |
| Student | `GET /api/student/batches/{batchId}/classes` |

#### Usage

```
GET /api/mentor/classes                              → all classes (no filter)
GET /api/mentor/classes?status=UPCOMING,LIVE         → active + scheduled only
GET /api/mentor/classes?status=ENDED                 → past classes / history
GET /api/mentor/classes?status=CANCELLED             → cancelled classes only

GET /api/student/batches/{id}/classes?status=UPCOMING,LIVE   → what student sees as "active"
GET /api/student/batches/{id}/classes?status=ENDED           → recordings / past classes
```

#### Recommended UI tabs

| Tab | Query param |
|-----|-------------|
| Upcoming / Active | `?status=UPCOMING,LIVE` |
| Past | `?status=ENDED` |
| All | _(no param)_ |
| Cancelled | `?status=CANCELLED` |

> `CANCELLED` classes are kept in the DB. They appear when no filter is applied or when `?status=CANCELLED` is explicitly requested. You can choose to hide them in the UI.

**Frontend action:** Pass `?status=UPCOMING,LIVE` for the active schedule view. Pass `?status=ENDED` for a recordings/history view. No param change needed for Swagger — all existing calls still work (no filter = all results).

---

## Change 7 — Single Active Session Per User

---

### 7.1 What Changed

Previously a user could be logged in on up to **2 devices** simultaneously. This is now reduced to **1 active device** at a time. If a user logs in from a new device (or a new browser), their existing session is **immediately terminated** — even without waiting for the token to expire.

Termination is real-time: the old device's next API request will receive a `401 Session expired. Please log in again.` response.

---

### 7.2 Login Response — New Fields

The `POST /api/auth/login/verify-otp` response now includes two additional fields:

```json
{
  "userId": "...",
  "email": "...",
  "role": "STUDENT",
  "previousSessionTerminated": true,
  "previousDeviceName": "Chrome on Windows 10"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `previousSessionTerminated` | `boolean` | `true` if a session on another device was ended by this login |
| `previousDeviceName` | `string` | Name of the kicked device — only present when `previousSessionTerminated` is `true` |

`previousSessionTerminated` is always present in the response. `previousDeviceName` is omitted when no session was terminated.

**When is `previousSessionTerminated: false`?**
- First login (no existing session)
- Re-login from the same device/browser (session is just refreshed, not kicked)

---

### 7.3 Recommended Frontend Behaviour

Show a toast/banner when `previousSessionTerminated === true`:

> "Your previous session on **Chrome on Windows 10** was ended."

This reassures the user that they haven't been hacked — it was their own previous login that was terminated.

---

### 7.4 What Happens to the Kicked Device

- The kicked device's session is invalidated **immediately** in the database
- The next API call from that device returns `401` with:
  ```json
  { "success": false, "message": "Session expired. Please log in again." }
  ```
- Frontend on the old device should catch this `401` and redirect to the login page

---

## Change 8 — Token & Session Security Fixes (Internal — No Frontend Contract Change)

These are backend-only fixes. No request/response payloads changed. No frontend action required.

---

### 8.1 Session check is now mandatory on every request

**Before:** If a JWT was missing the `sessionId` claim (old token format), the session revocation check was silently skipped — the token would authenticate as long as it had a valid signature and hadn't expired, even if the user was force-logged-out.

**After:** Any token without a `sessionId` claim is rejected with `401 Session expired. Please log in again.` All tokens issued by the current backend always contain `sessionId`, so legitimate users are unaffected.

---

### 8.2 `/token/refresh` now rejects revoked sessions immediately

**Before:** A kicked device could still call `/api/auth/token/refresh`, receive a fresh access token, and then fail on every subsequent request with `401` — a confusing loop.

**After:** Both `/token/refresh` and `/token/rotate` now check that the session is still active before issuing anything. A kicked device receives `401 Session has been terminated. Please log in again.` at the refresh step itself.

**Frontend:** Your existing `401` redirect-to-login handler already covers this. No change needed.

---

### 8.3 Session lifetime corrected to 14 days (matches refresh token)

**Before:** `UserSession.expiresAt` was set to `now + 6 hours` (access token lifetime). The session record showed expiry after 6h even though the user could still be logged in via a valid refresh token.

**After:** Set to `now + 14 days` (refresh token lifetime). Session and refresh token now expire together.

---

### 8.4 Token rotation now extends session expiry (sliding window)

**Before:** `/api/auth/token/rotate` issued a new refresh token with a fresh 14-day expiry but never updated `UserSession.expiresAt`. Session and refresh token were out of sync.

**After:** Rotation also extends `UserSession.expiresAt` by 14 days, keeping them in sync.

---

### 8.5 Removed legacy token generation code

`JwtService.generateToken()` was dead code that produced tokens without `sessionId`, `userId`, or `role` claims — bypassing all session security. Removed. Token generation is now exclusively `TokenService.generateAccessToken()`.

---

## Summary of Frontend Actions

| Area | Action Required |
|------|----------------|
| Login | None |
| Signup | Remove `send-mobile-otp` step; call `verify-mobile-otp` directly after email OTP verified |
| Admin create user | Remove `mobileOtp` field from verify-and-create request |
| Admin create meeting | Add `mentorId` field (required) — use mentor dropdown populated from `GET /api/admin/users?role=MENTOR` |
| Mentor create class | Step 1: show ALL courses. Step 2: on course select, load `GET /courses/{courseId}/batches` — batches for that course only. Step 3: submit `POST /classes` |
| Mentor course list | Update UI label if needed — now shows all courses |
| Mentor batch list | Update UI label if needed — now shows all batches |
| Student meeting card | Show `mentorName`; drive UI from `status` (`LIVE` → "Join Now", `UPCOMING` → countdown, `ENDED` → greyed, `CANCELLED` → badge) |
| Mentor create class | Step 1: all courses. Step 2: batches for selected course. Step 3: submit. Handle "Time conflict" 400 error |
| Mentor class start | Call `PUT /api/mentor/classes/{id}/start` when opening Zoom link |
| Mentor class end | Call `PUT /api/mentor/classes/{id}/end` when session finishes |
| Mentor cancel class | `DELETE /api/mentor/classes/{id}` (UPCOMING only) |
| Mentor reschedule | `PUT /api/mentor/classes/{id}/reschedule` with new `scheduledAt` |
| Admin meetings | New start/end/cancel/reschedule endpoints under `/api/admin/meetings/{id}/...` |
| Student schedule | Bug fixed — LIVE classes now appear in `GET /api/student/schedule` |
| Class list tabs | Use `?status=UPCOMING,LIVE` for active view, `?status=ENDED` for history |
| Login response | Read `previousSessionTerminated` — show toast "Your previous session on [device] was ended" if `true` |
| 401 handling | Catch `401` on any request → redirect to login (user was kicked by a new login elsewhere) |
| API calls (SSR) | Forward cookies via `req.headers.cookie` in server-side fetches |
| API calls (client) | Ensure `credentials: 'include'` / `withCredentials: true` on all fetch/axios calls |
