# WhatsApp Chatbot — Operations Guide

## How the Bot Works

When a lead sends a message to our WhatsApp business number, the bot checks a set of rules (stored in the DB via the Chatbot admin page) and sends an automated reply. It follows a **state machine** — each conversation moves through states based on what the lead selects or types.

```
New message arrives
       ↓
Is bot globally ON?        → NO  → Route to human (Needs Human)
       ↓ YES
Is per-chat bot ON?        → NO  → Route to human
       ↓ YES
Is conversation CLOSED?    → YES → Do nothing
       ↓ NO
Is lead in warm stage?     → YES → Auto-disable bot, route to human
       ↓ NO
Did bot reply in last 45s? → YES → Skip (cooldown, avoid double-reply)
       ↓ NO
Run bot → send auto-reply
```

---

## Bot States

| State | When |
|-------|------|
| `INITIAL` | First message from a new lead |
| `INITIAL_LEAD_GEN` | Lead came from a Facebook Lead Ad |
| `MENU_SHOWN` | Bot sent the main menu (course info, fee, demo) |
| `FEE_INQUIRY` | Lead asked about fees |
| `DEMO_BOOKING` | Lead is in demo booking flow |
| `ESCALATED` | Lead asked to speak to a human |
| `CLOSED` | Conversation marked closed |

---

## Three Layers of Bot Control

### 1. Global Toggle (Admin → Chatbot page)
A single ON/OFF switch for the entire system.

- **Turn OFF** — bot stops auto-replying to ALL conversations instantly
- **Turn ON** — bot resumes for all conversations where per-chat bot is also on
- Resets to ON after a server restart (by design — keeps it simple)

**When to use:** Outside business hours, during a server issue, or when the team needs full manual control for a day.

### 2. Per-Chat Toggle (WA Inbox → inside each conversation)
The Bot On / Bot Off button in the chat header.

- Turns the bot off only for that specific conversation
- Sales agent can take over a chat without affecting other conversations
- Stays off permanently until manually turned back on

**When to use:** When a lead is warm and the sales agent is actively chatting.

### 3. Smart Auto-Disable (Automatic — no action needed)
When a lead's CRM status reaches a certain stage, the bot **automatically turns itself off** for that conversation and routes it to human.

Bot auto-disables when lead status is:
- **CONTACTED** — someone already spoke to this lead
- **FOLLOWUP_SCHEDULED** — follow-up call is planned
- **DEMO_BOOKED** — demo is scheduled
- **DEMO_DONE** — demo already happened
- **DEMO_NO_SHOW** — lead didn't attend demo
- **CLOSING** — in final payment stage
- **PAYMENT_DONE** — already a customer

---

## Double-Reply Prevention

When a lead sends an image AND a text in the same WhatsApp message, the API sometimes delivers them as two separate events. The bot has a **45-second cooldown** — if it already replied in the last 45 seconds, it skips the second event automatically.

---

## Managing Bot Responses (Admin → Chatbot page)

Each row in the table is one auto-reply rule. The bot picks the best match using this priority order:

1. State + Button ID match (most specific)
2. Wildcard state + Button ID
3. State + Keyword match
4. Wildcard state + Keyword
5. State + DEFAULT (catch-all for that state)
6. Wildcard + DEFAULT (global fallback)
7. If nothing matches → escalate to human

### Adding a New Response

| Field | What to put |
|-------|-------------|
| State | Which state this rule applies in (use `*` for all states) |
| Trigger Type | `DEFAULT` (any message), `BUTTON_ID` (button click), `KEYWORD` (contains word) |
| Trigger Value | The button ID or keyword to match (leave blank for DEFAULT) |
| Message Type | `TEXT` or `INTERACTIVE_BUTTONS` |
| Response Text | The message to send |
| Buttons JSON | Only for button messages — see format below |
| Next State | State to move to after this reply |
| Updates Lead Status | Optional — automatically update CRM status when this reply fires |

**Buttons JSON format:**
```json
[
  { "id": "BUTTON_ID_1", "title": "Option 1" },
  { "id": "BUTTON_ID_2", "title": "Option 2" }
]
```
Maximum 3 buttons per message (WhatsApp limit).

---

## WhatsApp Templates

Templates are pre-approved message formats used to **re-engage a lead after 24 hours** (when the messaging window has expired). You must create and get them approved in Meta Business Manager before using them.

### Creating a Template in Meta

1. Go to **Meta Business Manager → WhatsApp → Message Templates**
2. Click **Create Template**
3. Fill in:
   - **Category:** Marketing or Utility
   - **Name:** lowercase with underscores, no spaces (e.g. `fmt_course_enquiry`)
   - **Language:** English
   - **Body:** Your message text. Use `{{1}}`, `{{2}}` for variable parts
4. Submit → wait for approval (usually a few minutes to 24 hours)
5. Once **APPROVED**, it appears automatically in the inbox dropdown

### Using Templates in Inbox

- When a lead's 24-hour window is closed, a banner appears in the chat
- Click **Send Template** → pick from the approved template dropdown
- Fill in the parameter values (e.g. lead's name for `{{1}}`)
- Click Send

### Using Templates in Campaigns

- Go to **Admin → Campaigns → Create Campaign**
- Select an approved template from the dropdown
- Fill in parameters and choose which lead statuses to target
- Click **Preview Count** to see how many leads will receive it
- Save as Draft → then Send when ready

---

## Recommended Setup for Your Team

| Situation | Action |
|-----------|--------|
| New lead messages for the first time | Bot handles automatically |
| Lead clicks "Talk to Human" | Bot escalates, conversation marked Needs Human |
| Sales agent picks up a chat | Turn off per-chat bot, take over manually |
| Lead is CONTACTED or higher in CRM | Bot auto-disables, sales handles it |
| After-hours / weekend | Turn off global bot from Chatbot admin page |
| Lead stopped responding (24h window closed) | Use Send Template to re-engage |
| Bulk outreach (webinar promo, etc.) | Use Campaigns with an approved template |

---

## Key Points to Remember

- **The bot never replies to a closed conversation**
- **Per-chat bot defaults to ON** for every new conversation — turn it off when taking over a lead
- **The global toggle resets to ON after a server restart** — remember to turn it off again if needed
- **WhatsApp allows max 3 buttons per message** — keep button menus short
- **Templates must be approved by Meta** before they can be sent — create them in advance
- **Image captions:** If a lead sends an image with a caption, write the caption IN the image caption field (not as a separate message) — otherwise they arrive as two separate messages
