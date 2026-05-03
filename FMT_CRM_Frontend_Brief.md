# FMT Sales CRM — Frontend Brief

**To:** Frontend Team
**From:** Backend Team (Yash)
**Date:** May 2026
**Attachments:** `Lead_Management_Frontend_Integration.md` ← full API reference

---

## What We're Building

We've built a full **mini-CRM** into the FMT platform for the internal sales team. Think of it as a lightweight LeadSquared or Zoho Bigin — purpose-built for a small team that sells trading courses.

The sales team uses this every single day. Every call they make, every demo they book, every payment they collect — it all lives here. **This is the most-used internal tool we have.** It needs to feel fast, clear, and satisfying to use.

We need you to build the UI for this. Please treat it with the same care and craft you'd give a flagship product page. The sales team is non-technical — if the UI is confusing, they won't use it properly and leads will fall through the cracks.

---

## Who Uses This

- **SALES role** — 2–3 people. On the phone all day. Need to log calls, update statuses, and see their pipeline at a glance. They're often on a laptop, sometimes mobile.
- **ADMIN role** — sees everything, assigns leads, manages webinars, views team performance.

---

## The 8 Things to Build

### 1. Dashboard / Stats Page
The homepage for the CRM. Shows the full funnel at a glance.

**Must include:**
- Status count cards (NEW, DNP total, WHATSAPP_SENT, CONTACTED, DEMO_BOOKED, DEMO_DONE, CLOSING, PAYMENT_DONE, NOT_INTERESTED)
- Conversion rate (e.g. "6.9% conversion")
- **Alert banner for overdue follow-ups** — red, prominent. If `overdueFollowups > 0`, this must be impossible to miss.
- **Alert for follow-ups due in next 2 hours** — orange badge
- Aging leads count (4–7 days old) — amber warning
- Stale leads count (8+ days old) — red warning
- Demo activity cards: Demos Done Today / This Week / This Month
- Sales cards: Sales This Week / This Month

**Design note:** Make the status cards scannable. Use a funnel shape or horizontal pipeline flow. The overdue follow-up alert should feel urgent — not just a small number badge, a real alert.

---

### 2. Leads List Page

A fast, filterable table of all leads. The most-used page.

**Filters (all in one row, not buried in a sidebar):**
- Stage dropdown: **All / Active / Inactive**
- Status dropdown: all 17 status values
- Assigned to: dropdown of SALES users
- Search bar: searches name + phone

**Table columns:**
- Name + phone (stacked, clickable → lead detail)
- Status badge (colour-coded per status group — see below)
- Lead age (e.g. "8 days" — show in red if stale, amber if aging)
- Last call (e.g. "2 days ago")
- Next follow-up (show in red if overdue)
- Assigned to
- Preferred timing badge (MORNING / AFTERNOON / EVENING / NIGHT)

**Status colour guide (suggestion):**
| Group | Colour |
|-------|--------|
| NEW | Blue |
| DNP_1–5 | Orange |
| WHATSAPP_SENT / RESPONDED | Purple |
| CONTACTED / FOLLOWUP | Teal |
| DEMO_BOOKED / DONE | Indigo |
| DEMO_NO_SHOW | Amber |
| CLOSING | Yellow |
| PAYMENT_DONE | Green |
| NOT_INTERESTED / SWITCH_OFF | Grey |

Pagination at the bottom. Default page size 20. Show total count ("Showing 1–20 of 320 leads").

**Quick actions on hover/row:** "Call" button, "View" button. Keep it tight.

---

### 3. Lead Detail / Activity Panel

Clicking a lead opens a full detail view — either a dedicated page or a right-side panel (panel is preferred for quick navigation).

**Layout (two columns on desktop):**

**Left column — lead info:**
- Name, phone, alternate phone
- Email, source, preferred timing
- Current level, preferred learning mode
- Assigned to
- Course fee, total paid, balance (show as a mini payment summary card)
- Closing blocker + comment (if in CLOSING)
- Demo details (mentor, scheduled time, type) if applicable

**Right column — activity + actions:**
- **Action buttons at the top** (context-aware based on current status):
  - "Log Call" (disabled if DNP ≥ 5)
  - "Send WhatsApp" (only when `whatsappEligible = true`)
  - "Log WhatsApp Step" (dropdown: Step 1 / 2 / 3 + message)
  - "Update Status" (opens a modal with the right fields for that transition)
  - "Add Note"
  - "Add Payment"
  - "Assign" (admin only)
