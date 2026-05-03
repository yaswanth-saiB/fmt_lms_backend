# FMT Sales CRM — Functional Guide

> **For:** Sales Team, Admin, Team Discussion
> **Platform:** First Million Trade — Internal CRM
> **Version:** v2 (May 2026)
> **Purpose:** Explains the lead lifecycle, what each status means, what actions to take at each stage, how payments are tracked, and how webinars feed leads automatically.

---

## Overview

The FMT Sales CRM is the internal tool used by the sales team to manage all incoming leads — people who have enquired about our trading courses through ads, organic content, webinars, or referrals.

Every lead goes through a defined journey from first contact to payment. The CRM tracks every action taken, so no lead falls through the cracks and the team always knows exactly where each person stands.

---

## How Leads Get Into the System

### 1. Auto-Sync from Google Sheet (Primary Source)
The digital marketing team adds all ad leads into a central Google Sheet. The system automatically reads this sheet **twice a day — Monday to Saturday at 10 AM and 5 PM IST** and imports any new phone numbers.

The sales team can also click **"Sync Now"** at any time to pull entries immediately.

**What gets imported:**
- Name, phone number, email
- Lead source (Meta Ads, Google Ads, Organic, etc.)
- Current experience level in stock market
- Preferred learning mode (Online / Offline)
- **Preferred call timing** (Morning / Afternoon / Evening / Night) — if the sheet has this column
- Original enquiry timestamp (used to calculate lead age)
- Sheet status notes and comments (stored in the Notes field)

**Duplicates are skipped** — if a phone number already exists in the CRM, it will not be imported again.

### 2. Manual Excel Upload
If the team has a bulk list in `.xlsx` format, use the **Import from Excel** button.

### 3. Manual Entry
Add a single lead manually if someone calls in or is referred directly.

### 4. Webinar Registrations (Automatic)
When someone registers for a free webinar via the registration form on our website, the system **automatically creates a lead** with source `WEBINAR` if their phone number is not already in the CRM. If the phone exists, the registration is linked to the existing lead.

---

## Lead Status Flow

```
NEW
 │
 ├──[Call, no answer]──► DNP_1 → DNP_2 → DNP_3 → DNP_4 → DNP_5
 │                                                            │
 │                                               [WhatsApp eligible after 5 DNPs]
 │                                                            │
 │                                                     WHATSAPP_SENT
 │                                                            │
 │                                               [Lead replies on WhatsApp]
 │                                                            │
 │                                                  WHATSAPP_RESPONDED
 │                                                            │
 └──[Connected by call]──────────────────────────────► CONTACTED
                                                             │
                                      ┌──────────────────────┤
                                      │                      │
                               FOLLOWUP_SCHEDULED       DEMO_BOOKED
                                                             │
                                                 ┌───────────┤
                                                 │           │
                                            DEMO_DONE   DEMO_NO_SHOW
                                                 │
                                              CLOSING
                                                 │
                                          PAYMENT_DONE ✅

At any point → SWITCH_OFF  (number unreachable)
At any point → NOT_INTERESTED  (lead clearly declined)
```

---

## What Each Status Means & What to Do

### NEW
The lead just came in. No contact has been made yet.

**Action:** Call as soon as possible. Leads contacted within the first hour convert significantly better.

---

### DNP_1 to DNP_5 — Did Not Pick

The lead did not answer. Every time you log a failed call, the system increments the count.

| Count | What to do |
|-------|-----------|
| DNP_1 | Try again later the same day |
| DNP_2 | Try a different time of day |
| DNP_3 | Try morning or evening |
| DNP_4 | Spread over 2–3 days |
| DNP_5 | System marks lead **WhatsApp Eligible** — send a message |

The **Call button is disabled** after 5 attempts.

---

### WHATSAPP_SENT

After 5 failed calls, you can send a WhatsApp intro message. Click **"Send WhatsApp"** in the CRM to log this. The CRM records that you sent it; you send the actual message on WhatsApp.

**Using WhatsApp Campaign Steps:** If you're running a multi-day follow-up sequence (Day 1 intro, Day 3 testimonial, Day 7 offer), use the **"Log WhatsApp Step"** button to record each message you send. This gives a complete history.

---

### WHATSAPP_RESPONDED

The lead replied to your WhatsApp message. Update the status to WHATSAPP_RESPONDED.

**If they gave a different phone number** in their reply, enter it in the **Alternate Phone** field when updating the status. The CRM will store it alongside the original number.

**Next step:** Call the lead — they've shown interest by responding.

---

