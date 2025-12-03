# Email Routing Guide - User vs System Emails

## Overview

The email service now intelligently routes emails based on:
1. **Email Type**: User-initiated vs System/Scheduled
2. **User Authentication**: Whether user has Google OAuth token
3. **Feature Flags**: Configuration settings

---

## Configuration

In `application.properties`:

```properties
# Overall email service mode
# Options: "smtp", "hybrid", "gmail-api"
mail.service.provider=hybrid

# Enable Gmail API for user-initiated emails
# true: User emails can use Gmail API if user has OAuth token
# false: All emails use SMTP
mail.service.user-emails.enabled=true

# Provider for system/scheduled emails
# "smtp": System emails always use SMTP (recommended)
# "gmail-api": System emails try Gmail API (not recommended)
mail.service.system-emails.provider=smtp
```

---

## Routing Logic

### Scenario 1: User with SSO + Gmail API Access

**Configuration:**
```properties
mail.service.provider=hybrid
mail.service.user-emails.enabled=true
mail.service.system-emails.provider=smtp
```

**Behavior:**
- ✅ **User-initiated emails** (class announcements, etc.) → Gmail API (from user's account)
- ✅ **System emails** (scheduled tasks, notifications, alerts) → SMTP (from REPS.DMS@gmail.com)
- ✅ **Fallback**: If Gmail API fails or token unavailable → SMTP

**Examples:**
- Teacher sends class announcement → Gmail API (from teacher's Gmail)
- Scheduled alert email → SMTP (from REPS.DMS@gmail.com)
- Password reset email → SMTP (from REPS.DMS@gmail.com)
- Punishment notification → SMTP (from REPS.DMS@gmail.com)

---

### Scenario 2: Traditional Login OR SSO Without Gmail API Access

**Configuration:**
```properties
mail.service.provider=smtp
# OR
mail.service.provider=hybrid
mail.service.user-emails.enabled=false
```

**Behavior:**
- ✅ **All emails** → SMTP (from REPS.DMS@gmail.com)
- ✅ Works for all users regardless of login method

---

## Email Classification

### User-Initiated Emails (Can use Gmail API)
- ✅ Class announcements (`/email/v1/classAnnouncement`)
  - Teacher sends to their class
  - Uses teacher's email as `fromEmail`
  - Marked as `isUserInitiated=true`

### System Emails (Always use SMTP)
- ✅ Password reset emails
- ✅ Contact form responses
- ✅ Scheduled alert emails (DETENTION/ISS reminders)
- ✅ Punishment notifications
- ✅ Positive shout-outs
- ✅ All bulk emails with CC/BCC

---

## Feature Flag Modes

### Mode 1: `smtp` (All SMTP)
```properties
mail.service.provider=smtp
```
- All emails use SMTP
- From: REPS.DMS@gmail.com
- Works for all users

### Mode 2: `hybrid` (Smart Routing) ⭐ Recommended
```properties
mail.service.provider=hybrid
mail.service.user-emails.enabled=true
mail.service.system-emails.provider=smtp
```
- User emails → Gmail API (if token available) or SMTP (fallback)
- System emails → SMTP
- Best of both worlds

### Mode 3: `gmail-api` (Force Gmail API)
```properties
mail.service.provider=gmail-api
mail.service.user-emails.enabled=true
mail.service.system-emails.provider=smtp
```
- Tries Gmail API for all emails (if token available)
- Falls back to SMTP if unavailable
- Not recommended for system emails

---

## How to Identify Email Type

### User-Initiated
- Called from user action (API endpoint triggered by logged-in user)
- Has `fromEmail` parameter with user's email
- Example: Teacher clicks "Send Announcement" button

### System/Scheduled
- Called from scheduled tasks, background jobs, or system events
- No user context or `fromEmail` is null
- Example: Daily reminder emails, password reset, notifications

---

## Code Examples

### User-Initiated Email (Class Announcement)
```java
// In EmailSenderService.sendClassAnnouncement()
emailRoutingService.sendBulkEmail(
    recipient,
    ccList,
    subject,
    body,
    bccList,
    teacherEmail,  // fromEmail
    true           // isUserInitiated = true
);
```

### System Email (Password Reset)
```java
// In AuthControllers.forgotPassword()
emailService.sendEmail(
    userEmail,
    "Reset Your Password",
    resetLink
    // No fromEmail, defaults to system email (SMTP)
);
```

---

## Fallback Behavior

The system **always** falls back to SMTP if:
1. Gmail API is not configured
2. User doesn't have OAuth token
3. OAuth token is expired
4. Gmail API call fails
5. Email has CC/BCC (Gmail API limitation)
6. System email (no user context)

**No emails will fail** - they'll always use SMTP as fallback.

---

## Logging

Check logs to see which method was used:
- `"Email sent via Gmail API on behalf of {email} (user-initiated: true)"` → Gmail API used
- `"Email sent via SMTP to {email}"` → SMTP used
- `"Failed to send via Gmail API for {email}, falling back to SMTP"` → Fallback occurred

---

## Testing

### Test Hybrid Mode:
1. Set `mail.service.provider=hybrid`
2. Set `mail.service.user-emails.enabled=true`
3. Log in with Google SSO
4. Send class announcement → Should use Gmail API
5. Trigger scheduled alert → Should use SMTP

### Test SMTP-Only Mode:
1. Set `mail.service.provider=smtp`
2. All emails → SMTP (from REPS.DMS@gmail.com)

---

## Recommendations

✅ **Recommended Configuration:**
```properties
mail.service.provider=hybrid
mail.service.user-emails.enabled=true
mail.service.system-emails.provider=smtp
```

This gives you:
- User emails from their Gmail accounts (better sender reputation)
- System emails from REPS.DMS@gmail.com (consistent branding)
- Automatic fallback to SMTP (reliability)