- **Activity timeline below** — chronological list of every action. Show icon + timestamp + description + who did it.

**Payment section** (below the timeline or in a tab):
- Table of payments: amount, type, status (PENDING/PAID), due date, notes
- "Mark Paid" button on pending payments
- Balance summary: Course Fee − Total Paid = Balance

---

### 4. Update Status Modal

When a user clicks "Update Status", show a modal with smart conditional fields:

| Target status | Show these fields |
|---------------|------------------|
| Any | Notes (textarea), Follow-up datetime |
| DEMO_BOOKED | Mentor picker, Date+time picker, Demo type (Online/Offline) |
| DEMO_DONE | Conducted-at datetime |
| CLOSING | Closing blocker dropdown, Comment, Course fee |
| PAYMENT_DONE | Course fee |
| WHATSAPP_RESPONDED | Alternate phone input |

Make the status dropdown a visual flow — not just a plain select. Show where in the pipeline each status sits.

---

### 5. Add Payment Modal

Clean, simple form:
- Amount (number input with ₹ prefix)
- Payment type: FULL_PAYMENT / ADVANCE / INSTALLMENT (radio or segmented button)
- Due date (date picker — only shown for INSTALLMENT)
- Notes
- Toggle: "Mark as Paid Now" — if on, shows paid. If off, saves as PENDING with a due date.

---

### 6. Team Performance Page

A leaderboard-style page showing per-rep stats. Accessible to both ADMIN and SALES.

**Cards per person:**
- Name + avatar/initials
- Active leads count
- Demos this week / month
- Sales this week / month
- Total sales (all-time)

Make it motivational — like a leaderboard. Sales people are competitive. A visual bar or rank position works well here.

---

### 7. Webinars Page

**Admin view:**
- List of all webinars (title, date, host mentor, capacity, registrations count, active/inactive toggle)
- Create/edit webinar button
- Click a webinar → see registrations table (name, phone, email, UTM source, attended toggle)
- "Mark Attended" button per registrant

**Sales view:**
- Same list but read-only (no create/edit)
- Can see registrations (to know who to follow up)

**Public registration page** (for the website / landing page — separate from the CRM):
- Simple form: Name, Phone, Email
- Should feel like a real event registration — webinar title, date, mentor photo, topic highlights
- Success screen: "You're registered! Check WhatsApp for the link."
- This page embeds UTM params automatically from the URL

---

### 8. Sync Status Widget

Small persistent widget or header indicator showing:
- Last sync time (e.g. "Last synced 2h ago")
- "Sync Now" button — shows loading state, then "Synced ✓ — 12 new leads"
- Link to sync log page (table of last 20 syncs)

---

## Design Principles — Please Read

We want this to feel like a **professional SaaS CRM**, not an internal admin panel. Here's what that means practically:

1. **Speed over decoration.** Sales reps log 30+ actions a day. Every click counts. Reduce friction — default to the most likely next action, use keyboard shortcuts where possible, auto-focus the first field in modals.

2. **Status badges must be instantly scannable.** A sales rep glances at the list and needs to know in 2 seconds which leads need attention today. Colour, iconography, and typography hierarchy matter here.

3. **Alerts must feel urgent.** If there are 3 overdue follow-ups, that needs to be the first thing the rep sees when they open the CRM — not buried below a fold.

4. **Mobile-aware.** Some reps check the CRM on their phone between calls. The lead detail and status update modal must work well on a 390px screen.

5. **Empty states.** When there are no leads, no payments, no webinars — show a helpful empty state, not a blank page.

6. **Inline feedback.** After logging a call, updating a status, or adding a payment — show a toast/snackbar confirmation. The rep should never wonder "did that save?"

7. **Loading states.** The leads list has real pagination. Show skeleton loaders while data loads, not blank space.

---

## Reference

Full API documentation (all endpoints, request/response shapes, enums, error format):

→ **`Lead_Management_Frontend_Integration.md`** — attached

All endpoints are under `/api/sales/**` (ADMIN + SALES) and `/api/public/**` (no auth for webinar registration). The backend is live at `https://api.firstmilliontrade.com`.

If anything is unclear or you need a field added, tell Yash — we can extend the API quickly.

---

*Build it like you'd want to use it every day.*