### CONTACTED

You successfully spoke to the lead. First real conversation.

**Find out:**
- Their stock market experience level
- What they're interested in (F&O, options, etc.)
- Availability and preferred learning style
- Their **preferred call timing** (morning/afternoon/evening/night) — useful for future follow-ups

**Next step:** Book a demo or schedule a follow-up call.

---

### FOLLOWUP_SCHEDULED

The lead needs more time or information. A follow-up has been scheduled.

**Important:** Always set the follow-up date and time in the CRM. The dashboard shows **overdue follow-ups** (already past) and **follow-ups due within the next 2 hours** as alerts — so nothing gets missed.

---

### DEMO_BOOKED

The lead has agreed to attend a demo. When you book it, fill in:
- **Demo Mentor** — which mentor will conduct the session
- **Demo Date & Time**
- **Demo Type** — Online (Zoom) or Offline (in-person)

**Before the demo:** Send reminders the day before and 1 hour before.

---

### DEMO_NO_SHOW

The lead missed the demo.

**What to do:** Call to understand why and offer to reschedule. Update back to DEMO_BOOKED with a new time if they agree.

---

### DEMO_DONE

The demo was conducted. Update the status and log the actual time it was conducted.

**What to do:** Follow up within 24 hours while the experience is fresh. Ask for their feedback and move to closing.

---

### CLOSING

The lead is interested but hasn't paid yet. There's a blocker preventing immediate payment.

When you move to CLOSING, select the **Closing Blocker** and add a comment:

| Blocker | Meaning |
|---------|---------|
| PRICING_ISSUE | Fee is too high |
| NEEDS_EMI | Wants to pay in installments |
| NEEDS_TIME | Not ready to commit right now |
| COMPARING_COMPETITOR | Evaluating other courses |
| NEEDS_OFFLINE_DEMO | Wants an in-person session before deciding |
| FAMILY_DECISION_PENDING | Needs family approval |
| OTHER | Any other reason — explain in the comment |

Also enter the **agreed course fee** at this stage.

**What to do:** Address the blocker. Follow up every 2–3 days.

---

### PAYMENT_DONE ✅

The lead has paid and enrolled. The sales cycle is complete.

When you mark this status, the system automatically records **who closed the deal** (your name). The agreed course fee should be entered here if not already done at CLOSING.

**After payment:** Admin creates the student account and enrolls them in the appropriate batch.

---

### NOT_INTERESTED

The lead has clearly declined. Do not pursue unless they reach back out.

---

### SWITCH_OFF

The number is always off or unreachable. No further action possible.

---

## Payment Tracking

Once a lead reaches CLOSING or PAYMENT_DONE, you can record payments directly in the CRM — no need for a separate spreadsheet.

### Payment Types

| Type | When to use |
|------|------------|
| FULL_PAYMENT | Paid the entire fee in one go |
| ADVANCE | Partial upfront payment (booking amount) |
| INSTALLMENT | One of multiple scheduled payments |

### How to Record a Payment

In the lead detail page, go to the **Payments section** and click **Add Payment**. Enter:
- Amount
- Payment type
- Due date (for pending installments)
- Any notes (UPI transaction ID, bank name, etc.)
- Check **"Mark as Paid Now"** if the money was received immediately

### Balance Tracking

The CRM automatically calculates:
- **Total Paid** = sum of all PAID payments for that lead
- **Balance** = Course Fee − Total Paid

This gives a clear picture of how much is still outstanding for installment students.

### Marking an Installment as Paid

When a scheduled installment is received, go to the payment record and click **"Mark Paid"**. The system sets the paid timestamp automatically.

---

## Webinar Management

Webinars are free online sessions used to attract leads and demonstrate value.

### How it works

1. Admin creates a webinar (title, date/time, Zoom link, mentor, capacity)
2. A registration link is shared publicly (social media, website)
3. People register using just their name, phone, and email
4. The system **automatically creates a CRM lead** for any new phone number
5. Sales team can see who registered and follow up

### UTM Tracking

Registration links can include UTM parameters (`utm_source`, `utm_medium`, `utm_campaign`) to track which social post or campaign drove registrations. This is visible in the registrations list for analysis.

### After the Webinar

- Mark each attendee using the **"Mark Attended"** button in the registrations list
- Follow up with attendees within 24 hours — they have the freshest intent
- Non-attendees should be contacted to offer the recording or reschedule

---

## Lead Age — Aging & Stale Alerts

The CRM tracks how old each lead is (based on their original enquiry date from the Google Sheet).

