# Reviews Feature — Frontend Integration Guide

## Overview

Admin manages Google reviews through the LMS. The homepage fetches them from the backend and displays them. No Google API involved — all data is stored in our PostgreSQL DB.

**Base URL (production):** `https://api.firstmilliontrade.com`  
**Base URL (local dev):** `http://localhost:8080`

---

## Public Endpoint — Homepage Reviews

### `GET /api/reviews`

**Auth:** Not required — fully public.  
**Cache:** Response is cached server-side for 12 hours. No extra work needed on frontend — just call it normally.

**Request:**
```
GET /api/reviews
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Reviews fetched",
  "data": [
    {
      "id": "uuid",
      "reviewerName": "Ravi Kumar",
      "rating": 5,
      "reviewText": "Excellent course! Learned a lot about technical analysis.",
      "reviewDate": "April 2024",
      "photoUrl": "https://lh3.googleusercontent.com/a/...",
      "isActive": true,
      "displayOrder": 0,
      "createdAt": "2026-04-24T10:30:00"
    },
    {
      "id": "uuid",
      "reviewerName": "Priya Sharma",
      "rating": 5,
      "reviewText": "Best trading mentor in Hyderabad.",
      "reviewDate": "March 2024",
      "photoUrl": null,
      "isActive": true,
      "displayOrder": 1,
      "createdAt": "2026-04-24T10:35:00"
    }
  ]
}
```

**Notes:**
- `displayOrder` — lower number = shown first. Already sorted server-side; render in the order received.
- `photoUrl` — can be `null`. Show a default avatar/placeholder when null.
- `reviewDate` — plain string (e.g. `"April 2024"`, `"2 months ago"`). Render as-is.
- `rating` — integer 1–5. Render as star icons.
- List only contains active reviews. No filtering needed on frontend.

**Usage (React example):**
```js
const res = await fetch('https://api.firstmilliontrade.com/api/reviews');
const { data } = await res.json();
// data is the array of reviews, already sorted by displayOrder ASC
```

---

## Admin Endpoints — Reviews Management

All admin endpoints require a valid JWT. Include the access token as a cookie (sent automatically if using `credentials: 'include'`) or as a Bearer token header.

**Headers for all admin requests:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

Or if using cookie auth:
```js
fetch(url, { credentials: 'include' })
```

---

### 1. List All Reviews (including hidden)

### `GET /api/admin/reviews`

**Auth:** ADMIN role required.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Reviews fetched",
  "data": [
    {
      "id": "uuid",
      "reviewerName": "Ravi Kumar",
      "rating": 5,
      "reviewText": "Excellent course!",
      "reviewDate": "April 2024",
      "photoUrl": "https://...",
      "isActive": true,
      "displayOrder": 0,
      "createdAt": "2026-04-24T10:30:00"
    },
    {
      "id": "uuid",
      "reviewerName": "Hidden User",
      "rating": 3,
      "reviewText": "Average experience.",
      "reviewDate": "January 2024",
      "photoUrl": null,
      "isActive": false,
      "displayOrder": 5,
      "createdAt": "2026-01-15T08:00:00"
    }
  ]
}
```

**Notes:**
- Returns all reviews including `isActive: false` (hidden/soft-deleted).
- Show inactive reviews in the admin panel with a "Hidden" badge so admin can restore them.

---

### 2. Get Single Review

### `GET /api/admin/reviews/{id}`

Used to pre-fill the edit form.

**Response `200 OK`:** Same shape as a single item from the list above.

**Error `404`:**
```json
{ "success": false, "message": "Review not found" }
```

---

### 3. Add a New Review

### `POST /api/admin/reviews`

**Request body:**
```json
{
  "reviewerName": "Ravi Kumar",
  "rating": 5,
  "reviewText": "Excellent course! Learned a lot about technical analysis in just 3 months.",
  "reviewDate": "April 2024",
  "photoUrl": "https://lh3.googleusercontent.com/a/...",
  "displayOrder": 0
}
```

**Field rules:**
| Field | Required | Notes |
|---|---|---|
| `reviewerName` | Yes | Max 100 characters |
| `rating` | Yes | Integer 1–5 |
| `reviewText` | Yes | No max length |
| `reviewDate` | No | String, e.g. `"April 2024"` or `"2 months ago"` |
| `photoUrl` | No | Paste Google profile photo URL. Max 500 chars. Pass `null` or omit if no photo. |
| `displayOrder` | No | Defaults to `0` if omitted. Lower = shown first on homepage. |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Review added",
  "data": { /* full ReviewResponse */ }
}
```

