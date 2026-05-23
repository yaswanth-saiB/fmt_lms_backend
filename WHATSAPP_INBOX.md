# WhatsApp Inbox + Integration — FMT Backend

## Status
| Module | Status |
|--------|--------|
| DB entities + repositories | ✅ Done |
| WhatsApp API service (send messages) | ✅ Done |
| Webhook receiver (messages + status + lead gen) | ✅ Done |
| Conversation service (incoming message flow) | ✅ Done |
| Lead gen service (Meta ads form → lead) | ✅ Done |
| Inbox APIs (list, detail, reply, assign, status) | ✅ Done |
| Chatbot admin CRUD | ✅ Done |
| Chatbot engine + seed responses | ✅ Done — **ON HOLD, logic under discussion** |
| Message templates approved on Meta | ⏳ Pending — Yash to set up |
| Webhook registered on Meta dashboard | ⏳ Pending — Yash to set up |
| Env vars added to EC2 .env | ⏳ Pending — Yash to set up |

---

## DB Tables (auto-created by Hibernate on deploy)
```
whatsapp_conversations   — one row per phone number
whatsapp_messages        — every inbound + outbound message
chatbot_responses        — bot reply rules (admin-editable from UI)
chatbot_sessions         — current bot state per conversation
```

---

## API Endpoints

### Webhooks (public — no auth)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/webhook/whatsapp` | Meta verification handshake (one-time) |
| POST | `/api/webhook/whatsapp` | All WhatsApp events (messages, status, lead gen) |

### Inbox (ADMIN or SALES)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/inbox/conversations` | List convs — `?status=NEEDS_HUMAN&assignedToMe=true&search=rahul&page=0&size=20` |
| GET | `/api/inbox/conversations/{id}` | Full conversation + all messages (marks as read) |
| POST | `/api/inbox/conversations/{id}/reply` | Send free text (requires open 24h window) |
| POST | `/api/inbox/conversations/{id}/send-template` | Send template (works after window expires too) |
| PUT | `/api/inbox/conversations/{id}/assign` | Assign to a user — `{ "assignedTo": "uuid" }` |
| PUT | `/api/inbox/conversations/{id}/status` | Update status — `{ "status": "CLOSED" }` |
| PUT | `/api/inbox/conversations/{id}/toggle-bot` | Enable/disable chatbot for this conversation |
| GET | `/api/inbox/unread-count` | `{ "total": 5, "needsHuman": 3 }` for navbar badge |

### Chatbot Admin (ADMIN only)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/admin/chatbot/responses` | List all chatbot response rules |
| POST | `/api/admin/chatbot/responses` | Create a rule |
| PUT | `/api/admin/chatbot/responses/{id}` | Update a rule |
| DELETE | `/api/admin/chatbot/responses/{id}` | Delete a rule |

---

## Conversation Status Values
| Status | Meaning |
|--------|---------|
| `OPEN` | Active, chatbot handling |
| `NEEDS_HUMAN` | Bot couldn't handle — needs sales person |
| `ASSIGNED` | Assigned to a specific sales person |
| `CLOSED` | Conversation closed |

## Chatbot States (ON HOLD)
| State | When |
|-------|------|
| `INITIAL` | New inbound message from unknown number |
| `INITIAL_LEAD_GEN` | Lead came via Meta ads form |
| `MENU_SHOWN` | Welcome message with buttons sent |
| `FEE_INQUIRY` | User asked about fees |
| `DEMO_BOOKING` | User wants to book a demo |
| `ESCALATED` | Handed off to human |
| `CLOSED` | Conversation closed |

---

## Chatbot Response Rules (how they work)
Each row in `chatbot_responses` has:
- `state` — which bot state triggers this. Use `*` for any state.
- `trigger_type` — `BUTTON_ID`, `KEYWORD`, or `DEFAULT`
- `trigger_value` — the button ID or keyword intent
- `message_type` — `TEXT` or `INTERACTIVE_BUTTONS`
- `response_text` — the message (supports `{{name}}` placeholder)
- `buttons_json` — `[{"id":"btn_id","title":"Button Title"}]` for interactive messages
- `next_state` — state to move to after this response
- `updates_lead_status` — optionally updates the lead's CRM status

**Resolution priority:**
1. Exact state + BUTTON_ID match
2. Wildcard `*` state + BUTTON_ID match
3. Exact state + KEYWORD match
4. Wildcard `*` state + KEYWORD match
5. DEFAULT fallback for current state
6. DEFAULT fallback for `*` state
7. Escalate to human (if nothing matches)

**Keywords resolved from text:**
- fee / price / cost → `FEE_INQUIRY`
- demo / class / attend → `DEMO_BOOKING`
- location / where / address → `LOCATION`
- timing / time / schedule → `TIMING`
- stop / human / agent / person → `ESCALATED`
- anything else → `ESCALATED`

---

## Env Variables Needed (add to EC2 `.env`)
```
WHATSAPP_ACCESS_TOKEN=         # From Meta App → WhatsApp → API Setup
WHATSAPP_PHONE_NUMBER_ID=      # Already known: 1105422512657572
WHATSAPP_WEBHOOK_VERIFY_TOKEN= # Set to: fmt_whatsapp_webhook_2026
META_APP_SECRET=               # From Meta App → Settings → Basic → App Secret
META_PAGE_ID=                  # Your Facebook Page ID
```

---

## What Yash Needs to Set Up (Before Going Live)

### 1. Meta App — WhatsApp Business API
- Go to: https://developers.facebook.com → Your App → WhatsApp → API Setup
- Get: `Access Token` (use a **System User Token** for production — not the temporary one)
- Get: `Phone Number ID` (already known)
- Get: `App Secret` from App → Settings → Basic

