# Enquiry Module — Frontend Integration Guide

**Base URL (dev):** `http://localhost:8080`
**Base URL (prod):** `https://api.firstmilliontrade.com`
**Last updated:** 2026-03-23

---

## Overview

Enquiries are submitted by public visitors via the website form. Both **ADMIN** and **MENTOR** can view and manage enquiries through their respective dashboards.

| Who | Endpoints |
|-----|-----------|
| ADMIN | `/api/admin/enquiries/**` |
| MENTOR | `/api/mentor/enquiries/**` |

Both expose identical functionality — list, get by ID, and update status.

---

## Enquiry Status Lifecycle

```
NEW  →  CONTACTED  →  CLOSED
```

| Status | Meaning |
|--------|---------|
| `NEW` | Just submitted — not yet actioned |
| `CONTACTED` | Team has reached out to the person |
| `CLOSED` | Fully handled (converted, not interested, etc.) |

---

## Enquiry Response Shape

All enquiry endpoints return this object:

```json
{
  "id":               "uuid",
  "name":             "Raj Kumar",
  "mobile":           "9876543210",
  "city":             "Hyderabad",
  "experienceLevel":  "BEGINNER",
  "areaOfInterest":   "Stock Market",
  "message":          "I want to learn trading from scratch",
  "status":           "NEW",
  "createdAt":        "2026-03-23T10:30:00"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | string (UUID) | Use this for update calls |
| `name` | string | Submitted name |
| `mobile` | string | Contact number |
| `city` | string / null | Optional — may be null |
| `experienceLevel` | string / null | `BEGINNER` / `INTERMEDIATE` / `ADVANCED` — may be null |
| `areaOfInterest` | string / null | Free text — may be null |
| `message` | string / null | Optional message — may be null |
| `status` | string | `NEW` / `CONTACTED` / `CLOSED` |
| `createdAt` | string (ISO datetime) | When the enquiry was submitted |

---

## Endpoints — Admin

### `GET /api/admin/enquiries` `PROTECTED · ADMIN`

List all enquiries, newest first. Optionally filter by status.

**Query Param (optional):** `?status=NEW` or `?status=CONTACTED` or `?status=CLOSED`

**Response `200`:**
```json
{
  "success": true,
  "message": "Enquiries fetched",
  "data": [
    {
      "id": "uuid",
      "name": "Raj Kumar",
      "mobile": "9876543210",
      "city": "Hyderabad",
      "experienceLevel": "BEGINNER",
      "areaOfInterest": "Stock Market",
      "message": "I want to learn trading",
      "status": "NEW",
      "createdAt": "2026-03-23T10:30:00"
    }
  ]
}
```

**Usage:**
```js
// All enquiries
const res = await api.get("/api/admin/enquiries");

// Only NEW (unactioned) leads
const res = await api.get("/api/admin/enquiries?status=NEW");

// Already contacted
const res = await api.get("/api/admin/enquiries?status=CONTACTED");
```

---

### `GET /api/admin/enquiries/{enquiryId}` `PROTECTED · ADMIN`

Get a single enquiry by ID.

**Path Param:** `enquiryId` — UUID from the list

**Response `200`:** Single enquiry object (same shape as above).

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Enquiry not found"` | Invalid enquiryId |

---

### `PUT /api/admin/enquiries/{enquiryId}/status` `PROTECTED · ADMIN`

Update the status of an enquiry — e.g. mark as CONTACTED after calling the lead.

**Path Param:** `enquiryId` — UUID

**Request Body:**
```json
{ "status": "CONTACTED" }
```

| Value | When to use |
|-------|-------------|
| `NEW` | Reset to unactioned (rare) |
| `CONTACTED` | After calling / messaging the lead |
| `CLOSED` | Fully handled — converted or dropped |

**Response `200`:** Updated enquiry object.

**Error Responses:**

| Message | Cause |
|---------|-------|
| `"Enquiry not found"` | Invalid enquiryId |
| `"Status is required"` | Missing body |

**Usage:**
```js
const markContacted = async (enquiryId) => {
  const res = await api.put(`/api/admin/enquiries/${enquiryId}/status`, {
    status: "CONTACTED"
  });
  if (res.data.success) {
    // Update the row in UI without refetching the full list
    setEnquiries(prev =>
      prev.map(e => e.id === enquiryId ? res.data.data : e)
    );
  }
};
```

---

## Endpoints — Mentor

Identical to admin endpoints, just under `/api/mentor/`:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/mentor/enquiries` | List all enquiries (optional `?status=`) |
| GET | `/api/mentor/enquiries/{enquiryId}` | Get single enquiry |
| PUT | `/api/mentor/enquiries/{enquiryId}/status` | Update status |

Same request/response shapes — only the path and required role differ.

---

## Suggested UI — Enquiries Tab

### List page

```
Enquiries                          [ NEW ] [ CONTACTED ] [ CLOSED ] [ ALL ]

┌────────────────────────────────────────────────────────────────────────┐
│ Name          Mobile         City       Experience   Status   Date     │
│ Raj Kumar     9876543210     Hyderabad  BEGINNER     NEW      23 Mar   │
│ Priya S       9123456789     Mumbai     INTERMEDIATE CONTACTED 22 Mar  │
└────────────────────────────────────────────────────────────────────────┘
```

- Status filter buttons at the top → call `?status=NEW` etc.
- Click a row → open detail panel / modal
- Quick action buttons: **Mark Contacted** | **Close**

### Status badge colors (suggested)

| Status | Color |
|--------|-------|
| `NEW` | Blue |
| `CONTACTED` | Yellow / Amber |
| `CLOSED` | Green |

### Quick status update (inline, no modal needed)

```js
// Inline action button on the row
const updateStatus = async (enquiryId, status) => {
  const res = await api.put(`/api/admin/enquiries/${enquiryId}/status`, { status });
  if (res.data.success) {
    setEnquiries(prev => prev.map(e => e.id === enquiryId ? res.data.data : e));
    toast.success(`Marked as ${status}`);
  }
};
```

---

## Error Handling

| HTTP Status | Meaning | Action |
|-------------|---------|--------|
| `200` | Success | Use `response.data.data` |
| `400` | Validation error | Show `response.data.message` |
| `401` | Not logged in | Redirect to `/login` |
| `403` | Wrong role | Redirect to `/unauthorized` |
| `500` | Server error | Show generic error message |

---

## Quick Reference — All Endpoints

### Admin (`/api/admin/**`) — ADMIN role

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/enquiries` | List all (optional `?status=`) |
| GET | `/api/admin/enquiries/{id}` | Get by ID |
| PUT | `/api/admin/enquiries/{id}/status` | Update status |

### Mentor (`/api/mentor/**`) — MENTOR role

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/mentor/enquiries` | List all (optional `?status=`) |
| GET | `/api/mentor/enquiries/{id}` | Get by ID |
| PUT | `/api/mentor/enquiries/{id}/status` | Update status |
