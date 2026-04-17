# Recordings Feature — Frontend Integration Guide

**Date:** 2026-04-11  
**Shared with:** Frontend team  
**Stack:** Spring Boot + PostgreSQL + Zoom Cloud Recording + Bunny.net Stream

---

## Overview

Live classes are recorded automatically via Zoom Cloud Recording.  
After a class ends, the recording is transferred to Bunny.net Stream for secure hosting.  
Students can watch recordings inside the app — no downloading, no redirecting to Zoom/Bunny.

**Recording lifecycle:**

```
Zoom records class automatically
    ↓
Zoom fires webhook → backend saves row (status: PROCESSING)
    ↓
Backend downloads MP4 from Zoom → uploads to Bunny.net (async, ~5–15 min)
    ↓
Bunny fires webhook → backend marks row (status: AVAILABLE)
    ↓
Student can watch
    ↓
After batch end + 2 months → auto-deleted (status: EXPIRED)
```

---

## Status Values

| Status | Meaning | What to show student |
|--------|---------|----------------------|
| `PROCESSING` | Upload in progress | "Recording being processed…" |
| `AVAILABLE` | Ready to watch | Watch button |
| `FAILED` | Processing error | "Recording unavailable" (admin investigates) |
| `EXPIRED` | Deleted after access period | "Recording no longer available" |

> **Only `AVAILABLE` recordings are returned to students** — the API already filters this server-side. You don't need to check status in the response.

---

## Response Format

All endpoints wrap their response in a standard envelope:

```json
{
  "success": true,
  "message": "Recordings fetched",
  "data": [ ... ]
}
```

Read `response.data` to get the actual payload. On errors, `success` is `false` and `message` describes the error.

---

## Endpoints

---

### 1. `GET /api/recordings/batch/{batchId}`

List all available recordings for a batch.

**Auth:** Required — student must be enrolled in this batch  
**Role:** STUDENT

**Request:**
```
GET /api/recordings/batch/{batchId}
Authorization: (cookie or Bearer token)
```

**Response `200 OK`:**
```json
[
  {
    "id": "uuid",
    "title": "Class 12 - Risk Management",
    "recordedDate": "2026-04-10",
    "durationMins": 120,
    "status": "AVAILABLE"
  },
  {
    "id": "uuid",
    "title": "Class 11 - Chart Patterns",
    "recordedDate": "2026-04-07",
    "durationMins": 90,
    "status": "AVAILABLE"
  }
]
```

> `status` is always `AVAILABLE` in this response — the API filters server-side. Included so the frontend can render consistently without special-casing.

**Error responses:**
| Code | Meaning |
|------|---------|
| `401` | Not logged in |
| `403` | Not enrolled in this batch |
| `404` | Batch not found |

> `bunnyVideoId` and `zoomDownloadUrl` are **never** returned. Do not ask for them.

---

### 2. `GET /api/recordings/{recordingId}/play`

Get a signed, time-limited play URL for a specific recording.

**Auth:** Required — student must be enrolled in the recording's batch  
**Role:** STUDENT

**Request:**
```
GET /api/recordings/{recordingId}/play
Authorization: (cookie or Bearer token)
```

**Response `200 OK`:**
```json
{
  "playUrl": "https://iframe.mediadelivery.net/embed/<libraryId>/<videoId>?token=abc123&expires=1713000000",
  "expiresIn": 10800
}
```

| Field | Type | Description |
|-------|------|-------------|
| `playUrl` | `string` | Signed Bunny.net Stream embed URL — valid for 3 hours |
| `expiresIn` | `number` | Seconds until the URL expires (always 10800 = 3h) |

**Signed URL algorithm (backend reference):**
```
token = SHA256(tokenKey + videoId + expiryTimestamp)  →  lowercase hex
url   = https://iframe.mediadelivery.net/embed/{libraryId}/{videoId}?token={token}&expires={expiryTimestamp}
```

**Error responses:**
| Code | Meaning |
|------|---------|
| `401` | Not logged in |
| `403` | Not enrolled in batch, or recording expired |
| `404` | Recording not found |
| `400` | Recording not yet available (still PROCESSING) |

