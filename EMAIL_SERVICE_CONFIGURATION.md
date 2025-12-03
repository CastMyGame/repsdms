# Email Service Configuration Guide

Your application now supports **two email sending methods** that can be toggled via configuration:

## 1. Gmail SMTP (Default)
- Uses `REPS.DMS@gmail.com` account
- All emails sent from this system account
- Works for all email types
- No user authentication required

## 2. Gmail API
- Sends emails on behalf of logged-in Google users
- Requires users to have logged in with Google SSO
- Falls back to SMTP if user doesn't have OAuth token
- Emails appear to come from the user's Gmail account

---

## Configuration

In `application.properties`:

```properties
# Choose email provider: "smtp" or "gmail-api"
mail.service.provider=smtp
```

### Options:
- `mail.service.provider=smtp` - Use Gmail SMTP (default, system account)
- `mail.service.provider=gmail-api` - Use Gmail API (user's account, with SMTP fallback)

---

## How It Works

### When `mail.service.provider=smtp`:
- All emails sent via Gmail SMTP
- From: `REPS.DMS@gmail.com`
- Works for all users (no Google login required)

### When `mail.service.provider=gmail-api`:
- **Single-recipient emails**: Tries Gmail API if sender has Google OAuth token, falls back to SMTP
- **Bulk emails (CC/BCC)**: Always uses SMTP (Gmail API limitation)
- **System emails (no sender context)**: Uses SMTP

---

## Email Types and Behavior

| Email Type | SMTP Mode | Gmail API Mode |
|------------|-----------|----------------|
| Simple email (single recipient) | ✅ SMTP | ✅ Gmail API (if token available) → SMTP fallback |
| Bulk email (CC/BCC) | ✅ SMTP | ✅ SMTP (Gmail API doesn't support well) |
| Class announcements | ✅ SMTP | ✅ Tries Gmail API → SMTP fallback |
| System notifications | ✅ SMTP | ✅ SMTP (no user context) |
| Password reset | ✅ SMTP | ✅ SMTP (no user context) |

---

## Usage Examples

### Example 1: Simple Email (Gmail API Mode)
If a teacher (logged in with Google) sends an email:
- **Has Google OAuth token**: Email sent via Gmail API (from teacher's account)
- **No token**: Email sent via SMTP (from REPS.DMS@gmail.com)

### Example 2: Class Announcement (Gmail API Mode)
When a teacher sends a class announcement:
- **Has Google OAuth token**: Tries Gmail API, but since it has CC recipients, falls back to SMTP
- **No token**: Email sent via SMTP

### Example 3: System Email (Any Mode)
Password reset, contact form, etc.:
- Always uses SMTP (no user context available)

---

## Requirements for Gmail API Mode

1. **Gmail API enabled** in Google Cloud Console
2. **User must log in with Google SSO** (to get OAuth token)
3. **Gmail send scope** included in OAuth scopes (already configured)

---

## Testing

### Test SMTP Mode:
```properties
mail.service.provider=smtp
```
All emails will come from `REPS.DMS@gmail.com`

### Test Gmail API Mode:
```properties
mail.service.provider=gmail-api
```
1. Log in with Google SSO
2. Send an email
3. Check if it comes from your Gmail account (if token available) or REPS.DMS@gmail.com (fallback)

---

## Logging

The service logs which method was used:
- `"Email sent via Gmail API on behalf of {email}"` - Gmail API used
- `"Email sent via SMTP to {email}"` - SMTP used
- `"Failed to send via Gmail API for {email}, falling back to SMTP"` - Fallback occurred

---

## Notes

- **Bulk emails** (with CC/BCC) always use SMTP due to Gmail API limitations
- **System emails** always use SMTP (no user context)
- **Fallback is automatic** - if Gmail API fails or token unavailable, SMTP is used
- **No code changes needed** - existing email methods work with both modes

