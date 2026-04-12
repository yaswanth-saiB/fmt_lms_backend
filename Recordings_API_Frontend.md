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
    "durationMins": 120
  },
  {
    "id": "uuid",
    "title": "Class 11 - Chart Patterns",
    "recordedDate": "2026-04-07",
    "durationMins": 90
  }
]
```

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
  "playUrl": "https://vz-xxxxxxxx.b-cdn.net/<videoId>/play?token=abc123&expires=1713000000",
  "expiresIn": 10800
}
```

| Field | Type | Description |
|-------|------|-------------|
| `playUrl` | `string` | Signed Bunny.net Stream URL — valid for 3 hours |
| `expiresIn` | `number` | Seconds until the URL expires (always 10800 = 3h) |

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

Use Bunny.net's built-in iframe player. The `playUrl` is the direct URL:

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

Or use it with a custom player (Video.js, Plyr, etc.) as a direct `.m3u8` HLS source:

```js
// Replace /play with /playlist.m3u8 for HLS
const hlsUrl = playUrl.replace('/play', '/playlist.m3u8');
```

> Bunny.net also supports direct MP4 streaming — the `/play` URL handles both HLS and direct playback automatically.

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

> Mentor response includes `status` so they can see if processing is still in progress.  
> Mentor does **not** get `bunnyVideoId` or `playUrl` — those are student-only.

---

### 4. `GET /api/mentor/batches/{batchId}/recordings`

List recordings for a specific batch (mentor view).

**Auth:** Required  
**Role:** MENTOR

**Response:** Same shape as mentor list above.

---

## Admin Endpoints

---

### 5. `GET /api/admin/recordings`

List all recordings across all batches (existing endpoint — already documented).

**Auth:** Required  
**Role:** ADMIN

**Response:** Same shape as mentor, all batches.

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