> **The URL expires in 3 hours.** Call this endpoint fresh each time the student opens a video — do not cache the URL across sessions.

---

## How to Embed the Video

The `playUrl` is a Bunny Stream embed URL — put it directly in an `<iframe>`:

```html
<iframe
  src="{playUrl}"
  width="100%"
  height="500"
  frameborder="0"
  allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
  allowfullscreen
></iframe>
```

> The URL already contains the signed token — do not modify it. Use it as returned.

---

## Mentor Endpoints

---

### 3. `GET /api/mentor/recordings`

List all recordings for classes conducted by the logged-in mentor.

**Auth:** Required  
**Role:** MENTOR

**Response `200 OK`:**
```json
[
  {
    "id": "uuid",
    "title": "Class 12 - Risk Management",
    "recordedDate": "2026-04-10",
    "durationMins": 120,
    "status": "AVAILABLE",
    "batchName": "April 2026 Batch",
    "courseName": "Advanced Trading"
  }
]
```

> `status` is included so mentor can see if a recording is still processing or failed.

---

### 4. `GET /api/mentor/batches/{batchId}/recordings`

List recordings for a specific batch (mentor view).

**Auth:** Required  
**Role:** MENTOR

**Response:** Same shape as mentor list above.

---

### 4a. `GET /api/mentor/recordings/{recordingId}/play`

Get a signed play URL so the mentor can watch a recording from one of their own classes.

**Auth:** Required  
**Role:** MENTOR

**Response `200 OK`:**
```json
{
  "playUrl": "https://iframe.mediadelivery.net/embed/...",
  "expiresIn": 10800
}
```

**Error responses:**
| Code | Meaning |
|------|---------|
| `403` | Recording is not from one of your classes |
| `400` | Recording still processing or failed |
| `410` | Recording has expired |
| `404` | Recording not found |

> Same embed flow as students — put `playUrl` in an `<iframe>`. URL valid 3 hours, call fresh each time.

---

## Admin Endpoints

---

### 5. `GET /api/admin/recordings`

List all recordings across all batches.

**Auth:** Required  
**Role:** ADMIN

**Response:** Same shape as mentor list, all batches included.

---

### 5a. `GET /api/admin/recordings/{recordingId}/play`

Get a signed play URL so admin can watch any recording.

**Auth:** Required  
**Role:** ADMIN

**Response `200 OK`:**
```json
{
  "playUrl": "https://iframe.mediadelivery.net/embed/...",
  "expiresIn": 10800
}
```

**Error responses:**
| Code | Meaning |
|------|---------|
| `400` | Recording still processing or failed |
| `410` | Recording has expired |
| `404` | Recording not found |

> Same embed flow as students — put `playUrl` in an `<iframe>`. URL valid 3 hours, call fresh each time.

---

### 6. `POST /api/admin/recordings/manual` — Manual Recording (Backup Flow)

**When to use:** The app was down during a live class. The class continued on Zoom, the recording saved to Zoom Cloud, but the Zoom webhook was never received by our backend. Use this to manually trigger the download → Bunny upload pipeline for that missed recording.

**Auth:** Required  
**Role:** ADMIN only

**Request body:**
```json
{
  "zoomMeetingId": "87654321234",
  "meetingId": "uuid (optional)",
  "batchId": "uuid (optional — only needed if meeting wasn't created via our app)",
  "title": "Class 14 - Risk Management (optional override)",
  "durationMins": 120
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `zoomMeetingId` | **Yes** | The Zoom meeting ID — the numeric string visible in Zoom dashboard or cloud recordings page |
| `meetingId` | No | Our internal meeting UUID. Use this if you can find the meeting in the admin panel — most precise. |
| `batchId` | Conditional | Required **only** if the class was run directly from Zoom (not created via our app). A placeholder meeting record will be created. |
| `title` | No | Override the recording title. Defaults to the meeting topic. |
| `durationMins` | No | Override the duration. Defaults to what's stored on the meeting. |

**How admin finds the Zoom meeting ID:**
- Go to zoom.us → Recordings → Cloud Recordings
- Find the class recording — the meeting ID is shown (e.g. `87654321234`)
- Copy and paste it into this field

> The backend calls the Zoom API directly to fetch the download URL — admin does NOT need to paste any token-bearing URLs.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Recording queued for processing. It will be AVAILABLE in ~15–30 minutes.",
  "data": {
    "id": "uuid",
    "title": "Class 14 - Risk Management",
    "batchId": "uuid",
    "batchName": "April 2026 Batch",
    "status": "PROCESSING",
    "durationMins": 120
  }
}
```

