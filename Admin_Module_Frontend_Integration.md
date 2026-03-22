# Admin Module — Frontend Integration Guide

**Base URL (dev):** `http://localhost:8080`
**Base URL (prod):** `https://api.firstmilliontrade.com`
**Auth:** HttpOnly cookie — role must be `ADMIN`
**Last updated:** 2026-03-22

> All `/api/admin/**` endpoints return `403 Forbidden` if the logged-in user is not `ADMIN`.

---

## Table of Contents

1. [Admin Module Overview](#1-admin-module-overview)
2. [Dashboard](#2-dashboard)
3. [User Management](#3-user-management)
4. [Platform Overview — Courses, Batches, Classes, Recordings](#4-platform-overview)
5. [Change Password (All Roles)](#5-change-password-all-roles)
6. [Error Handling](#6-error-handling)
7. [Quick Reference — All Endpoints](#7-quick-reference--all-endpoints)

> **Updated 2026-03-22:** Added `DELETE /api/admin/users/{userId}` — permanent user deletion with cascading cleanup.

---

## 1. Admin Module Overview

### What admin can do

| Feature | Description |
|---------|-------------|
| Dashboard | Platform-wide stats — users, courses, batches, classes, recordings + upcoming classes + recent signups |
| Create user | Register an offline student (no OTP — admin sets credentials directly) |
| List users | View all users, filter by role |
| Update role | Promote a student to MENTOR, or demote back |
| Activate / deactivate | Enable or disable a user account |
| Reset password | Set a new password for any user (no current password needed) |
| **Delete user** | **Permanently delete a user and ALL their data (auth, enrollments, OTPs, devices)** |
| View courses | All courses across all mentors |
| View batches | All batches across all courses |
| View classes | All meetings/classes across all batches |
| View recordings | All recordings across the platform |

### Suggested left-nav structure for UI

```
Dashboard
Users
  └─ All Users
  └─ Mentors
Courses
Batches
Classes
Recordings
```

---

## 2. Dashboard

### `GET /api/admin/dashboard` `PROTECTED · ADMIN`

**Request:** No body, no params.

**Response `200`:**
```json
{
  "success": true,
  "message": "Dashboard loaded",
  "data": {
    "totalUsers":      150,
    "totalStudents":   140,
    "totalMentors":    8,
    "totalCourses":    5,
    "totalBatches":    12,
    "totalClasses":    80,
    "totalRecordings": 60,
    "upcomingClasses": [ ],
    "recentUsers":     [ ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `totalUsers` | number | All users (students + mentors + admins) |
| `totalStudents` | number | Users with role `STUDENT` |
| `totalMentors` | number | Users with role `MENTOR` |
| `totalCourses` | number | All courses |
| `totalBatches` | number | All batches |
| `totalClasses` | number | All meetings/classes |
| `totalRecordings` | number | All recordings |
| `upcomingClasses` | array | Next 5 upcoming classes (MeetingResponse with `startUrl`) |
| `recentUsers` | array | Last 5 registered users (UserResponse) |

**MeetingResponse shape (in `upcomingClasses`):**
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
  "startUrl":     "https://zoom.us/s/...",
  "joinUrl":      "https://zoom.us/j/..."
}
```

**UserResponse shape (in `recentUsers`):**
```json
{
  "id":               "uuid",
  "firstName":        "Raj",
  "lastName":         "Kumar",
  "email":            "raj@gmail.com",
  "phoneNumber":      "9876543210",
  "role":             "STUDENT",
  "gender":           "MALE",
  "city":             "Hyderabad",
  "state":            "Telangana",
  "country":          "India",
  "postalCode":       "500001",
  "isActive":         true,
  "isEmailVerified":  true,
  "isMobileVerified": true,
  "lastLoginAt":      "2026-03-21T09:30:00",
  "createdAt":        "2026-03-15T10:00:00",
  "failedLoginAttempts": 0
}
```

**Usage:**
```js
const loadDashboard = async () => {
  const res = await api.get("/api/admin/dashboard");
  if (res.data.success) setDashboard(res.data.data);
};
```

---

## 3. User Management

### `GET /api/admin/users` `PROTECTED · ADMIN`

List all users. Optionally filter by role.

**Query Param (optional):** `?role=STUDENT` or `?role=MENTOR` or `?role=ADMIN`

**Response `200` — array of UserResponse:**
```json
[
  {
    "id":               "uuid",
    "firstName":        "Raj",
    "lastName":         "Kumar",
    "email":            "raj@gmail.com",
    "phoneNumber":      "9876543210",
    "role":             "STUDENT",
    "gender":           "MALE",
    "city":             "Hyderabad",
    "state":            "Telangana",
    "country":          "India",
    "postalCode":       "500001",
    "isActive":         true,
    "isEmailVerified":  true,
    "isMobileVerified": true,
    "lastLoginAt":      "2026-03-21T09:30:00",
    "createdAt":        "2026-03-15T10:00:00",
    "failedLoginAttempts": 0
  }
]
```

**Usage:**
```js
// All users
const res = await api.get("/api/admin/users");

// Students only
const res = await api.get("/api/admin/users?role=STUDENT");

// Mentors only
const res = await api.get("/api/admin/users?role=MENTOR");
```

---

### `POST /api/admin/users` `PROTECTED · ADMIN`

Admin registers an offline student. No OTP — account is created active and verified immediately. Share the credentials with the student manually.

**Request Body:**
```json
{
  "firstName":   "Ravi",
  "lastName":    "Kumar",
  "email":       "ravi@gmail.com",
  "password":    "Ravi@1234",
  "phoneNumber": "+919876543210",
  "role":        "STUDENT",
  "gender":      "MALE",
  "city":        "Hyderabad",
  "state":       "Telangana",
  "country":     "India",
  "postalCode":  "500001"
}
```

**Field Validation:**

| Field | Required | Rules |
|-------|----------|-------|
| `firstName` | Yes | 2–100 characters |
| `lastName` | Yes | 1–100 characters |
| `email` | Yes | Valid email, must not already exist |
| `password` | Yes | Min 8 chars · 1 digit · 1 lowercase · 1 uppercase · 1 special char (`@#$%^&+=!`) · no spaces |
| `phoneNumber` | No | Format: `+919876543210` |
| `role` | No | `STUDENT` / `MENTOR` / `ADMIN` — defaults to `STUDENT` |
| `gender` | No | `MALE` / `FEMALE` / `OTHER` |
| `city`, `state`, `country`, `postalCode` | No | Free text |

**Response `200`:** Full UserResponse object of the created user.

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Email is already registered"` | Duplicate email |
| `"First name is required"` | Missing field |
| `"Password must contain..."` | Weak password |

**Usage:**
```js
const createUser = async (formData) => {
  const res = await api.post("/api/admin/users", {
    firstName:   formData.firstName,
    lastName:    formData.lastName,
    email:       formData.email,
    password:    formData.password,
    phoneNumber: formData.phoneNumber || null,
    role:        formData.role || "STUDENT",
    gender:      formData.gender || null,
    city:        formData.city || null,
    state:       formData.state || null,
    country:     formData.country || null,
    postalCode:  formData.postalCode || null,
  });

  if (res.data.success) {
    toast.success(`User created! Share credentials: ${formData.email} / ${formData.password}`);
    return res.data.data;  // UserResponse
  } else {
    toast.error(res.data.message);
  }
};
```

---

### `GET /api/admin/users/{userId}` `PROTECTED · ADMIN`

**Path Param:** `userId` — UUID from the users list

**Response `200`:** Single UserResponse object.

---

### `PUT /api/admin/users/{userId}/role` `PROTECTED · ADMIN`

Update a user's role — e.g. promote student to mentor or demote back.

**Path Param:** `userId` — UUID

**Request Body:**
```json
{ "role": "MENTOR" }
```

| Value | Effect |
|-------|--------|
| `STUDENT` | User can access `/api/student/**` |
| `MENTOR` | User can access `/api/mentor/**` |
| `ADMIN` | Full admin access |

**Response `200`:** Updated UserResponse.

> After a role change, the user's **existing JWT is NOT updated** — they need to log out and log back in to get a new token with the updated role. Display a message to inform.

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"User not found"` | Invalid userId |
| `"Role is required"` | Missing body |

**Usage:**
```js
const updateRole = async (userId, newRole) => {
  const res = await api.put(`/api/admin/users/${userId}/role`, { role: newRole });

  if (res.data.success) {
    toast.success(res.data.message);  // "Role updated to MENTOR"
    toast.info("User must log out and back in for the change to take effect.");
  } else {
    toast.error(res.data.message);
  }
};
```

---

### `PUT /api/admin/users/{userId}/status` `PROTECTED · ADMIN`

Activate or deactivate a user account.

**Path Param:** `userId` — UUID

**Request Body:**
```json
{ "isActive": false }
```

| Value | Effect |
|-------|--------|
| `true` | Account enabled — user can log in |
| `false` | Account disabled — user cannot log in |

**Response `200`:** Updated UserResponse. Message is `"User activated"` or `"User deactivated"`.

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"User not found"` | Invalid userId |
| `"isActive is required"` | Missing body |

---

### `PUT /api/admin/users/{userId}/reset-password` `PROTECTED · ADMIN`

Admin sets a new password for a user — no current password required. Also clears any account lockouts.

**Path Param:** `userId` — UUID

**Request Body:**
```json
{ "newPassword": "NewPass@123" }
```

| Field | Required | Rules |
|-------|----------|-------|
| `newPassword` | Yes | Same password rules — min 8, digit, upper, lower, special char |

**Response `200`:**
```json
{ "success": true, "message": "Password reset successfully", "data": null }
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"User not found"` | Invalid userId |
| `"Password must contain..."` | Weak new password |

**Usage:**
```js
const resetPassword = async (userId, newPassword) => {
  const res = await api.put(`/api/admin/users/${userId}/reset-password`, { newPassword });

  if (res.data.success) {
    toast.success("Password reset! Share new credentials with the user.");
  } else {
    toast.error(res.data.message);
  }
};
```

---

### `DELETE /api/admin/users/{userId}` `PROTECTED · ADMIN`

Permanently deletes a user and **all associated data** — devices, sessions, tokens, enrollments, and OTPs.

**Path Param:** `userId` — UUID

**No request body.**

**What gets deleted (in order):**
1. Refresh tokens
2. Login sessions
3. Devices
4. Batch enrollments (student's course history)
5. OTPs
6. Email verification tokens
7. The user record itself

> **MENTOR guard:** If the user is a MENTOR with existing courses, deletion is **blocked**. Admin must delete their courses (and batches) first, then delete the user.

**Response `200`:**
```json
{ "success": true, "message": "User deleted successfully", "data": null }
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"User not found"` | Invalid userId |
| `"Cannot delete mentor with existing courses. Delete or reassign their courses first."` | Mentor has courses — block deletion |

**Usage:**
```js
const deleteUser = async (userId, userName) => {
  const confirmed = window.confirm(
    `Permanently delete ${userName}? This cannot be undone.`
  );
  if (!confirmed) return;

  const res = await api.delete(`/api/admin/users/${userId}`);

  if (res.data.success) {
    toast.success("User deleted permanently.");
    setUsers(prev => prev.filter(u => u.id !== userId));
  } else {
    toast.error(res.data.message);
    // If mentor with courses → guide admin: "Delete their courses first"
  }
};
```

> Always show a confirmation dialog before calling this — deletion is irreversible. For a less destructive action (suspend without deleting), use `PUT /api/admin/users/{userId}/status` with `{ "isActive": false }` instead.

---

## 4. Platform Overview

These are read-only views of the entire platform. Ordered by creation date, newest first.

### `GET /api/admin/courses` `PROTECTED · ADMIN`

All courses across all mentors.

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

---

### `GET /api/admin/batches` `PROTECTED · ADMIN`

All batches across all courses.

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

---

### `GET /api/admin/classes` `PROTECTED · ADMIN`

All classes (meetings) across all batches. Includes `startUrl`.

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
    "startUrl":     "https://zoom.us/s/...",
    "joinUrl":      "https://zoom.us/j/..."
  }
]
```

---

### `GET /api/admin/recordings` `PROTECTED · ADMIN`

All recordings across all batches.

**Response `200` — array of RecordingResponse:**
```json
[
  {
    "id":          "uuid",
    "title":       "Day 1 - Market Intro",
    "batchId":     "uuid",
    "batchName":   "Batch 1 - March 2026",
    "status":      "AVAILABLE",
    "durationMins": 90,
    "createdAt":   "2026-03-21T12:00:00"
  }
]
```

**RecordingStatus values:**

| Status | Meaning |
|--------|---------|
| `PROCESSING` | Received from Zoom, upload to Cloudflare in progress |
| `AVAILABLE` | Ready to watch |
| `FAILED` | Upload or processing failed |
| `EXPIRED` | Recording link expired |

---

## 5. Change Password (All Roles)

Available to **all authenticated users** — STUDENT, MENTOR, and ADMIN. User must know their current password.

### `PUT /api/auth/change-password` `PROTECTED · ALL ROLES`

**Request Body:**
```json
{
  "currentPassword": "OldPass@123",
  "newPassword":     "NewPass@456"
}
```

| Field | Required | Rules |
|-------|----------|-------|
| `currentPassword` | Yes | Must match the current stored password |
| `newPassword` | Yes | Min 8 chars · 1 digit · 1 lowercase · 1 uppercase · 1 special char · no spaces · must differ from current |

**Response `200`:**
```json
{ "success": true, "message": "Password changed successfully", "data": null }
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Current password is incorrect"` | Wrong current password entered |
| `"New password must be different from current password"` | Same as old |
| `"Password must contain..."` | Weak new password |

**Usage:**
```js
const changePassword = async (currentPassword, newPassword) => {
  const res = await api.put("/api/auth/change-password", {
    currentPassword,
    newPassword,
  });

  if (res.data.success) {
    toast.success("Password changed successfully!");
    navigate("/dashboard");
  } else {
    toast.error(res.data.message);
  }
};
```

> After a successful password change, the user's existing JWT and cookies remain valid until they naturally expire. The user does **not** get automatically logged out.

---

## 6. Error Handling

### HTTP Status Codes

| Status | Meaning | Action |
|--------|---------|--------|
| `200` | Success | Use `response.data.data` |
| `400` | Validation / business error | Show `response.data.message` |
| `401` | Not authenticated | Redirect to `/login` |
| `403` | Not ADMIN role | Redirect to `/unauthorized` |
| `500` | Server error | Show generic message |

### Common Error Messages

| Message | Endpoint | Cause |
|---------|----------|-------|
| `"Email is already registered"` | Create user | Duplicate email |
| `"User not found"` | Any user endpoint | Invalid userId |
| `"Role is required"` | Update role | Missing body |
| `"isActive is required"` | Update status | Missing body |
| `"Current password is incorrect"` | Change password | Wrong current password |
| `"New password must be different from current password"` | Change password | Same password |
| `"Password must contain..."` | Create user / reset / change password | Weak password |

---

## 7. Quick Reference — All Endpoints

### Admin (`/api/admin/**`) — ADMIN role required

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/dashboard` | Platform stats + upcoming classes + recent users |
| GET | `/api/admin/users` | List all users (optional `?role=`) |
| POST | `/api/admin/users` | Create user — admin registers offline student |
| GET | `/api/admin/users/{userId}` | Get user by ID |
| PUT | `/api/admin/users/{userId}/role` | Update user role |
| PUT | `/api/admin/users/{userId}/status` | Activate / deactivate user |
| PUT | `/api/admin/users/{userId}/reset-password` | Admin resets user's password |
| DELETE | `/api/admin/users/{userId}` | **Permanently delete user + all data** |
| GET | `/api/admin/courses` | All courses |
| GET | `/api/admin/batches` | All batches |
| GET | `/api/admin/classes` | All classes |
| GET | `/api/admin/recordings` | All recordings |

### Auth (all roles) — requires login

| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/auth/change-password` | Change own password (requires current password) |

---

## Key Notes for UI

- **No public signup** — keep the signup button hidden on the frontend. Admin creates all accounts manually via `POST /api/admin/users` and shares credentials offline.
- **Role change requires re-login** — after admin updates a user's role, the user must log out and back in to get a JWT with the new role. Show an info message.
- **Password reset vs change password:**
  - `PUT /api/admin/users/{userId}/reset-password` → admin resets for any user, no current password needed
  - `PUT /api/auth/change-password` → user changes their own, must know current password
- **User activation** — deactivated users (`isActive: false`) cannot log in. Use to suspend an account without deleting data.
- **Delete vs deactivate** — prefer deactivating (`isActive: false`) over deleting when in doubt. Deletion is permanent and irreversible. Use delete only to GDPR-remove a user or clean up test accounts.
- **Mentor deletion guard** — `DELETE /api/admin/users/{userId}` returns an error if the user is a MENTOR with existing courses. The UI should guide the admin: "Delete their courses first, then delete the user."
- **Recordings are Phase 2** — `GET /api/admin/recordings` returns data but play URLs are not yet functional (Cloudflare Stream integration pending).