### 2. Register Webhook on Meta Dashboard
- Go to: App → WhatsApp → Configuration → Webhook
- Webhook URL: `https://api.firstmilliontrade.com/api/webhook/whatsapp`
- Verify Token: `fmt_whatsapp_webhook_2026`
- Subscribe to these fields:
  - `messages` — incoming messages + status updates
  - `messaging_postbacks` — button replies
  - (optional) `leadgen` — if using Meta Lead Gen ads

### 3. Message Templates (required for outbound after 24h window)
Meta must approve your templates before you can send them.
- Go to: App → WhatsApp → Message Templates → Create Template
- Template needed: `fmt_lead_acknowledgement`
  - Category: MARKETING or UTILITY
  - Body: `Hi {{1}}! Thank you for your interest in First Million Trade. Our team will contact you shortly to discuss how we can help you on your trading journey. 🙏`
- Also create templates for: follow-up, demo reminder, etc. as needed
- Template approval takes 24–48 hours

### 4. Lead Gen Ads Webhook (if using Meta Lead Gen forms)
- Go to: Meta Business Suite → Ads Manager → Lead Gen form
- OR: Facebook Page → Publishing Tools → Instant Forms → Connect CRM
- Webhook must be subscribed to `leadgen` field (step 2 above)

### 5. Update Seed Data Before Go-Live
In `ChatbotResponseSeeder.java`, replace `+91-XXXXXXXXXX` with the actual support phone number.

### 6. System User Token (production)
The temporary access token expires in ~60 days.
For production, create a **System User Token** that doesn't expire:
- Meta Business Manager → Settings → System Users → Create System User
- Assign WhatsApp permissions
- Generate token (select `whatsapp_business_messaging` and `whatsapp_business_management` scopes)

---

## Files Reference
```
service/WhatsAppApiService.java       — Meta Graph API calls (send text, template, buttons)
service/ConversationService.java      — handles incoming messages, routes to bot or human
service/ChatbotEngine.java            — bot logic, state machine, response lookup
service/LeadGenService.java           — Meta lead gen form → create lead + conversation
service/InboxService.java             — all inbox API logic
service/ChatbotResponseSeeder.java    — seeds default bot responses on first startup

controller/WhatsappWebhookController.java  — GET+POST /api/webhook/whatsapp
controller/InboxController.java            — /api/inbox/**
controller/ChatbotAdminController.java     — /api/admin/chatbot/**

entity/WhatsappConversation.java
entity/WhatsappMessage.java
entity/ChatbotResponse.java
entity/ChatbotSession.java

repository/WhatsappConversationRepository.java
repository/WhatsappMessageRepository.java
repository/ChatbotResponseRepository.java
repository/ChatbotSessionRepository.java
```

---

---

## Instant Lead from Meta Ad — How It Works

### Complete Flow
```
Person sees FMT ad on Instagram/Facebook
    ↓
Fills Lead Gen form (name, phone, email)
    ↓
Meta sends webhook → POST /api/webhook/whatsapp
    ↓
LeadGenService.processNewLead()
    ├── Fetches full lead data from Meta Graph API
    ├── Normalizes phone (adds 91 country code)
    ├── Duplicate check by phone
    ├── Saves lead to DB (source: META_ADS_DIRECT, status: NEW)
    ├── Logs LEAD_IMPORTED activity
    ├── Sends instant email alert to admin (🔥 New Lead from Meta Ad)
    ├── Creates WhatsApp conversation (state: INITIAL_LEAD_GEN)
    └── Sends fmt_lead_acknowledgement WhatsApp template to lead
```

### Edge Cases Handled
| Scenario | Behaviour |
|----------|-----------|
| No phone in form | Lead saved with name + email, no WhatsApp sent |
| Duplicate phone | Skipped, admin notified with ⚠️ Duplicate alert |
| Template not approved yet | WhatsApp send fails silently, lead still saved + admin notified |
| Meta API fetch fails | Admin notified by email with leadgenId for manual follow-up |

### What Yash Needs to Set Up for Instant Leads

**Step 1 — Subscribe the app to `leadgen` field:**
- Meta App Dashboard → Webhooks → Add Subscription
- Object: **Page**
- Field: **leadgen**
- Webhook URL: `https://api.firstmilliontrade.com/api/webhook/whatsapp`
- Verify Token: `fmt_whatsapp_webhook_2026`

**Step 2 — Link your Facebook Page to the app:**
- Meta App Dashboard → Facebook Login → Settings → Page Subscriptions
- Select your FMT Facebook Page

**Step 3 — Link Lead Gen form to the page:**
- Go to Meta Ads Manager → Lead Ads → your campaign
- The `leadgen` webhook fires automatically when someone submits the form

**Step 4 — Test with Meta's Lead Gen Test Tool:**
- Meta App Dashboard → WhatsApp → Lead Ads Testing
- Submits a fake lead — you should see it in the CRM within seconds

### Admin Email Alert (sent instantly on new lead)
```
Subject: 🔥 New Lead from Meta Ad — [Name]

Name:     Rahul Sharma
Phone:    919876543210
Email:    rahul@gmail.com
Ad/Form:  FMT Trading Course - June 2026
Status:   Added to CRM — status: NEW
```

---

## Chatbot — Discussion Points (ON HOLD)
Things to decide before the chatbot goes live:
- [ ] Finalize exact greeting message text + button labels
- [ ] Decide on course fee to display (currently ₹15,000 placeholder)
- [ ] Decide class timings to display (currently 7–9 AM and 7–9 PM placeholder)
- [ ] Decide phone number to show in bot messages
- [ ] Confirm whether to auto-update lead status when bot triggers DEMO_BOOKING
- [ ] Decide if bot should escalate after N unanswered messages
- [ ] Decide on follow-up template messages content