**Error responses:**
| Code | Meaning | Fix |
|------|---------|-----|
| `404` | Meeting not found by zoomMeetingId | Provide `meetingId` (our UUID), or provide `batchId` if class ran outside app |
| `409` | Recording already exists for this meeting | Use the retry endpoint if it failed |
| `400` | Zoom has no completed recording yet | Wait a few minutes — Zoom may still be processing |
| `503` | Zoom API call failed | Check ZOOM_* env vars; Zoom may be down |

---

### 7. `POST /api/admin/recordings/{recordingId}/retry`

Retry a recording that has `status: FAILED`.

**Auth:** Required  
**Role:** ADMIN only

**When to use:** A recording failed to process (Bunny upload failed, network error, etc.). This re-fetches a fresh Zoom download URL (the previously stored token may have expired) and restarts the pipeline.

**Request:** No body — just the recording UUID in the path.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Recording retry started. Check status in ~15–30 minutes.",
  "data": null
}
```

**Error responses:**
| Code | Meaning |
|------|---------|
| `404` | Recording not found |
| `400` | Recording is not in FAILED status (only FAILED can be retried) |

---

## Recommended UI Flow

### Student — Recordings Tab

```
1. Student opens "My Classes" → picks a batch
2. Call GET /api/recordings/batch/{batchId}
3. Show list of recordings (title, date, duration)
4. Student clicks "Watch"
5. Call GET /api/recordings/{recordingId}/play
6. Embed the returned playUrl in an iframe or video player
```

**Handling states in the list:**
- If the API returns an empty array → show "No recordings yet"
- Show a refresh button (recordings appear ~10–15 minutes after class ends)

### Student — Dashboard

The student dashboard (`GET /api/student/dashboard`) does **not** include recordings — recordings are accessed through the batch-specific recordings tab only.

---

## Security Rules (What Frontend Must Respect)

| Rule | Detail |
|------|--------|
| Never ask for `bunnyVideoId` | Backend will never send it — it's an internal ID |
| Never ask for `zoomDownloadUrl` | Backend will never send it — backend use only |
| Always call `/play` fresh | URL expires in 3 hours — don't save it to localStorage/sessionStorage |
| No download button | Bunny.net token authentication blocks direct downloads — do not add one |
| Use `credentials: 'include'` | All recording API calls need the auth cookie |

---

## Environment Variables (Backend — for reference)

These are set in the backend `.env` file. Frontend does not need them.

```
ZOOM_WEBHOOK_SECRET=...     # from Zoom Marketplace → Event Subscriptions
BUNNY_LIBRARY_ID=635843
BUNNY_API_KEY=...
BUNNY_CDN_HOSTNAME=vz-xxxxxxxx.b-cdn.net
BUNNY_TOKEN_KEY=...         # from Bunny CDN Zone → Token Authentication
```

---

## Timing Expectations

| Event | Time |
|-------|------|
| Class ends | T+0 |
| Zoom processes recording | T+5 to T+15 min |
| Zoom webhook fires | T+5 to T+15 min |
| Bunny upload completes | T+15 to T+30 min |
| Recording appears as AVAILABLE | T+15 to T+30 min |

> Tell students: "Recordings are usually available within 30 minutes after class ends."

---

## Testing Checklist

- [ ] `GET /api/recordings/batch/{batchId}` — returns list for enrolled student
- [ ] `GET /api/recordings/batch/{batchId}` — returns `403` for non-enrolled student
- [ ] `GET /api/recordings/{id}/play` — returns signed URL
- [ ] Signed URL plays video in iframe
- [ ] Signed URL expires after 3 hours (try the same URL again)
- [ ] Empty list shown when no AVAILABLE recordings for batch
- [ ] Mentor can see recordings for their classes

---

*Backend: Yash | Frontend integration questions → share this doc*
