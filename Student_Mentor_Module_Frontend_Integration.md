# Student & Mentor Module — Frontend Integration Guide

**Project:** First Million Trade — LMS Backend
**Base URL (dev):** `http://localhost:8080`
**Base URL (prod):** `https://api.firstmilliontrade.com`
**Document covers:** Mentor & Student module APIs — Courses, Batches, Classes (Meetings), Recordings, Dashboards, Enrollment
**Last updated:** 2026-03-20

---

## Table of Contents

1. [Authentication & Cookie Setup](#1-authentication--cookie-setup)
2. [Universal Response Format](#2-universal-response-format)
3. [Enums Reference](#3-enums-reference)
4. [Axios Instance Setup](#4-axios-instance-setup)
5. [Mentor APIs](#5-mentor-apis)
   - 5.1 [Dashboard](#51-mentor-dashboard)
   - 5.2 [Courses](#52-courses)
   - 5.3 [Batches](#53-batches)
   - 5.4 [Classes (Meetings)](#54-classes-meetings)
   - 5.5 [Student Management](#55-student-management)
6. [Student APIs](#6-student-apis)
   - 6.1 [Dashboard](#61-student-dashboard)
   - 6.2 [My Courses](#62-my-courses)
   - 6.3 [Schedule](#63-schedule)
   - 6.4 [Batch Classes & Join URL](#64-batch-classes--join-url)
7. [Error Handling](#7-error-handling)
8. [Role-Based Routing Guide](#8-role-based-routing-guide)
9. [Important Notes](#9-important-notes)
10. [Quick Reference — All Endpoints](#10-quick-reference--all-endpoints)

---

## 1. Authentication & Cookie Setup

### How Auth Works

After a successful login, the backend sets **two HttpOnly cookies** automatically:

| Cookie | Lifetime | Purpose |
|--------|----------|---------|
| `access_token` | 6 hours | Sent on every API call to authenticate |
| `refresh_token` | 14 days | Used to get a new access token silently |

- The browser stores and sends these cookies automatically.
- **You do NOT need to store or pass tokens manually in JS.**
- Cookies are `HttpOnly` — JS cannot read them (intentional, for security).

### CRITICAL: withCredentials

Every API call MUST include `withCredentials: true` (axios) or `credentials: 'include'` (fetch).
Without this, cookies are not sent → every request gets a `401 Unauthorized`.

```js
// axios
axios.get('/api/mentor/dashboard', { withCredentials: true })

// fetch
fetch('/api/mentor/dashboard', { credentials: 'include' })
```

### Role-Based Access

| Role | Can access |
|------|-----------|
| `MENTOR` | `/api/mentor/**` |
| `STUDENT` | `/api/student/**` |
| `ADMIN` | `/api/admin/**` |

Role is returned in the login response as `role: "MENTOR" | "STUDENT" | "ADMIN"`.
Store it in app state (Redux / Context) to control which routes the user sees.

---

## 2. Universal Response Format

Every API response — success or error — follows this structure:

```json
{
  "success": true,
  "message": "Human-readable message",
  "timestamp": "2026-03-20T10:30:00",
  "data": { ... }
}
```

| Field | Type | Notes |
|-------|------|-------|
| `success` | `boolean` | `true` = OK, `false` = error |
| `message` | `string` | Show this to users in toasts/alerts |
| `timestamp` | `string` (ISO datetime) | Server time of response |
| `data` | `object / array / null` | The actual payload — null on errors |

### Success Example
```json
{
  "success": true,
  "message": "Course created",
  "timestamp": "2026-03-20T10:30:00",
  "data": {
    "id": "uuid",
    "title": "Trading Fundamentals",
    "price": 4999.00
  }
}
```

### Error Example
```json
{
  "success": false,
  "message": "You are not enrolled in this batch",
  "timestamp": "2026-03-20T10:30:00"
}
```

**Always check `response.data.success` before using `response.data.data`.**

---

## 3. Enums Reference

### BatchStatus
```
UPCOMING   → Batch not yet started
ACTIVE     → Batch is currently running
COMPLETED  → Batch has ended
CANCELLED  → Batch was cancelled
```

### MeetingStatus
```
UPCOMING   → Class scheduled but not started
LIVE       → Class is currently ongoing (updated by Zoom webhook automatically)
ENDED      → Class has ended (updated by Zoom webhook automatically)
CANCELLED  → Class was cancelled
```

### RecordingStatus
```
PROCESSING → Recording received from Zoom, upload to Cloudflare in progress
AVAILABLE  → Recording ready to watch
FAILED     → Upload or processing failed
EXPIRED    → Recording link expired
```

---

## 4. Axios Instance Setup

Create a single axios instance for the entire app:

```js
// src/api/axiosInstance.js
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
  withCredentials: true,              // REQUIRED — sends cookies on every request
  headers: {
    'Content-Type': 'application/json',
  },
});

// Response interceptor — handle 401 globally
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status;

    if (status === 401) {
      // Try silent token refresh
      try {
        await axios.post('/api/auth/token/refresh', {}, { withCredentials: true });
        // Retry original request
        return api.request(error.config);
      } catch (refreshError) {
        // Refresh failed — redirect to login
        window.location.href = '/login';
      }
    }

    if (status === 403) {
      // Authenticated but wrong role
      window.location.href = '/unauthorized';
    }

    return Promise.reject(error);
  }
);

export default api;
```

---

## 5. Mentor APIs

All endpoints require the logged-in user to have role `MENTOR`.
Unauthorized access returns `403 Forbidden`.

---

### 5.1 Mentor Dashboard

**`GET /api/mentor/dashboard`**

Returns summary stats and recent data for the mentor's home screen.

#### Response
```json
{
  "success": true,
  "message": "Dashboard loaded",
  "data": {
    "totalCourses": 3,
    "totalBatches": 7,
    "totalClasses": 42,
    "totalStudents": 128,
    "recentBatches": [ /* last 5 BatchResponse objects */ ],
    "upcomingClasses": [ /* next 5 MeetingResponse objects with status=UPCOMING */ ]
  }
}
```

#### Usage
```js
const loadDashboard = async () => {
  const res = await api.get('/api/mentor/dashboard');
  if (res.data.success) {
    setDashboard(res.data.data);
  }
};
```

---

### 5.2 Courses

#### List All Courses — `GET /api/mentor/courses`

Returns all courses created by the logged-in mentor.

**Response `data` (array):**
```json
[
  {
    "id": "3f7a1b2c-...",
    "title": "Trading Fundamentals",
    "description": "Learn stock market basics",
    "price": 4999.00,
    "isActive": true,
    "mentorName": "Yash Reddy",
    "createdAt": "2026-03-15T10:00:00"
  }
]
```

> Use this list to populate the **"Select Course"** dropdown when creating a batch.
> The `id` from each item is the `courseId` to send in the batch creation request.

---

#### Get Single Course — `GET /api/mentor/courses/{courseId}`

**Path param:** `courseId` — UUID from the courses list

**Response `data`:** Single CourseResponse object (same structure as list item).

---

#### Get Batches for a Course — `GET /api/mentor/courses/{courseId}/batches`

Returns all batches belonging to a specific course. Use this on the **course detail page** to list batches under that course.

**Path param:** `courseId` — UUID

**Request:** No body. Auth via cookie.

**Response `data` (array of BatchResponse):**
```json
[
  {
    "id": "uuid",
    "name": "Batch 1 - March 2026",
    "courseId": "uuid",
    "courseName": "Trading Fundamentals",
    "startDate": "2026-03-21",
    "endDate": "2026-04-21",
    "maxStudents": 30,
    "enrolledCount": 12,
    "status": "ACTIVE",
    "description": "Morning batch, 10AM–12PM",
    "createdAt": "2026-03-15T10:00:00"
  }
]
```

Returns empty array `[]` if the course has no batches yet.

**Error Responses:**

| HTTP | Message | Cause |
|------|---------|-------|
| 400 | `"Course not found"` | Invalid `courseId` |
| 400 | `"Access denied"` | Course belongs to a different mentor |
| 401 | `"Authentication required"` | Not logged in |
| 403 | Forbidden | User is not a MENTOR |

**UI Pattern — Course Detail Page:**
```js
const CourseDetailPage = () => {
  const { courseId } = useParams();
  const [course, setCourse] = useState(null);
  const [batches, setBatches] = useState([]);

  useEffect(() => {
    // Load course info and its batches in parallel
    Promise.all([
      api.get(`/api/mentor/courses/${courseId}`),
      api.get(`/api/mentor/courses/${courseId}/batches`),
    ]).then(([courseRes, batchRes]) => {
      if (courseRes.data.success) setCourse(courseRes.data.data);
      if (batchRes.data.success) setBatches(batchRes.data.data);
    });
  }, [courseId]);

  return (
    <>
      <h1>{course?.title}</h1>
      <p>{course?.description}</p>

      {/* "Create Batch" button — pre-fills courseId */}
      <button onClick={() => navigate(`/mentor/batches/new?courseId=${courseId}`)}>
        + Create Batch
      </button>

      {/* Batch list for this course */}
      {batches.map(batch => (
        <div key={batch.id} onClick={() => navigate(`/mentor/batches/${batch.id}`)}>
          <span>{batch.name}</span>
          <span>{batch.enrolledCount}/{batch.maxStudents} students</span>
          <span>{batch.status}</span>
        </div>
      ))}

      {batches.length === 0 && <p>No batches yet. Create the first one.</p>}
    </>
  );
};
```

---

#### Create Course — `POST /api/mentor/courses`

**Request Body:**
```json
{
  "title": "Advanced Options Trading",
  "description": "Deep dive into options strategies",
  "price": 9999.00
}
```

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `title` | string | Yes | Not blank |
| `description` | string | No | — |
| `price` | number (decimal) | No | — |

**Response `data`:** CourseResponse object.

**Usage:**
```js
const createCourse = async (formData) => {
  const res = await api.post('/api/mentor/courses', {
    title: formData.title,
    description: formData.description,
    price: parseFloat(formData.price) || null,
  });

  if (res.data.success) {
    toast.success(res.data.message);
    navigate(`/mentor/courses/${res.data.data.id}`);
  } else {
    toast.error(res.data.message);
  }
};
```

---

### 5.3 Batches

A batch is a specific run/cohort of a course with an enrollment limit and schedule.

#### List All Batches — `GET /api/mentor/batches`

**Response `data` (array):**
```json
[
  {
    "id": "uuid",
    "name": "Batch 1 - March 2026",
    "courseId": "uuid",
    "courseName": "Trading Fundamentals",
    "startDate": "2026-03-21",
    "endDate": "2026-04-21",
    "maxStudents": 30,
    "enrolledCount": 12,
    "status": "ACTIVE",
    "description": "Morning batch, 10AM–12PM",
    "createdAt": "2026-03-15T10:00:00"
  }
]
```

> Use this list to populate the **"Select Batch"** dropdown when creating a class/meeting.

---

#### Get Single Batch — `GET /api/mentor/batches/{batchId}`

**Path param:** `batchId` — UUID from the batches list
**Response `data`:** Single BatchResponse object.

---

#### Create Batch — `POST /api/mentor/batches`

**UI Flow:** First call `GET /api/mentor/courses` to load the course dropdown. When user selects a course, use its `id` as `courseId`.

**Request Body:**
```json
{
  "name": "Batch 2 - April 2026",
  "courseId": "3f7a1b2c-...",
  "startDate": "2026-04-01",
  "endDate": "2026-04-30",
  "maxStudents": 25,
  "description": "Evening batch, 6PM–8PM"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `name` | string | Yes | Not blank |
| `courseId` | UUID string | Yes | Selected from course dropdown — must be a course owned by this mentor |
| `startDate` | string `YYYY-MM-DD` | No | — |
| `endDate` | string `YYYY-MM-DD` | No | — |
| `maxStudents` | number | No | Defaults to `30` if not provided |
| `description` | string | No | — |

**Response `data`:** BatchResponse object.

**Usage:**
```js
// Step 1: Load courses for the dropdown (on page mount)
const [courses, setCourses] = useState([]);
useEffect(() => {
  api.get('/api/mentor/courses').then(res => {
    if (res.data.success) setCourses(res.data.data);
  });
}, []);

// Step 2: Submit form
const createBatch = async (formData) => {
  const res = await api.post('/api/mentor/batches', {
    name: formData.name,
    courseId: formData.courseId,        // from dropdown selection
    startDate: formData.startDate || null,
    endDate: formData.endDate || null,
    maxStudents: parseInt(formData.maxStudents) || null,
    description: formData.description || null,
  });

  if (res.data.success) {
    toast.success('Batch created!');
    navigate(`/mentor/batches/${res.data.data.id}`);
  } else {
    toast.error(res.data.message);
  }
};
```

---

#### Update Batch Status — `PUT /api/mentor/batches/{batchId}/status`

Used to move a batch through its lifecycle. The mentor controls this manually from the batch detail page.

**Path param:** `batchId` — UUID

**Request Body:**
```json
{
  "status": "ACTIVE"
}
```

| Value | When to use |
|-------|------------|
| `UPCOMING` | Batch created but not started yet (default) |
| `ACTIVE` | Batch is running — students can see and join classes |
| `COMPLETED` | All classes done — batch archived |
| `CANCELLED` | Batch cancelled |

**Response `data`:** Updated BatchResponse object.

**Usage:**
```js
const updateBatchStatus = async (batchId, newStatus) => {
  const res = await api.put(`/api/mentor/batches/${batchId}/status`, {
    status: newStatus,
  });

  if (res.data.success) {
    toast.success(`Batch marked as ${newStatus}`);
    setBatch(res.data.data);
  } else {
    toast.error(res.data.message);
  }
};

// Example — "Mark as Active" button on batch detail page
<button onClick={() => updateBatchStatus(batch.id, 'ACTIVE')}>
  Mark as Active
</button>
```

---

### 5.4 Classes (Meetings)

A "class" is a live Zoom meeting scheduled for a batch.

---

#### List All Classes — `GET /api/mentor/classes`

Returns all meetings created by the mentor across all batches.

**Response `data` (array of MeetingResponse):**
```json
[
  {
    "id": "uuid",
    "zoomMeetingId": "84374937493",
    "topic": "Day 1 - Market Intro",
    "batchId": "uuid",
    "batchName": "Batch 1 - March 2026",
    "status": "UPCOMING",
    "scheduledAt": "2026-03-21T10:00:00",
    "durationMins": 90,
    "createdAt": "2026-03-20T10:00:00",
    "startUrl": "https://zoom.us/s/84374937493?zak=...",
    "joinUrl": "https://zoom.us/j/84374937493"
  }
]
```

> `startUrl` is only present in mentor responses. Never shown to students.

---

#### Get All Classes for a Batch — `GET /api/mentor/batches/{batchId}/classes`

**Path param:** `batchId` — UUID
**Response `data`:** Array of MeetingResponse (same as above, includes `startUrl`).

---

#### Create a Class — `POST /api/mentor/classes`

Calls the Zoom API internally, creates a real Zoom meeting, and saves it. Returns `startUrl` for the mentor and `joinUrl` for students.

**UI Flow:** First call `GET /api/mentor/batches` to load the batch dropdown. When user selects a batch, use its `id` as `batchId`.

**Request Body:**
```json
{
  "batchId": "3f7a1b2c-...",
  "topic": "Day 1 - Market Intro",
  "durationMins": 90,
  "scheduledAt": "2026-03-21T10:00:00"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `batchId` | UUID string | Yes | Selected from batch dropdown — must be owned by this mentor |
| `topic` | string | Yes | Not blank — becomes the Zoom meeting title |
| `durationMins` | number | No | Defaults to `120` if not provided |
| `scheduledAt` | string (ISO datetime) | No | Format: `YYYY-MM-DDTHH:mm:ss` |

**Response `data`:**
```json
{
  "id": "uuid",
  "zoomMeetingId": "84374937493",
  "topic": "Day 1 - Market Intro",
  "batchId": "uuid",
  "batchName": "Batch 1 - March 2026",
  "status": "UPCOMING",
  "scheduledAt": "2026-03-21T10:00:00",
  "durationMins": 90,
  "createdAt": "2026-03-20T10:00:00",
  "startUrl": "https://zoom.us/s/84374937493?zak=...",
  "joinUrl": "https://zoom.us/j/84374937493"
}
```

**Usage:**
```js
// Step 1: Load batches for the dropdown (on page mount)
const [batches, setBatches] = useState([]);
useEffect(() => {
  api.get('/api/mentor/batches').then(res => {
    if (res.data.success) setBatches(res.data.data);
  });
}, []);

// Step 2: Submit form
const createClass = async (formData) => {
  const res = await api.post('/api/mentor/classes', {
    batchId: formData.batchId,          // from dropdown selection
    topic: formData.topic,
    durationMins: parseInt(formData.durationMins) || 120,
    scheduledAt: formData.scheduledAt || null,  // "2026-03-21T10:00:00"
  });

  if (res.data.success) {
    const meeting = res.data.data;
    toast.success('Class created!');
    setStartUrl(meeting.startUrl);      // show "Start Class" button to mentor
    setJoinUrl(meeting.joinUrl);        // show "Copy Join Link" button
  } else {
    toast.error(res.data.message);
  }
};
```

**Important UI notes:**
- Show `startUrl` as a **"Start Class"** button → opens Zoom as host
- Show `joinUrl` as a **"Copy Join Link"** button → mentor copies and shares with students
- **Never** show `startUrl` on the student side — it won't be in student responses anyway

---

### 5.5 Student Management

#### Search Students — `GET /api/mentor/students/search?q={query}`

Used to search students by name or email for the enrollment autocomplete/combobox.
Returns results for any partial match (minimum 2 characters).

**Query param:** `q` — search string (min 2 chars; returns empty array if shorter)

**Response `data` (array of StudentSummaryResponse):**
```json
[
  {
    "id": "a9d2f1e3-...",
    "firstName": "Raj",
    "lastName": "Kumar",
    "email": "raj@gmail.com",
    "phoneNumber": "9876543210",
    "enrolledAt": null
  }
]
```

> `enrolledAt` is `null` here — it is only populated in the batch student list.

**UI Pattern — Enrollment Autocomplete:**
```js
// Debounced search as the mentor types
const [query, setQuery] = useState('');
const [results, setResults] = useState([]);
const [selectedStudent, setSelectedStudent] = useState(null);

useEffect(() => {
  if (query.length < 2) { setResults([]); return; }

  const timer = setTimeout(async () => {
    const res = await api.get(`/api/mentor/students/search?q=${encodeURIComponent(query)}`);
    if (res.data.success) setResults(res.data.data);
  }, 300);  // debounce 300ms

  return () => clearTimeout(timer);
}, [query]);

// JSX
<input
  type="text"
  placeholder="Search by name or email..."
  value={query}
  onChange={(e) => setQuery(e.target.value)}
/>
{results.map(student => (
  <div key={student.id} onClick={() => {
    setSelectedStudent(student);
    setQuery(`${student.firstName} ${student.lastName}`);
    setResults([]);
  }}>
    {student.firstName} {student.lastName} — {student.email}
  </div>
))}
```

---

#### Enroll a Student — `POST /api/mentor/students/enroll`

**Request Body:**
```json
{
  "batchId": "3f7a1b2c-...",
  "studentId": "a9d2f1e3-..."
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `batchId` | UUID string | Yes | The batch to enroll into |
| `studentId` | UUID string | Yes | From the student search result (`student.id`) |

**Possible error messages:**
| Message | Cause |
|---------|-------|
| `"Student is already enrolled in this batch"` | Duplicate enrollment |
| `"Batch is full"` | `enrolledCount >= maxStudents` |
| `"User is not a student"` | The selected user has MENTOR/ADMIN role |
| `"Batch not found"` | Invalid batchId |

**Full enrollment flow:**
```js
const enrollStudent = async () => {
  if (!selectedStudent) {
    toast.error('Please select a student first');
    return;
  }

  const res = await api.post('/api/mentor/students/enroll', {
    batchId: currentBatchId,           // from route params or context
    studentId: selectedStudent.id,     // from search selection
  });

  if (res.data.success) {
    toast.success('Student enrolled!');
    setSelectedStudent(null);
    setQuery('');
    refreshStudentList();              // re-fetch batch students
  } else {
    toast.error(res.data.message);
  }
};
```

---

#### List Students in a Batch — `GET /api/mentor/batches/{batchId}/students`

Returns all currently active enrollments for a batch with full student details and enrollment date.

**Path param:** `batchId` — UUID

**Response `data` (array of StudentSummaryResponse):**
```json
[
  {
    "id": "a9d2f1e3-...",
    "firstName": "Raj",
    "lastName": "Kumar",
    "email": "raj@gmail.com",
    "phoneNumber": "9876543210",
    "enrolledAt": "2026-03-15T10:00:00"
  }
]
```

> `enrolledAt` is populated here — use it to show the enrollment date in the student table.

---

#### Unenroll a Student — `DELETE /api/mentor/batches/{batchId}/students/{studentId}`

Soft-removes the student from the batch (sets enrollment to inactive — data is preserved).

**Path params:**
- `batchId` — UUID of the batch
- `studentId` — UUID of the student (from the batch students list)

**Response:**
```json
{
  "success": true,
  "message": "Student unenrolled successfully",
  "data": null
}
```

**UI Pattern — Students Table with Unenroll:**
```js
// After fetching batch students
const [students, setStudents] = useState([]);

const loadStudents = async () => {
  const res = await api.get(`/api/mentor/batches/${batchId}/students`);
  if (res.data.success) setStudents(res.data.data);
};

const unenrollStudent = async (studentId, studentName) => {
  if (!window.confirm(`Remove ${studentName} from this batch?`)) return;

  const res = await api.delete(`/api/mentor/batches/${batchId}/students/${studentId}`);
  if (res.data.success) {
    toast.success('Student removed');
    setStudents(prev => prev.filter(s => s.id !== studentId));
  } else {
    toast.error(res.data.message);
  }
};

// JSX
{students.map(student => (
  <tr key={student.id}>
    <td>{student.firstName} {student.lastName}</td>
    <td>{student.email}</td>
    <td>{formatDate(student.enrolledAt)}</td>
    <td>
      <button onClick={() => unenrollStudent(student.id, `${student.firstName} ${student.lastName}`)}>
        Remove
      </button>
    </td>
  </tr>
))}
```

---

## 6. Student APIs

All endpoints require the logged-in user to have role `STUDENT`.
Unauthorized access returns `403 Forbidden`.

---

### 6.1 Student Dashboard

**`GET /api/student/dashboard`**

Returns enrolled batch count, batch list, and upcoming classes.

#### Response
```json
{
  "success": true,
  "message": "Dashboard loaded",
  "data": {
    "enrolledCoursesCount": 2,
    "enrolledBatches": [ /* array of BatchResponse */ ],
    "upcomingClasses": [ /* next 5 MeetingResponse — NO startUrl */ ]
  }
}
```

#### MeetingResponse for Students (NO `startUrl`)
```json
{
  "id": "uuid",
  "topic": "Day 1 - Market Intro",
  "batchId": "uuid",
  "batchName": "Batch 1 - March 2026",
  "status": "UPCOMING",
  "scheduledAt": "2026-03-21T10:00:00",
  "durationMins": 90,
  "createdAt": "2026-03-20T10:00:00",
  "joinUrl": "https://zoom.us/j/84374937493"
}
```

---

### 6.2 My Courses

**`GET /api/student/courses`**

Returns all batches the student is enrolled in.

**Response `data` (array of BatchResponse):**
```json
[
  {
    "id": "uuid",
    "name": "Batch 1 - March 2026",
    "courseId": "uuid",
    "courseName": "Trading Fundamentals",
    "startDate": "2026-03-21",
    "endDate": "2026-04-21",
    "maxStudents": 30,
    "enrolledCount": 12,
    "status": "ACTIVE",
    "description": "Morning batch",
    "createdAt": "2026-03-15T10:00:00"
  }
]
```

---

### 6.3 Schedule

**`GET /api/student/schedule`**

Returns all `UPCOMING` meetings across all batches the student is enrolled in. No `startUrl` in any response.

**Response `data`:** Array of MeetingResponse (student view).

**Usage:**
```js
const loadSchedule = async () => {
  const res = await api.get('/api/student/schedule');
  if (res.data.success) {
    setUpcomingClasses(res.data.data);   // array, may be empty []
  }
};
```

---

### 6.4 Batch Classes & Join URL

#### All Classes for a Batch — `GET /api/student/batches/{batchId}/classes`

Returns all meetings (all statuses) for a specific batch. Student must be enrolled — returns error if not.

**Path param:** `batchId` — UUID from the student's enrolled batches list

**Response `data`:** Array of MeetingResponse (no `startUrl`), ordered newest first.

---

#### Get Join URL — `GET /api/student/classes/{meetingId}/join`

The main endpoint for a student to get the Zoom link for a class.

**Path param:** `meetingId` — the meeting's `id` UUID from our database (from the batch classes list)

**Validation:**
- Student must be enrolled in the batch this meeting belongs to
- Returns error with `"You are not enrolled in this batch"` if not

**Response `data`:**
```json
{
  "id": "uuid",
  "topic": "Day 1 - Market Intro",
  "batchId": "uuid",
  "batchName": "Batch 1 - March 2026",
  "status": "UPCOMING",
  "scheduledAt": "2026-03-21T10:00:00",
  "durationMins": 90,
  "joinUrl": "https://zoom.us/j/84374937493"
}
```

**Usage:**
```js
const joinClass = async (meetingId) => {
  const res = await api.get(`/api/student/classes/${meetingId}/join`);

  if (res.data.success) {
    const { joinUrl, status, scheduledAt } = res.data.data;

    if (status === 'LIVE') {
      window.open(joinUrl, '_blank');
    } else if (status === 'UPCOMING') {
      toast.info(`Class starts at ${new Date(scheduledAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })}`);
    } else if (status === 'ENDED') {
      toast.warn('This class has already ended. Check recordings.');
    }
  } else {
    toast.error(res.data.message);
  }
};
```

---

## 7. Error Handling

### HTTP Status Codes

| Status | Meaning | What to do |
|--------|---------|-----------|
| `200` | Success | Use `response.data.data` |
| `400` | Bad request / validation error | Show `response.data.message` |
| `401` | Not authenticated | Redirect to `/login` |
| `403` | Wrong role / access denied | Redirect to unauthorized page |
| `500` | Server error | Show generic error message |

### Common Error Messages

| Message | Endpoint | Cause |
|---------|----------|-------|
| `"Batch not found"` | Any batch endpoint | Invalid batchId |
| `"Student not found"` | Enroll / unenroll | Invalid studentId |
| `"Student is already enrolled in this batch"` | Enroll | Duplicate |
| `"Batch is full"` | Enroll | enrolledCount >= maxStudents |
| `"You are not enrolled in this batch"` | Student join | Student not in batch |
| `"You can only create meetings for your own batches"` | Create class | Wrong mentor |
| `"You can only manage students in your own batches"` | Unenroll | Wrong mentor |
| `"Topic is required"` | Create class | Missing field |
| `"Batch name is required"` | Create batch | Missing field |

### Global Error Handler (add to axios interceptor)
```js
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || 'Something went wrong';

    if (status === 401) {
      store.dispatch(logout());
      window.location.href = '/login';
    } else if (status === 403) {
      toast.error('You do not have permission to perform this action');
    } else if (status >= 500) {
      toast.error('Server error. Please try again later.');
    }

    return Promise.reject(error);
  }
);
```

---

## 8. Role-Based Routing Guide

After login, the user's `role` field determines where to redirect:

```js
const { role } = loginResponse.data;

switch (role) {
  case 'MENTOR':  navigate('/mentor/dashboard'); break;
  case 'STUDENT': navigate('/student/dashboard'); break;
  case 'ADMIN':   navigate('/admin/dashboard'); break;
}
```

### Protected Route Component
```jsx
const ProtectedRoute = ({ allowedRoles, children }) => {
  const { user } = useAuth();

  if (!user) return <Navigate to="/login" />;
  if (!allowedRoles.includes(user.role)) return <Navigate to="/unauthorized" />;

  return children;
};

// Usage
<Route path="/mentor/dashboard" element={
  <ProtectedRoute allowedRoles={['MENTOR']}><MentorDashboard /></ProtectedRoute>
} />
<Route path="/student/dashboard" element={
  <ProtectedRoute allowedRoles={['STUDENT']}><StudentDashboard /></ProtectedRoute>
} />
```

---

## 9. Important Notes

### IDs — Where They Come From

Never hardcode or ask users to type UUIDs. Every ID should come from a prior API response:

| ID needed | Where to get it |
|-----------|----------------|
| `courseId` (when creating batch) | From `GET /api/mentor/courses` list — show as dropdown |
| `batchId` (when creating class) | From `GET /api/mentor/batches` list — show as dropdown |
| `batchId` (when creating class, pre-selected) | From the URL if user is already on a batch page |
| `studentId` (when enrolling) | From `GET /api/mentor/students/search?q=` — show as autocomplete |
| `studentId` (when unenrolling) | From `GET /api/mentor/batches/{batchId}/students` list |
| `meetingId` (student join) | From `GET /api/student/batches/{batchId}/classes` list |

### startUrl vs joinUrl

| URL | Who gets it | What it does |
|-----|------------|-------------|
| `startUrl` | **MENTOR ONLY** | Opens Zoom and starts the meeting as host |
| `joinUrl` | Both mentor + students | Joins Zoom as a participant |

- Backend strips `startUrl` from all student responses server-side — it will simply not be in the JSON.
- Show `startUrl` as a **"Start Class"** button: `window.open(startUrl, '_blank')`

### Meeting Status Updates (Automatic)

`UPCOMING → LIVE → ENDED` transitions happen automatically via Zoom webhooks. No frontend action needed.

To reflect live status in the UI without WebSockets, poll the schedule endpoint:
```js
// Poll every 60 seconds on the schedule page
useEffect(() => {
  loadSchedule();
  const interval = setInterval(loadSchedule, 60000);
  return () => clearInterval(interval);
}, []);
```

### Date & Time Format

All datetimes from the backend are ISO 8601 without timezone (server is IST UTC+5:30):
```
"2026-03-21T10:00:00"
```

Display in user's timezone:
```js
new Date('2026-03-21T10:00:00').toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })
// → "21/3/2026, 10:00:00 am"
```

### Batch Full Check

```js
const isFull = batch.maxStudents != null && batch.enrolledCount >= batch.maxStudents;
// Show "Batch Full" badge and disable enroll button if isFull
```

### Recordings (Phase 2)

The `RecordingResponse` schema exists in the backend but the full recordings API is **Phase 2** (Cloudflare Stream integration). Do not build recording UI yet.

---

## 10. Quick Reference — All Endpoints

### Mentor (`/api/mentor/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/mentor/dashboard` | Dashboard stats + recent data |
| GET | `/api/mentor/courses` | List all my courses |
| POST | `/api/mentor/courses` | Create a course |
| GET | `/api/mentor/courses/{courseId}` | Get course by ID |
| GET | `/api/mentor/courses/{courseId}/batches` | All batches under a specific course |
| GET | `/api/mentor/batches` | List all my batches |
| POST | `/api/mentor/batches` | Create a batch |
| GET | `/api/mentor/batches/{batchId}` | Get batch by ID |
| PUT | `/api/mentor/batches/{batchId}/status` | Update batch status |
| GET | `/api/mentor/batches/{batchId}/classes` | All classes in a batch (with startUrl) |
| GET | `/api/mentor/batches/{batchId}/students` | Students enrolled in a batch (with IDs) |
| GET | `/api/mentor/classes` | All my classes across all batches |
| POST | `/api/mentor/classes` | Create a class — calls Zoom API |
| GET | `/api/mentor/students/search?q=` | Search students by name or email |
| POST | `/api/mentor/students/enroll` | Enroll a student in a batch |
| DELETE | `/api/mentor/batches/{batchId}/students/{studentId}` | Unenroll a student |

### Student (`/api/student/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/student/dashboard` | Dashboard — enrolled batches + upcoming |
| GET | `/api/student/courses` | All my enrolled batches |
| GET | `/api/student/schedule` | Upcoming classes across all batches |
| GET | `/api/student/batches/{batchId}/classes` | All classes in a batch I'm enrolled in |
| GET | `/api/student/classes/{meetingId}/join` | Get join URL for a specific class |
