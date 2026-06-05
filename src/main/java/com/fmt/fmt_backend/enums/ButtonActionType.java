package com.fmt.fmt_backend.enums;

/**
 * Action types that can be assigned to a WhatsApp template button.
 * Each value is implemented in ButtonActionExecutor. Adding a new action
 * requires (a) adding to this enum and (b) implementing the case in the executor.
 */
public enum ButtonActionType {

    /** Reply with a plain text message. params: { "text": "..." } */
    SEND_TEXT,

    /** Send another approved WhatsApp template. params: { "templateName": "..." } */
    SEND_TEMPLATE,

    /** Escalate the conversation to a human sales agent + send the standard escalation text. */
    ESCALATE_TO_HUMAN,

    /** The "Reserve My Seat" flow — confirmation text + admin email + escalate.
     *  params: { "batchName": "..." } (optional, defaults to "the upcoming batch") */
    RESERVE_SEAT,

    /** Kick off the existing demo booking state machine (date → mode → time). */
    TRIGGER_DEMO_FLOW,

    /** Update the lead's status (no message sent). params: { "status": "CONTACTED" | ... } */
    UPDATE_LEAD_STATUS
}
