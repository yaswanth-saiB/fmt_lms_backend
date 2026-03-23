# Admin — Content Management & Verified Registration Guide

**Base URL (dev):** `http://localhost:8080`
**Base URL (prod):** `https://api.firstmilliontrade.com`
**Auth:** HttpOnly cookie — role must be `ADMIN`
**Last updated:** 2026-03-23

> All `/api/admin/**` endpoints return `403 Forbidden` if the logged-in user is not `ADMIN`.

---

## Table of Contents

1. [What's New](#1-whats-new)
2. [OTP-Verified User Registration (2-step)](#2-otp-verified-user-registration-2-step)
3. [Course Management](#3-course-management)
4. [Batch Management](#4-batch-management)
5. [Student Enrollment](#5-student-enrollment)
6. [Meeting Management](#6-meeting-management)
7. [Bug Fixes — What Changed in Existing Flows](#7-bug-fixes--what-changed-in-existing-flows)
8. [Quick Reference — All New Endpoints](#8-quick-reference--all-new-endpoints)

---

## 1. What's New

### Admin now has full content management power

| Feature | Endpoint |
|---------|----------|
| Create course (for any mentor) | `POST /api/admin/courses` |
| Update course | `PUT /api/admin/courses/{courseId}` |
| Activate / deactivate course | `PUT /api/admin/courses/{courseId}/toggle-active` |
| Create batch | `POST /api/admin/batches` |
| Update batch status | `PUT /api/admin/batches/{batchId}/status` |
| Search students | `GET /api/admin/students/search?q=` |
| Get students in a batch | `GET /api/admin/batches/{batchId}/students` |
| Enroll student | `POST /api/admin/batches/{batchId}/enroll` |
| Unenroll student | `DELETE /api/admin/batches/{batchId}/students/{studentId}` |
| Create Zoom meeting | `POST /api/admin/meetings` |
| Get meetings for batch | `GET /api/admin/batches/{batchId}/meetings` |
| Send OTP for new user registration | `POST /api/admin/users/send-otp` |
| Verify OTP + create verified user | `POST /api/admin/users/verify-and-create` |

### Key design principle — Admin bypasses mentor ownership
Mentor endpoints are locked to the logged-in mentor's own content. Admin endpoints have no such restriction — admin can manage any mentor's courses, batches, and meetings. The backend automatically derives the correct mentor context from the course/batch data.

---

## 2. OTP-Verified User Registration (2-step)

The old `POST /api/admin/users` still exists for quick creation (no verification). This new 2-step flow is for **proper verified registration** where the user is present with the admin and verifies their own email and mobile.

### Flow

```
Step 1:  Admin enters user's details + clicks "Send OTP"
         → POST /api/admin/users/send-otp
         → OTP sent to email (and mobile if provided)

User verifies OTPs on their phone/email (they're sitting with admin)

Step 2:  Admin submits full form + both OTPs
         → POST /api/admin/users/verify-and-create
         → Both OTPs verified
         → Account created, active, verified
```

---

### `POST /api/admin/users/send-otp` `PROTECTED · ADMIN`

**Step 1** — Send OTP to the user's email and mobile.

**Request Body:**
```json
{
  "email":       "ravi@gmail.com",
  "phoneNumber": "+919876543210"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `email` | Yes | Must not already be registered |
| `phoneNumber` | No | If provided, OTP is also sent via SMS |

**Response `200`:**
```json
{
  "success": true,
  "message": "OTP sent to email and mobile. Both must be verified.",
  "data": null
}
```
If no phone: `"message": "OTP sent to email."`

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Email is already registered"` | User already exists |

---

### `POST /api/admin/users/verify-and-create` `PROTECTED · ADMIN`

**Step 2** — Verify OTPs and create the account.

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
  "postalCode":  "500001",
  "emailOtp":    "847291",
  "mobileOtp":   "512837"
}
```

| Field | Required | Rules |
|-------|----------|-------|
| `firstName` | Yes | Text |
| `lastName` | Yes | Text |
| `email` | Yes | Same email used in step 1 |
| `password` | Yes | Min 8 chars · digit · upper · lower · special char (`@#$%^&+=!`) · no spaces |
| `phoneNumber` | No | Same phone used in step 1 (if any) |
| `role` | No | `STUDENT` / `MENTOR` / `ADMIN` — defaults to `STUDENT` |
| `gender` | No | `MALE` / `FEMALE` / `OTHER` |
| Address fields | No | `city`, `state`, `country`, `postalCode` |
| `emailOtp` | Yes | OTP received in email |
| `mobileOtp` | Required if phoneNumber was provided | OTP received via SMS |

**Response `200`:** Full `UserResponse` of the created user.

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Invalid or expired email OTP"` | Wrong or expired email OTP |
| `"Invalid or expired mobile OTP"` | Wrong or expired mobile OTP |
| `"Mobile OTP is required when phone number is provided"` | `mobileOtp` missing |
| `"Email is already registered"` | Race condition — email taken between steps |

**OTP expiry:** 10 minutes (longer than login OTP to give time for admin registration flow)

**Usage:**
```js
// Step 1 — Send OTP
const sendOtp = async (email, phoneNumber) => {
  const res = await api.post("/api/admin/users/send-otp", {
    email,
    phoneNumber: phoneNumber || null
  });
  if (res.data.success) {
    toast.success(res.data.message);
    setStep(2); // show OTP input fields
  } else {
    toast.error(res.data.message);
  }
};

// Step 2 — Verify + create
const verifyAndCreate = async (formData) => {
  const res = await api.post("/api/admin/users/verify-and-create", {
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
    emailOtp:    formData.emailOtp,
    mobileOtp:   formData.mobileOtp || null,
  });

  if (res.data.success) {
    toast.success("User registered and verified!");
    navigate("/admin/users");
  } else {
    toast.error(res.data.message);
  }
};
```

> The old `POST /api/admin/users` (no OTP) still works — use it for quick test user creation only.

---

## 3. Course Management

### `POST /api/admin/courses` `PROTECTED · ADMIN`

Create a course and assign it to a specific mentor.

**Request Body:**
```json
{
  "title":       "Trading Fundamentals",
  "description": "Learn stock market basics",
  "price":       4999.00,
  "mentorId":    "uuid-of-mentor"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `title` | Yes | Course title |
| `description` | No | Course description |
| `price` | No | Decimal — e.g. `4999.00` |
| `mentorId` | Yes | UUID of the user with role `MENTOR` |

**Response `200`:** `CourseResponse`
```json
{
  "id":          "uuid",
  "title":       "Trading Fundamentals",
  "description": "Learn stock market basics",
  "price":       4999.00,
  "isActive":    true,
  "mentorName":  "Yash Reddy",
  "createdAt":   "2026-03-23T10:00:00"
}
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Mentor not found"` | Invalid mentorId |
| `"User is not a mentor"` | mentorId belongs to a STUDENT or ADMIN |

---

### `PUT /api/admin/courses/{courseId}` `PROTECTED · ADMIN`

Update course title, description, or price. Only sends the fields you want to change.

**Request Body (all optional):**
```json
{
  "title":       "Advanced Trading",
  "description": "Updated description",
  "price":       5999.00
}
```

**Response `200`:** Updated `CourseResponse`.

---

### `PUT /api/admin/courses/{courseId}/toggle-active` `PROTECTED · ADMIN`

Activate or deactivate a course. Deactivated courses are hidden from students.

**Query Param:** `?isActive=true` or `?isActive=false`

**Response `200`:**
```json
{ "success": true, "message": "Course deactivated", "data": null }
```

**Usage:**
```js
// Deactivate
await api.put(`/api/admin/courses/${courseId}/toggle-active?isActive=false`);

// Reactivate
await api.put(`/api/admin/courses/${courseId}/toggle-active?isActive=true`);
```

---

## 4. Batch Management

### `POST /api/admin/batches` `PROTECTED · ADMIN`

Create a batch for any course. Admin does not need to be the mentor.

**Request Body:**
```json
{
  "courseId":    "uuid",
  "name":        "Batch 1 - April 2026",
  "startDate":   "2026-04-01",
  "endDate":     "2026-04-30",
  "maxStudents": 30,
  "description": "Morning batch, 10AM–12PM"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `courseId` | Yes | UUID of the course |
| `name` | Yes | Batch display name |
| `startDate` | No | `YYYY-MM-DD` |
| `endDate` | No | `YYYY-MM-DD` |
| `maxStudents` | No | Cap on enrollment — omit for unlimited |
| `description` | No | Free text |

**Response `200`:** `BatchResponse`
```json
{
  "id":            "uuid",
  "name":          "Batch 1 - April 2026",
  "courseId":      "uuid",
  "courseName":    "Trading Fundamentals",
  "startDate":     "2026-04-01",
  "endDate":       "2026-04-30",
  "maxStudents":   30,
  "enrolledCount": 0,
  "status":        "UPCOMING",
  "description":   "Morning batch, 10AM–12PM",
  "createdAt":     "2026-03-23T10:00:00"
}
```

---

### `PUT /api/admin/batches/{batchId}/status` `PROTECTED · ADMIN`

Update batch lifecycle status.

**Request Body:**
```json
{ "status": "ACTIVE" }
```

| Status | When to use |
|--------|-------------|
| `UPCOMING` | Batch not started yet |
| `ACTIVE` | Batch is running |
| `COMPLETED` | Batch finished |
| `CANCELLED` | Batch cancelled |

**Response `200`:** Updated `BatchResponse`.

---

## 5. Student Enrollment

### `GET /api/admin/students/search?q=` `PROTECTED · ADMIN`

Autocomplete search for students by name or email.

**Query Param:** `q` — minimum 2 characters

**Response `200`:**
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

Use this to find the student's UUID before enrolling.

---

### `GET /api/admin/batches/{batchId}/students` `PROTECTED · ADMIN`

Get all active enrollments for a batch.

**Response `200`:** Array of `StudentSummaryResponse` with `enrolledAt` populated.

---

### `POST /api/admin/batches/{batchId}/enroll` `PROTECTED · ADMIN`

Enroll a student into a batch.

**Query Param:** `?studentId=uuid`

**Response `200`:**
```json
{ "success": true, "message": "Student enrolled", "data": null }
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Batch not found"` | Invalid batchId |
| `"Student not found"` | Invalid studentId |
| `"User is not a student"` | studentId belongs to a mentor or admin |
| `"Student is already enrolled in this batch"` | Duplicate |
| `"Batch is full"` | maxStudents limit reached |

**Usage:**
```js
// 1. Search for student
const searchRes = await api.get(`/api/admin/students/search?q=${query}`);
const student = searchRes.data.data[0]; // pick from autocomplete

// 2. Enroll
await api.post(`/api/admin/batches/${batchId}/enroll?studentId=${student.id}`);
```

---

### `DELETE /api/admin/batches/{batchId}/students/{studentId}` `PROTECTED · ADMIN`

Unenroll a student from a batch (soft delete — enrollment record kept).

**Response `200`:**
```json
{ "success": true, "message": "Student unenrolled", "data": null }
```

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Student is not enrolled in this batch"` | Already unenrolled or wrong IDs |

---

## 6. Meeting Management

### `POST /api/admin/meetings` `PROTECTED · ADMIN`

Create a Zoom meeting for any batch. Admin does not need to be the mentor.

**Request Body:**
```json
{
  "batchId":     "uuid",
  "topic":       "Day 1 - Market Intro",
  "durationMins": 90,
  "scheduledAt": "2026-04-01T10:00:00"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `batchId` | Yes | UUID of the batch |
| `topic` | Yes | Meeting title shown in Zoom |
| `durationMins` | No | Defaults to 120 minutes |
| `scheduledAt` | No | ISO datetime |

**Response `200`:** `MeetingResponse` with `startUrl` (for mentor to start) and `joinUrl` (for students).

```json
{
  "id":           "uuid",
  "zoomMeetingId":"84374937493",
  "topic":        "Day 1 - Market Intro",
  "batchId":      "uuid",
  "batchName":    "Batch 1 - April 2026",
  "status":       "UPCOMING",
  "scheduledAt":  "2026-04-01T10:00:00",
  "durationMins": 90,
  "startUrl":     "https://zoom.us/s/...",
  "joinUrl":      "https://zoom.us/j/...",
  "createdAt":    "2026-03-23T10:00:00"
}
```

---

### `GET /api/admin/batches/{batchId}/meetings` `PROTECTED · ADMIN`

Get all meetings for a batch — includes `startUrl`.

**Response `200`:** Array of `MeetingResponse`.

---

## 7. Bug Fixes — What Changed in Existing Flows

These are fixes to existing behaviour. No frontend changes needed, but good to know.

### Fix 1 — Login OTP: deactivated users now blocked at OTP step
**Before:** A deactivated user (`isActive: false`) could still complete login if they had the OTP.
**After:** After OTP is entered, the backend checks `isActive`. If false, returns:
```json
{ "success": false, "message": "Account is deactivated. Please contact support." }
```

### Fix 2 — Login OTP: no crash for users without a phone number
**Before:** If email OTP was wrong and user had no phone, the server threw a NullPointerException (500 error).
**After:** If no phone number is registered, mobile OTP fallback is skipped cleanly. Returns `"Invalid OTP"`.

### Fix 3 — Batch enrollment: no crash when maxStudents is not set
**Before:** Creating a batch without `maxStudents` and then enrolling a student caused a NullPointerException.
**After:** If `maxStudents` is null (unlimited batch), the cap check is skipped entirely.

### Fix 4 — Batch enrollment: race condition fixed
**Before:** Two concurrent enroll requests could both pass the "batch is full" check.
**After:** `enrollStudent` is now `@Transactional` — concurrent requests are serialized.

---

## 8. Quick Reference — All New Endpoints

### OTP-Verified Registration

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/users/send-otp` | Step 1: Send OTP to email + mobile |
| POST | `/api/admin/users/verify-and-create` | Step 2: Verify OTPs + create user |

### Course Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/courses` | Create course (specify mentorId) |
| PUT | `/api/admin/courses/{courseId}` | Update title / description / price |
| PUT | `/api/admin/courses/{courseId}/toggle-active?isActive=` | Activate or deactivate |

### Batch Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/batches` | Create batch for any course |
| PUT | `/api/admin/batches/{batchId}/status` | Update batch status |

### Student Enrollment

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/students/search?q=` | Autocomplete student search |
| GET | `/api/admin/batches/{batchId}/students` | List enrolled students |
| POST | `/api/admin/batches/{batchId}/enroll?studentId=` | Enroll student |
| DELETE | `/api/admin/batches/{batchId}/students/{studentId}` | Unenroll student |

### Meeting Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/meetings` | Create Zoom meeting for any batch |
| GET | `/api/admin/batches/{batchId}/meetings` | Get meetings for batch (with startUrl) |

---

## Key Notes for UI

- **Mentor search for course creation:** Use `GET /api/admin/users?role=MENTOR` to get the list of mentors, then let admin pick from a dropdown when creating a course.
- **Student search before enrollment:** Use `GET /api/admin/students/search?q=` autocomplete (min 2 chars) to find the student's UUID, then call the enroll endpoint.
- **OTP expiry is 10 minutes** for the admin registration flow (longer than the standard 5-minute login OTP).
- **Old `POST /api/admin/users` still works** — use it for test accounts only. For real users, use the 2-step OTP flow.
- **`startUrl` in meetings** — only returned in admin and mentor endpoints. Never exposed to students.
