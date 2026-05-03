# Lead Management CRM — Frontend Integration Guide

> **Version:** v3 (Payment tracking, Webinars, WhatsApp sequences, Per-rep stats)
> **Roles with access:** `ADMIN` and `SALES` (unless noted otherwise)
> All endpoints require a valid `access_token` cookie (sent automatically by the browser), **except** public webinar endpoints.

---

## Table of Contents

1. [Roles & Access](#roles--access)
2. [Lead Object Reference](#lead-object-reference)
3. [Status Flow](#status-flow)
4. [Lead Endpoints](#lead-endpoints)
5. [Payment Endpoints](#payment-endpoints)
6. [Stats & Rep Performance](#stats--rep-performance)
7. [Webinar Endpoints](#webinar-endpoints)
8. [Enquiry Endpoints](#enquiry-endpoints)
9. [Google Sheet Sync](#google-sheet-sync)
10. [Enum Reference](#enum-reference)
11. [Error Handling](#error-handling)

---

## Roles & Access

| Endpoint | ADMIN | SALES | MENTOR | Public |
|----------|-------|-------|--------|--------|
| All `/api/sales/**` | ✅ | ✅ | ❌ | ❌ |
| `PUT /api/sales/leads/{id}/assign` | ✅ | ❌ | ❌ | ❌ |
| `GET /api/sales/stats/reps` | ✅ | ✅ | ❌ | ❌ |
| All `/api/admin/webinars/**` | ✅ | ❌ | ❌ | ❌ |
| `GET /api/sales/webinars/**` | ✅ | ✅ | ❌ | ❌ |
| `GET /api/public/webinars/**` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/public/webinars/{id}/register` | ✅ | ✅ | ✅ | ✅ |

> **Creating a SALES user:** Only ADMIN can create a user with `role: SALES` via `POST /api/admin/users`.

---

## Lead Object Reference

### LeadSummaryResponse (paginated list)

```json
{
  "id": "uuid",
  "name": "Rahul Sharma",
  "phone": "9876543210",
  "alternatePhone": "9123456789",
  "email": "rahul@gmail.com",
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
  "preferredTimings": "EVENING",
  "sheetCreatedAt": "2026-04-20T09:00:00",
  "leadAgeDays": 10,
  "demoType": null,
  "demoScheduledAt": null,
  "demoMentorName": null,
  "courseFee": null,
  "createdAt": "2026-04-27T09:00:00",
  "updatedAt": "2026-04-28T14:15:00"
}
```

- `preferredTimings` — `MORNING | AFTERNOON | EVENING | NIGHT | null`
- `alternatePhone` — set when lead responds on WhatsApp with a different number
- `leadAgeDays` — days since `sheetCreatedAt` (or `createdAt`). Badge ≥ 4 = aging, ≥ 8 = stale.
- `null` fields are omitted from the JSON response (`@JsonInclude(NON_NULL)`)

### LeadResponse (full lead detail)

Same as summary, plus:

```json
{
  "courseInterest": "F&O Trading",
  "notes": "Interested, needs EMI option",

  "demoMentorName": "Mentor Full Name",
  "demoScheduledAt": "2026-05-03T11:00:00",
  "demoConductedAt": "2026-05-03T11:45:00",
  "demoType": "ONLINE",

  "closingBlocker": "NEEDS_EMI",
  "closingComment": "Needs 3-month EMI plan",

  "courseFee": 15000.00,
  "totalPaid": 5000.00,
  "balance": 10000.00,
  "closedByName": "Sales Person Name",

  "activities": [
    {
      "id": "uuid",
      "activityType": "LEAD_IMPORTED",
      "description": "Lead synced from Google Sheet (FMT Ad Leads)",
      "createdByName": null,
      "createdAt": "2026-04-27T09:00:00"
    }
  ],

  "payments": [
    {
      "id": "uuid",
      "leadId": "uuid",
      "amount": 5000.00,
      "paymentType": "ADVANCE",
      "dueDate": null,
      "paidAt": "2026-05-01T10:00:00",
      "status": "PAID",
      "notes": "Paid via UPI",
      "recordedByName": "Sales Person Name",
      "createdAt": "2026-05-01T10:00:00"
    }
  ]
}
```

---

## Status Flow

```
NEW
 └─► DNP_1 → DNP_2 → DNP_3 → DNP_4 → DNP_5  (call-attempted endpoint)
                                       └─► WHATSAPP_SENT  (whatsapp-sent endpoint)
                                             └─► WHATSAPP_RESPONDED  (update-status)
                                                   └─► CONTACTED
                                                         ├─► FOLLOWUP_SCHEDULED
                                                         ├─► DEMO_BOOKED
                                                         │     ├─► DEMO_DONE → CLOSING → PAYMENT_DONE ✅
                                                         │     └─► DEMO_NO_SHOW
                                                         └─► NOT_INTERESTED ❌
 └─► SWITCH_OFF ❌  (unreachable at any point)
```

**Stage filter (for `GET /api/sales/leads?stage=ACTIVE|INACTIVE`):**
- `ACTIVE` = NEW, DNP_1–5, WHATSAPP_SENT, WHATSAPP_RESPONDED, CONTACTED, FOLLOWUP_SCHEDULED, DEMO_BOOKED, DEMO_DONE, DEMO_NO_SHOW, CLOSING
- `INACTIVE` = PAYMENT_DONE, NOT_INTERESTED, SWITCH_OFF

**UI tips:**
- Disable **Call** button when `dnpCount >= 5`
- Show **Send WhatsApp** only when `whatsappEligible = true && whatsappSent = false`
- When status → `DEMO_BOOKED`: show mentor picker, datetime picker, demo type (ONLINE/OFFLINE)
- When status → `DEMO_DONE`: show conducted-at datetime
- When status → `CLOSING`: show closing blocker dropdown + course fee field
- When status → `PAYMENT_DONE`: show course fee field — `closedBy` auto-set to current user
- When status → `WHATSAPP_RESPONDED`: show alternate phone field

---

## Lead Endpoints

### Import Leads from Excel

```
POST /api/sales/leads/import
Content-Type: multipart/form-data
```

**Request:** form field `file` = `.xlsx` file

**Response (200):**
```json
{
  "success": true,
  "message": "Import complete",
  "data": { "imported": 47, "skipped": 3, "errors": ["Row 5: invalid data"] }
}
```

---

### Add Single Lead

```
POST /api/sales/leads
Content-Type: application/json
```

```json
{
  "name": "Rahul Sharma",
  "phone": "9876543210",
  "email": "rahul@gmail.com",
  "courseInterest": "F&O Trading",
  "notes": "Referred by existing student"
}
```

**Response (201):** Full `LeadResponse`

---

### List Leads (Paginated)

```
GET /api/sales/leads?status=DEMO_BOOKED&stage=ACTIVE&assignedTo=uuid&search=rahul&page=0&size=20
```

| Param | Type | Description |
|-------|------|-------------|
| `status` | string | Filter by exact `LeadStatus` enum value |
| `stage` | string | `ACTIVE` or `INACTIVE` — broad bucket filter |
| `assignedTo` | UUID | Filter by assigned sales rep |
| `search` | string | Searches name + phone (case-insensitive) |
| `page` | int | 0-based page number (default: 0) |
| `size` | int | Page size (default: 20) |

> `status` takes priority over `stage` when both are provided.

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

### Get Lead Detail

```
GET /api/sales/leads/{id}
```

**Response (200):** Full `LeadResponse` with `activities` + `payments` arrays.

---

### Log Failed Call Attempt (DNP)

```
PUT /api/sales/leads/{id}/call-attempted
```

No request body. Auto-increments `dnpCount`, updates status to `DNP_1`–`DNP_5`, sets `whatsappEligible = true` after 5 DNPs.

---

### Mark WhatsApp Sent

```
PUT /api/sales/leads/{id}/whatsapp-sent
```

No body. Only allowed when `whatsappEligible = true`. Sets `whatsappSent = true`, status → `WHATSAPP_SENT`.

---

### Log WhatsApp Campaign Step

```
POST /api/sales/leads/{id}/whatsapp-step
```

```json
{
  "step": 2,
  "message": "Sent follow-up video about F&O course"
}
```

Logs a `WHATSAPP_SEQUENCE` activity entry. Use this to track which step of your WhatsApp campaign sequence you sent (Day 1 intro, Day 3 social proof, Day 7 offer, etc.).

---

### Update Lead Status

```
PUT /api/sales/leads/{id}/status
```

```json
{
  "status": "DEMO_BOOKED",
  "notes": "Confirmed for Saturday 11 AM",
  "followupDatetime": "2026-05-03T11:00:00",

  "demoMentorId": "uuid-of-mentor",
  "demoScheduledAt": "2026-05-03T11:00:00",
  "demoType": "ONLINE",

  "demoConductedAt": "2026-05-03T11:45:00",

  "closingBlocker": "NEEDS_EMI",
  "closingComment": "Wants 3-month EMI",

  "courseFee": 15000.00,

  "alternatePhone": "9123456789"
}
```

**Fields by status:**

| Status | Required extra fields |
|--------|-----------------------|
| `DEMO_BOOKED` | `demoMentorId`, `demoScheduledAt`, `demoType` |
| `DEMO_DONE` | `demoConductedAt` (defaults to now if omitted) |
| `CLOSING` | `closingBlocker`, `closingComment`, `courseFee` |
| `PAYMENT_DONE` | `courseFee` (closedBy auto-set to caller) |
| `WHATSAPP_RESPONDED` | `alternatePhone` |
| Others | Only `notes`, `followupDatetime` apply |

---

### Add Note

```
POST /api/sales/leads/{id}/note
```

```json
{ "note": "Called back, interested in offline batch" }
```

---

### Assign Lead (ADMIN only)

```
PUT /api/sales/leads/{id}/assign
```

```json
{ "assignedTo": "uuid-of-sales-user" }
```

---

### Get Mentor List (for demo booking dropdown)

```
GET /api/sales/mentors
```

Returns active users with role `MENTOR`. Use this to populate the demo mentor dropdown.

---

## Payment Endpoints

### Record a Payment

```
POST /api/sales/leads/{id}/payments
```

```json
{
  "amount": 5000.00,
  "paymentType": "ADVANCE",
  "dueDate": null,
  "notes": "Paid via UPI",
  "markPaidNow": true
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `amount` | ✅ | Payment amount |
| `paymentType` | ✅ | `ADVANCE`, `INSTALLMENT`, or `FULL_PAYMENT` |
| `dueDate` | ❌ | Date the payment is due (`YYYY-MM-DD`) — for pending installments |
| `notes` | ❌ | Any notes (UPI ref, bank name, etc.) |
| `markPaidNow` | ❌ | `true` = status PAID + paidAt = now. `false` (default) = PENDING |

**Response (201):** `LeadPaymentResponse`

---

### Get Payments for a Lead

```
GET /api/sales/leads/{id}/payments
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "payments": [ /* LeadPaymentResponse array */ ],
    "totalPaid": 5000.00,
    "courseFee": 15000.00,
    "balance": 10000.00
  }
}
```

- `balance` is `null` when `courseFee` is not set on the lead.

---

### Mark a Payment as Paid

```
PUT /api/sales/leads/{id}/payments/{paymentId}/mark-paid
```

No body. Marks a `PENDING` payment as `PAID` and sets `paidAt = now`.

**Response (200):** Updated `LeadPaymentResponse`

---

## Stats & Rep Performance

### Dashboard Stats

```
GET /api/sales/leads/stats
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "total": 320,
    "newLeads": 45,
    "dnp": 80,
    "whatsappSent": 30,
    "whatsappResponded": 12,
    "contacted": 55,
    "followupScheduled": 20,
    "demoBooked": 18,
    "demoDone": 14,
    "demoNoShow": 4,
    "closing": 8,
    "paymentDone": 22,
    "notInterested": 27,
    "switchOff": 5,
    "conversionRate": "6.9%",

    "overdueFollowups": 3,
    "dueSoonFollowups": 2,

    "agingLeads": 15,
    "staleLeads": 7,

    "demosDoneToday": 2,
    "demosDoneThisWeek": 9,
    "demosDoneThisMonth": 14,
    "demoBookedToday": 3,
    "demoBookedThisWeek": 11,
    "demoBookedThisMonth": 18,

    "salesDoneThisWeek": 4,
    "salesDoneThisMonth": 22
  }
}
```

**UI alerts to show:**
- `overdueFollowups > 0` → red badge "X follow-ups overdue"
- `dueSoonFollowups > 0` → orange badge "X follow-ups due in next 2 hrs"
- `agingLeads > 0` → yellow notice "X leads aging (4–7 days old)"
- `staleLeads > 0` → red notice "X leads stale (8+ days old)"

---

### Per-Rep Stats

```
GET /api/sales/stats/reps
```

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "userId": "uuid",
      "repName": "Arjun Kumar",
      "activeLeads": 48,
      "demosThisWeek": 5,
      "demosThisMonth": 12,
      "salesThisWeek": 2,
      "salesThisMonth": 6,
      "totalSales": 22
    }
  ]
}
```

Includes all SALES + ADMIN users. Use this for a leaderboard / team performance table.

---

## Webinar Endpoints

### Public — List Active Webinars

```
GET /api/public/webinars
```

No auth required. Returns webinars with `isActive: true`.

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "title": "Free Trading Masterclass",
      "description": "Learn F&O basics in 2 hours",
      "scheduledAt": "2026-05-10T11:00:00",
      "zoomLink": "https://zoom.us/j/...",
      "hostMentorName": "Mentor Name",
      "isActive": true,
      "maxCapacity": 200,
      "registrationCount": 87,
      "createdAt": "2026-05-01T09:00:00"
    }
  ]
}
```

---

### Public — Get Webinar by ID

```
GET /api/public/webinars/{id}
```

---

### Public — Register for a Webinar

```
POST /api/public/webinars/{id}/register
```

```json
{
  "name": "Rahul Sharma",
  "phone": "9876543210",
  "email": "rahul@gmail.com",
  "utmSource": "instagram",
  "utmMedium": "reel",
  "utmCampaign": "may-masterclass"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | ✅ | Registrant name |
| `phone` | ✅ | Mobile number |
| `email` | ❌ | Optional |
| `utmSource` | ❌ | Traffic source (e.g. `instagram`, `facebook`) |
| `utmMedium` | ❌ | Medium (e.g. `reel`, `story`, `post`) |
| `utmCampaign` | ❌ | Campaign name (e.g. `may-masterclass`) |

**Auto-creates a Lead** with `source: WEBINAR` if the phone number is new to the system. If the phone exists, the registration is linked to the existing lead.

**Errors:**
- `400` — already registered for this webinar
- `400` — webinar is full (if `maxCapacity` is set)
- `400` — webinar is inactive (registration closed)

---

### Admin — Create Webinar

```
POST /api/admin/webinars
```

```json
{
  "title": "Free Trading Masterclass",
  "description": "...",
  "scheduledAt": "2026-05-10T11:00:00",
  "zoomLink": "https://zoom.us/j/...",
  "hostMentorId": "uuid-of-mentor",
  "maxCapacity": 200
}
```

---

### Admin — Update Webinar

```
PUT /api/admin/webinars/{id}
```

Same body as create.

---

### Admin — Toggle Active/Inactive

```
PUT /api/admin/webinars/{id}/toggle-active
```

No body. Toggles `isActive`. Setting to `false` closes registrations.

---

### Admin — List All Webinars

```
GET /api/admin/webinars
```

Returns all webinars including inactive ones.

---

### Admin — Get Registrations for a Webinar

```
GET /api/admin/webinars/{id}/registrations
```

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "webinarId": "uuid",
      "webinarTitle": "Free Trading Masterclass",
      "leadId": "uuid-or-null",
      "name": "Rahul Sharma",
      "phone": "9876543210",
      "email": "rahul@gmail.com",
      "registeredAt": "2026-05-02T15:30:00",
      "attended": false,
      "utmSource": "instagram",
      "utmMedium": "reel",
      "utmCampaign": "may-masterclass"
    }
  ]
}
```

- `leadId` is `null` for registrations where phone already existed and was linked to existing lead — wait, actually `leadId` is always set since auto-create happens or existing lead is linked.

---

### Admin — Mark Attended

```
PUT /api/admin/webinars/{id}/registrations/{regId}/mark-attended
```

No body. Sets `attended: true` for the registration.

---

### Sales — View Webinars

```
GET /api/sales/webinars
GET /api/sales/webinars/{id}/registrations
```

Same responses as admin equivalents but accessible to SALES role.

---

## Enquiry Endpoints

```
GET  /api/sales/enquiries?status=NEW
GET  /api/sales/enquiries/{id}
PUT  /api/sales/enquiries/{id}/status
```

**Status values:** `NEW` → `CONTACTED` → `CLOSED`

---

## Google Sheet Sync

```
POST /api/sales/leads/sync-sheets     — manual trigger
GET  /api/sales/leads/sync-logs       — last 20 sync runs
```

**Columns read from Google Sheet:**

| Column name (case-insensitive) | Mapped to |
|-------------------------------|-----------|
| `full_name` / `name` | `name` |
| `phone_number` / `phone` / `mobile` | `phone` |
| `email` | `email` |
| `platform` / `source` / `lead source` | `source` (mapped to LeadSource enum) |
| `what_is_your_current_level…` / `current level` | `currentLevel` |
| `preferred_learning_mode` / `learning mode` | `preferredLearningMode` |
| `preferred_timings` / `preferred timing` / `timing` | `preferredTimings` (MORNING/AFTERNOON/EVENING/NIGHT) |
| `created_time` / `timestamp` / `date` | `sheetCreatedAt` |
| `comment` / `comments` | appended to `notes` |
| All columns named `status` | appended to `notes` as "[Sheet Status: …]" |

Phone numbers are normalised (strips `+91` / `91` prefix, strips non-numeric characters including `p:` format). Duplicate phones are skipped.

---

## Enum Reference

### LeadStatus
`NEW`, `DNP_1`, `DNP_2`, `DNP_3`, `DNP_4`, `DNP_5`, `WHATSAPP_SENT`, `WHATSAPP_RESPONDED`, `CONTACTED`, `FOLLOWUP_SCHEDULED`, `DEMO_BOOKED`, `DEMO_DONE`, `DEMO_NO_SHOW`, `CLOSING`, `PAYMENT_DONE`, `NOT_INTERESTED`, `SWITCH_OFF`

### LeadSource
`META_ADS`, `GOOGLE_ADS`, `ORGANIC`, `REFERRAL`, `WALK_IN`, `WEBINAR`, `OTHER`

### DemoType
`ONLINE`, `OFFLINE`

### PreferredTiming
`MORNING`, `AFTERNOON`, `EVENING`, `NIGHT`

### PaymentType
`ADVANCE`, `INSTALLMENT`, `FULL_PAYMENT`

### PaymentStatus
`PENDING`, `PAID`, `OVERDUE`

### ClosingBlocker
`PRICING_ISSUE`, `NEEDS_EMI`, `NEEDS_TIME`, `COMPARING_COMPETITOR`, `NEEDS_OFFLINE_DEMO`, `FAMILY_DECISION_PENDING`, `OTHER`

### ActivityType
`CALL_ATTEMPTED`, `WHATSAPP_SENT`, `WHATSAPP_SEQUENCE`, `STATUS_CHANGE`, `NOTE_ADDED`, `LEAD_IMPORTED`, `FOLLOWUP_SCHEDULED`, `PAYMENT_RECORDED`, `WEBINAR_REGISTERED`

---

## Error Handling

All error responses follow:
```json
{ "success": false, "message": "Human-readable error" }
```

| HTTP | Meaning |
|------|---------|
| 400 | Validation failed or business rule violation |
| 401 | Not authenticated (no/expired access_token cookie) |
| 403 | Authenticated but wrong role |
| 404 | Resource not found |

> Make sure your API client uses `credentials: 'include'` (fetch) or `withCredentials: true` (axios) — without this, cookies are not sent and every request returns 401.