**Errors:**
```json
{ "success": false, "message": "Validation failed. Please check your input.",
  "data": { "reviewerName": "Reviewer name is required", "rating": "Rating is required" } }
```

**After success:** Homepage cache is immediately cleared. Next homepage load will show the new review.

---

### 4. Update a Review

### `PUT /api/admin/reviews/{id}`

Same body as POST. All fields are re-applied — send the full object, not just changed fields.

**To hide a review (soft delete via update):**
```json
{ ..., "isActive": false }
```

**To restore a hidden review:**
```json
{ ..., "isActive": true }
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Review updated",
  "data": { /* full ReviewResponse */ }
}
```

---

### 5. Soft Delete (Hide) a Review

### `DELETE /api/admin/reviews/{id}`

Sets `is_active = false`. Review disappears from homepage immediately.  
Data is not deleted from DB — admin can restore it via `PUT` with `isActive: true`.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Review hidden from homepage",
  "data": null
}
```

---

### 6. Reorder Reviews

### `PUT /api/admin/reviews/reorder`

Send the full list with updated `displayOrder` values after drag-and-drop. Lower number = shown first on homepage.

**Request body:**
```json
[
  { "id": "uuid-of-review-1", "displayOrder": 0 },
  { "id": "uuid-of-review-2", "displayOrder": 1 },
  { "id": "uuid-of-review-3", "displayOrder": 2 }
]
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Reviews reordered",
  "data": null
}
```

**Suggested implementation:** Use a drag-and-drop library. On drop, reassign `displayOrder` as the index (0, 1, 2...) and call this endpoint with the full updated list.

---

## Admin Panel UI — Suggested Flow

```
Reviews Management page
├── [+ Add Review] button → opens modal/form
│     Fields: Reviewer Name, Rating (1–5 stars), Review Text,
│             Review Date (text), Photo URL (paste link)
│     [Save] → POST /api/admin/reviews
│
├── Review list (drag-to-reorder)
│     Each row shows: photo, name, rating, excerpt, date, status badge
│     Actions per row:
│       [Edit]   → GET /api/admin/reviews/{id} → pre-fill form → PUT
│       [Hide]   → DELETE /api/admin/reviews/{id}  (if isActive=true)
│       [Restore]→ PUT /api/admin/reviews/{id} with isActive=true (if isActive=false)
│
└── [Save Order] button → PUT /api/admin/reviews/reorder
```

---

## Homepage UI — Suggested Display

```
Reviews / Testimonials section
├── Render reviews in the order received (displayOrder already applied server-side)
├── Each card:
│     - Profile photo (or default avatar if photoUrl is null)
│     - Star rating (filled stars based on rating 1–5)
│     - Review text
│     - Reviewer name + date
│     - Google logo (to indicate these are real Google reviews)
```

---

## Cache Behaviour — What Frontend Should Know

- The public `GET /api/reviews` response is **cached server-side for 12 hours**.
- Frontend does **not** need to cache it — just call it fresh on every page load.
- When admin adds/edits/deletes/reorders a review, cache is **instantly cleared**.
  The very next homepage request will get fresh data from the DB.
- There is no stale-data problem from the frontend's perspective.

---

## Error Response Shape (all endpoints)

```json
{
  "success": false,
  "message": "Error description here",
  "data": null
}
```

Validation errors include a `data` object with field-level messages:
```json
{
  "success": false,
  "message": "Validation failed. Please check your input.",
  "data": {
    "rating": "Rating must be at least 1",
    "reviewerName": "Reviewer name is required"
  }
}
```

| HTTP Status | Meaning |
|---|---|
| `200` | Success |
| `400` | Validation error or bad request |
| `401` | Not authenticated (missing/expired token) |
| `403` | Wrong role (not ADMIN) |
| `404` | Review not found |
| `500` | Unexpected server error |