| Age | Label | Action |
|-----|-------|--------|
| 0–3 days | Fresh | Normal priority |
| 4–7 days | Aging ⚠️ | Follow up urgently |
| 8+ days | Stale 🔴 | High priority — may lose interest |

The dashboard shows counts for aging and stale leads. Leads already at PAYMENT_DONE, NOT_INTERESTED, or SWITCH_OFF are excluded.

---

## Follow-Up Alerts

The dashboard shows two follow-up alerts:

| Alert | Meaning |
|-------|---------|
| **Overdue follow-ups** | FOLLOWUP_SCHEDULED leads where the time has already passed |
| **Due soon (2 hrs)** | FOLLOWUP_SCHEDULED leads due within the next 2 hours |

Check these first thing every morning and again at midday.

---

## Lead Stage Filter

When viewing the leads list, you can filter by **stage** instead of individual status:

- **ACTIVE** — leads still in the pipeline (NEW through CLOSING)
- **INACTIVE** — leads that are done (PAYMENT_DONE, NOT_INTERESTED, SWITCH_OFF)

This is useful for focusing the sales team on live leads without individually selecting each status.

---

## Preferred Call Timing

Leads imported from Google Sheet (or webinar registrations) may have a **Preferred Call Timing**:
- MORNING, AFTERNOON, EVENING, or NIGHT

Use this field to schedule your calls at the time the lead is most likely to answer. It's shown in the lead list and detail view.

---

## Per-Rep Performance Dashboard

The **Team Stats** section shows a breakdown per sales person:

| Metric | Description |
|--------|-------------|
| Active Leads | Leads assigned to this person that are still in the pipeline |
| Demos This Week | Demos conducted by this person this week |
| Demos This Month | Demos conducted this month |
| Sales This Week | Deals closed (PAYMENT_DONE) this week |
| Sales This Month | Deals closed this month |
| Total Sales | All-time deals closed |

This lets the admin see who is performing and who may need support.

---

## Demo Performance Tracking

The dashboard shows demo counts by time period:

| Metric | What it counts |
|--------|---------------|
| Demos Booked Today/Week/Month | Status moved to DEMO_BOOKED in that period |
| Demos Done Today/Week/Month | Status moved to DEMO_DONE in that period |
| Sales This Week/Month | Status moved to PAYMENT_DONE in that period |

These come from the **activity log timestamps** — accurate even if a lead's status was later changed.

---

## Activity Log

Every action on a lead is recorded and visible in the lead detail view:

| Activity | When logged |
|----------|------------|
| LEAD_IMPORTED | Lead first added (sheet sync, Excel, manual, webinar) |
| CALL_ATTEMPTED | Every failed call attempt |
| WHATSAPP_SENT | When WhatsApp is marked as sent |
| WHATSAPP_SEQUENCE | Each step of a WhatsApp campaign sequence |
| STATUS_CHANGE | Every status change (shows old → new) |
| NOTE_ADDED | When a note is added |
| FOLLOWUP_SCHEDULED | When a follow-up is scheduled |
| PAYMENT_RECORDED | When a payment is added or marked paid |
| WEBINAR_REGISTERED | When the lead registered for a webinar |

The activity log gives a complete history so any team member can pick up where another left off.

---

## Google Sheets Sync — What to Know

- The digital marketing team manages the Google Sheet with ad leads
- Syncs run automatically at **10 AM and 5 PM IST, Monday–Saturday**
- Use **"Sync Now"** to pull immediately any time
- **Sync Logs** shows history — rows read, imported, skipped, and any errors
- Duplicate phone numbers are always skipped
- Phone numbers are normalised automatically — `+91` and `91` prefix are stripped, non-numeric characters removed

---

## Who Can Do What

| Action | SALES | ADMIN |
|--------|-------|-------|
| View all leads | ✅ | ✅ |
| Add / import leads | ✅ | ✅ |
| Log calls, update status | ✅ | ✅ |
| Log WhatsApp steps | ✅ | ✅ |
| Record payments | ✅ | ✅ |
| Sync from Google Sheets | ✅ | ✅ |
| View webinar registrations | ✅ | ✅ |
| View team rep stats | ✅ | ✅ |
| View enquiries | ✅ | ✅ |
| **Assign lead to another person** | ❌ | ✅ |
| **Create / manage webinars** | ❌ | ✅ |
| **Mark registrants as attended** | ❌ | ✅ |
| **Create SALES user accounts** | ❌ | ✅ |

---

*FMT Sales CRM Functional Guide — v2, May 2026*
