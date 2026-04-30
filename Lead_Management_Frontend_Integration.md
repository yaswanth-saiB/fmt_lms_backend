# Lead Management CRM — Frontend Integration Guide

> **Roles that can access this module:** `ADMIN` and `SALES`
> **Mentors** do NOT have access to any `/api/sales/**` endpoint.
> All endpoints require a valid `access_token` cookie (sent automatically by the browser).

---

## Table of Contents
1. [Roles & Access](#roles--access)
2. [Lead Object Reference](#lead-object-reference)
3. [Status Flow](#status-flow)
4. [Endpoints](#endpoints)
   - [Import from Excel](#1-import-leads-from-excel)
   - [Add Single Lead](#2-add-single-lead)
   - [List Leads (Paginated)](#3-list-leads-paginated)
   - [Get Lead Detail](#4-get-lead-detail-with-activity-history)
   - [Log Failed Call (DNP)](#5-log-failed-call-attempt-dnp)
   - [Mark WhatsApp Sent](#6-mark-whatsapp-sent)
   - [Update Status](#7-update-lead-status)
   - [Add Note](#8-add-note)
   - [Dashboard Stats](#9-dashboard-stats)
   - [Assign Lead](#10-assign-lead--admin-only)
   - [Enquiries](#11-enquiries)
5. [Excel Import Column Guide](#excel-import-column-guide)
6. [Enum Reference](#enum-reference)
7. [Error Handling](#error-handling)

---

## Roles & Access

| Endpoint | ADMIN | SALES | MENTOR |
|----------|-------|-------|--------|
| All `/api/sales/**` | ✅ | ✅ | ❌ |
| `PUT /api/sales/leads/{id}/assign` | ✅ | ❌ | ❌ |

> **Creating a SALES user:** Only ADMIN can create a user with `role: SALES` via `POST /api/admin/users`.

---

## Lead Object Reference

### LeadSummaryResponse (used in paginated list)

```json
{
  "id": "uuid",
  "name": "Rahul Sharma",
  "phone": "9876543210",
  "email": "rahul@gmail.com",
  "courseInterest": "F&O Trading",
  "source": "META_ADS",
  "status": "DNP_2",
  "dnpCount": 2,
  "whatsappEligible": false,
  "whatsappSent": false,
  "followupDatetime": null,
  "lastCallAt": "2026-04-28T14:15:00",
  "daysSinceLastCall": 2,
  "assignedToName": "Sales Person Name",
  "currentLevel": "Beginner",
  "preferredLearningMode": "Online",
  "createdAt": "2026-04-27T09:00:00",
  "updatedAt": "2026-04-28T14:15:00"
}
```

### LeadResponse (used in single lead detail)

Same as `LeadSummaryResponse` plus:
```json
{
  "notes": "Interested, needs EMI option",
  "activities": [
    {
      "id": "uuid",
      "activityType": "LEAD_IMPORTED",
      "description": "Lead imported from Excel",
      "createdByName": "Admin FMT",
      "createdAt": "2026-04-27T09:00:00"
    }
  ]
}
```

---

## Status Flow

```
NEW
 └─► DNP_1 → DNP_2 → DNP_3 → DNP_4 → DNP_5  (via call-attempted)
                                         └─► WHATSAPP_SENT  (via whatsapp-sent)
                                               └─► CONTACTED
                                                     ├─► FOLLOWUP_SCHEDULED
                                                     ├─► DEMO_BOOKED
                                                     │      └─► DEMO_DONE
                                                     │             └─► CLOSING
                                                     │                   └─► PAYMENT_DONE
                                                     └─► NOT_INTERESTED
 └─► SWITCH_OFF  (at any point — number unreachable)
```

**UI suggestion:**
- Show a **"Call" button** that triggers `call-attempted` — disable it when `dnpCount >= 5`
- Show a **"Send WhatsApp" button** only when `whatsappEligible = true` and `whatsappSent = false`
- After 5 DNPs, the status automatically becomes `DNP_5` and `whatsappEligible` flips to `true`

---

## Endpoints

### 1. Import Leads from Excel

```
POST /api/sales/leads/import
Content-Type: multipart/form-data
```

**Request:** Form field `file` = `.xlsx` file

**Response:**
```json
{
  "success": true,
  "message": "Import complete",
  "data": {
    "imported": 47,
    "skipped": 3,
    "errors": ["Row 5: invalid data"]
  }
}
```

**Notes:**
- Duplicate phone numbers are silently skipped (counted in `skipped`)
- Rows with empty phone are skipped
- Errors list contains row-specific messages for truly invalid data
- All imported leads get status `NEW` and create a `LEAD_IMPORTED` activity

---

### 2. Add Single Lead

```
POST /api/sales/leads
Content-Type: application/json
```

**Request body:**
```json
{
  "name": "Rahul Sharma",
  "phone": "9876543210",
  "email": "rahul@gmail.com",
  "courseInterest": "F&O Trading",
  "notes": "Referred by existing student"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | ✅ | Full name |
| `phone` | ✅ | Mobile number (must be unique) |
| `email` | ❌ | Email address |
| `courseInterest` | ❌ | e.g. "F&O Trading", "Options" |
| `notes` | ❌ | Any initial notes |

**Response (201):** Full `LeadResponse`

**Error (400):**
```json
{ "success": false, "message": "A lead with this phone number already exists" }
```

---

### 3. List Leads (Paginated)

```
GET /api/sales/leads?status=DNP_1&assignedTo=uuid&search=rahul&page=0&size=20
```

| Param | Type | Description |
|-------|------|-------------|
| `status` | String (optional) | Filter by LeadStatus enum value |
| `assignedTo` | UUID (optional) | Filter by assigned sales person |
| `search` | String (optional) | Search in name and phone |
| `page` | int (default: 0) | Page number |
| `size` | int (default: 20) | Page size |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "content": [ /* array of LeadSummaryResponse */ ],
    "totalElements": 150,
    "totalPages": 8,
    "currentPage": 0
  }
}
```

---

### 4. Get Lead Detail with Activity History

```
GET /api/sales/leads/{id}
```

**Response (200):** Full `LeadResponse` with `activities` array

**Response (404):**
```json
{ "success": false, "message": "Lead not found" }
```

---

### 5. Log Failed Call Attempt (DNP)

```
PUT /api/sales/leads/{id}/call-attempted
```

No request body needed.

**Logic:**
- Increments `dnpCount` by 1
- Sets status to `DNP_{count}` (e.g. `DNP_3`)
- Sets `lastCallAt` to now
- When `dnpCount` reaches 5: sets `whatsappEligible = true`

**Response (200):** Updated `LeadResponse`

**Error (400) — already at 5 DNPs:**
```json
{ "success": false, "message": "Maximum call attempts reached. Send WhatsApp." }
```

---

### 6. Mark WhatsApp Sent

```
PUT /api/sales/leads/{id}/whatsapp-sent
```

No request body needed.

**Requires:** `whatsappEligible = true` (i.e. 5 DNPs completed)

**Logic:**
- Sets `whatsappSent = true`
- Sets status to `WHATSAPP_SENT`

**Response (200):** Updated `LeadResponse`

**Error (400):**
```json
{ "success": false, "message": "Lead is not eligible for WhatsApp yet. Complete 5 call attempts first." }
```

---

### 7. Update Lead Status

```
PUT /api/sales/leads/{id}/status
Content-Type: application/json
```

**Request body:**
```json
{
  "status": "CONTACTED",
  "notes": "Spoke to lead, interested in F&O batch",
  "followupDatetime": "2026-05-01T15:00:00"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `status` | ✅ | Any valid `LeadStatus` value |
| `notes` | ❌ | Updates the lead's notes field |
| `followupDatetime` | ❌ | ISO-8601 datetime for follow-up reminder |

**Response (200):** Updated `LeadResponse`

---

### 8. Add Note

```
POST /api/sales/leads/{id}/note
Content-Type: application/json
```

**Request body:**
```json
{ "note": "Lead asked about EMI options, follow up next week" }
```

**Response (200):** Updated `LeadResponse`

> **Note:** This replaces the current `notes` field on the lead and also creates a `NOTE_ADDED` activity entry, so the full history is preserved in the activity log.

---

### 9. Dashboard Stats

```
GET /api/sales/leads/stats
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "total": 200,
    "newLeads": 45,
    "dnp": 32,
    "whatsappSent": 12,
    "contacted": 56,
    "followupScheduled": 23,
    "demoBooked": 15,
    "demoDone": 10,
    "closing": 5,
    "paymentDone": 8,
    "notInterested": 14,
    "switchOff": 3,
    "conversionRate": "4.0%",
    "demosDoneToday": 2,
    "demosDoneThisWeek": 8,
    "demosDoneThisMonth": 25,
    "demoBookedToday": 3,
    "demoBookedThisWeek": 10,
    "demoBookedThisMonth": 30
  }
}
```

**Field notes:**
- `dnp` — sum of all leads in DNP_1 through DNP_5
- `conversionRate` — `paymentDone / total * 100`, formatted as "X.X%"
- `demosDoneToday/ThisWeek/ThisMonth` — counts from the activity log timestamp (accurate even if a lead's status was later changed)
- `demoBookedToday/ThisWeek/ThisMonth` — same, for DEMO_BOOKED transitions

---

### 10. Assign Lead *(ADMIN only)*

```
PUT /api/sales/leads/{id}/assign
Content-Type: application/json
```

**Request body:**
```json
{ "assignedTo": "uuid-of-sales-user" }
```

**Response (200):** Updated `LeadResponse`

**Error (400):**
```json
{ "success": false, "message": "Can only assign leads to SALES or ADMIN users" }
```

**Error (400):**
```json
{ "success": false, "message": "Assigned user not found" }
```

> A SALES user calling this endpoint will get **403 Forbidden** — the backend enforces this in SecurityConfig.

---

---

### 11. Enquiries

> Enquiry data comes from the public contact form on the website. ADMIN and SALES can view and manage them. Mentors no longer have access via the frontend.

#### List Enquiries

```
GET /api/sales/enquiries?status=NEW
```

| Param | Values | Default |
|-------|--------|---------|
| `status` | `NEW`, `CONTACTED`, `CLOSED` | all |

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Arjun Mehta",
      "mobile": "9876543210",
      "city": "Hyderabad",
      "experienceLevel": "BEGINNER",
      "areaOfInterest": "F&O Trading",
      "message": "Interested in joining the next batch",
      "status": "NEW",
      "createdAt": "2026-04-28T10:30:00"
    }
  ]
}
```

#### Get Single Enquiry

```
GET /api/sales/enquiries/{id}
```

**Response (200):** Same object as above

**Response (404):** `{ "success": false, "message": "Enquiry not found" }`

#### Update Enquiry Status

```
PUT /api/sales/enquiries/{id}/status
Content-Type: application/json
```

**Request body:**
```json
{ "status": "CONTACTED" }
```

Valid values: `NEW` → `CONTACTED` → `CLOSED`

**Response (200):** Updated enquiry object

---

> **Note for Admin:** Enquiries are also available under `GET /api/admin/enquiries` (existing endpoint — no change).

---

## Excel Import Column Guide

The import endpoint accepts `.xlsx` files with these column headers (case-insensitive, flexible naming):

| Our Field | Accepted Column Headers |
|-----------|------------------------|
| `name` | `full_name`, `name`, `full name`, `customer name` |
| `phone` | `phone_number`, `phone`, `mobile`, `contact`, `mobile number` |
| `email` | `email`, `email address`, `email id` |
| `source` | `platform`, `source`, `lead source`, `channel` |
| `currentLevel` | `what_is_your_current_level_in_stock_market?`, `current level`, `level`, `experience` |
| `preferredLearningMode` | `preferred_learning_mode`, `learning mode`, `mode`, `preferred mode` |
| `courseInterest` | `course_interest`, `course`, `interest` |
| `notes` | `comment`, `comments`, `notes`, `remark`, `remarks` |

**Platform → Source mapping:**

| Excel platform value | Stored as |
|---------------------|-----------|
| meta / facebook / fb / instagram / ig | `META_ADS` |
| google | `GOOGLE_ADS` |
| organic / seo | `ORGANIC` |
| referral / refer | `REFERRAL` |
| walk | `WALK_IN` |
| anything else | `OTHER` |
| blank | `META_ADS` (default) |

---

## Enum Reference

### LeadStatus

| Value | Meaning |
|-------|---------|
| `NEW` | Just imported, no contact yet |
| `DNP_1` to `DNP_5` | Did Not Pick — call attempt number |
| `WHATSAPP_SENT` | WhatsApp sent after 5 DNPs |
| `CONTACTED` | Successfully spoke to the lead |
| `FOLLOWUP_SCHEDULED` | Follow-up call/meeting scheduled |
| `DEMO_BOOKED` | Demo session booked |
| `DEMO_DONE` | Demo completed |
| `CLOSING` | Negotiation/closing stage |
| `PAYMENT_DONE` | Converted — payment received |
| `NOT_INTERESTED` | Lead declined |
| `SWITCH_OFF` | Number unreachable / switched off |

### ActivityType

| Value | When created |
|-------|-------------|
| `LEAD_IMPORTED` | Lead added via Excel or manual form |
| `CALL_ATTEMPTED` | `call-attempted` endpoint called |
| `WHATSAPP_SENT` | `whatsapp-sent` endpoint called |
| `STATUS_CHANGE` | `status` endpoint called |
| `NOTE_ADDED` | `note` endpoint called |
| `FOLLOWUP_SCHEDULED` | Status updated to `FOLLOWUP_SCHEDULED` |

### LeadSource

`META_ADS` | `GOOGLE_ADS` | `ORGANIC` | `REFERRAL` | `WALK_IN` | `OTHER`

---

## Error Handling

All responses follow the same wrapper:
```json
{
  "success": true | false,
  "message": "Human-readable message",
  "timestamp": "2026-04-30T10:00:00",
  "data": { ... }
}
```

| HTTP Status | Meaning |
|-------------|---------|
| 200 | Success |
| 201 | Created (new lead) |
| 400 | Validation error or business rule violation |
| 401 | Not authenticated — check cookie |
| 403 | Authenticated but wrong role |
| 404 | Resource not found |

**Common 400 errors:**

| Endpoint | Error message |
|----------|--------------|
| `POST /leads` | "A lead with this phone number already exists" |
| `PUT /{id}/call-attempted` | "Maximum call attempts reached. Send WhatsApp." |
| `PUT /{id}/whatsapp-sent` | "Lead is not eligible for WhatsApp yet. Complete 5 call attempts first." |
| `PUT /{id}/assign` | "Can only assign leads to SALES or ADMIN users" |
