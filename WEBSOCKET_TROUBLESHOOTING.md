# WebSocket Connection Troubleshooting Guide

## Errors Reported (Real Case Analysis)

### Error 1: SocketTimeoutException
```
java.net.SocketTimeoutException: failed to connect to /[IP] (port [WRONG_PORT]) after 10000ms
```

**What it means**: The client waited 10 seconds but the server never responded.

**Root Cause (from your case)**:
- App was configured with **wrong port** (9090 instead of 9003)
- Port 9090 had no service listening
- Connection timed out after 10 seconds with no response

**Common causes**:
1. Server listening on different port than configured
2. Firewall is blocking the port
3. Server service is not running
4. Wrong IP address configured

### Error 2: ProtocolException / HTTP 404
```
java.net.ProtocolException: Expected HTTP 101 response but was '404 Not Found'
```

**What it means**: WebSocket handshake was attempted but endpoint returned 404.

**Root Cause (from your case)**:
- After retrying, connection finally reached a service (possibly wrong server)
- That service had no WebSocket endpoint at the requested path
- App was still using wrong port configuration

**Common causes**:
1. Terminal ID doesn't exist on server
2. Wrong API path or version
3. WebSocket endpoint not implemented on that server
4. Connected to wrong server/port entirely

---

## How to Debug

### Step 1: Verify Server Address and Port

**First, confirm the correct server details**:
- What IP address should the app connect to?
- What port is the Claude Monitor server listening on?
- Is there a hostname instead of IP?

**Example** (use your actual server details):
```bash
# Verify server is running
ping [YOUR_SERVER_IP]
curl -v http://[YOUR_SERVER_IP]:[YOUR_SERVER_PORT]/api/health
```

**What to look for**:
- If ping fails → Network/firewall issue
- If curl timeout (takes >10 seconds) → Port not open or service not responding
- If curl succeeds (< 3s) → Server is up, proceed to next step

### Step 2: Verify Terminal Exists

**Check terminal exists on server**:
```bash
# REST endpoint should return terminal data
curl -u username:password \
  http://[YOUR_SERVER_IP]:[YOUR_SERVER_PORT]/api/terminals/[TERMINAL_ID]

# Should return something like:
# {"success": true, "data": {"id": "...", "name": "...", "status": "running"}}
```

**What to look for**:
- If 404 → Terminal ID doesn't exist (check correct ID)
- If 401 → Wrong credentials
- If 200 with `"status": "running"` → Terminal exists and is ready ✓
- If 200 with `"status": "stopped"` → Terminal needs Resume (use app UI button)

### Step 3: Test WebSocket Endpoint

**Test WebSocket handshake directly**:
```bash
# Using websocat (install if needed)
websocat -E --add-headers \
  --header "Authorization: Basic $(echo -n 'username:password' | base64)" \
  ws://[YOUR_SERVER_IP]:[YOUR_SERVER_PORT]/api/terminals/[TERMINAL_ID]/ws

# Should either:
# - Connect successfully (WebSocket handshake works) ✓
# - Return 404 (endpoint doesn't exist - server problem)
# - Return 401 (auth failed - check credentials)
```

### Step 4: Enable Detailed Logging

**In Android app logcat, filter by tag**:
```bash
logcat | grep -E "WebSocketService|DriverRepository|TerminalViewModel"
```

**Look for these log messages** (added in commit `62715c4`):

**Successful connection**:
```
=== WebSocket Connection Attempt #1 ===
Base URL (original): http://[YOUR_SERVER_IP]:[YOUR_SERVER_PORT]
Terminal ID: [TERMINAL_ID]
WebSocket URL: ws://[YOUR_SERVER_IP]:[YOUR_SERVER_PORT]/api/terminals/[TERMINAL_ID]/ws
Initiating WebSocket connection (attempt 1)
WebSocket connected ✓
```

**Failed connection (timeout)**:
```
=== WebSocket Connection Attempt #1 ===
[logs as above]
=== WebSocket Connection Failed ===
Error type: SocketTimeoutException
Error message: failed to connect to /[YOUR_SERVER_IP] (port [PORT]) after 10000ms
No HTTP response available (network error)
Retry attempt: 0/3
```

**Failed connection (404)**:
```
=== WebSocket Connection Failed ===
Error type: ProtocolException
Error message: Expected HTTP 101 response but was '404 Not Found'
HTTP Response Code: 404
Retry attempt: 0/3
```

---

## Fixes Implemented (Commit 62715c4)

### 1. Extended Timeout
- **Before**: 10 seconds
- **After**: 15 seconds
- **Why**: Allows more time for WebSocket handshake, especially during server startup

### 2. URL Encoding
- Terminal IDs are now URL-encoded before building WebSocket URL
- Prevents issues with special characters

### 3. URL Validation
- URLs are validated when adding new drivers
- Checks for:
  - Valid http:// or https:// scheme
  - Valid host address
  - Valid port range (1-65535)
  - Will reject malformed URLs before attempting connection

### 4. Enhanced Logging
- Detailed logs show exact URL being attempted
- Shows terminal ID being used
- Shows specific error details (socket timeout vs HTTP 404 vs auth error)

---

## Diagnostic Checklist

- [ ] Can ping server IP address?
- [ ] Can curl server health endpoint (curl http://[IP]:[PORT]/api/health)?
- [ ] Response time is < 3 seconds (not hanging)?
- [ ] Does REST endpoint return terminal data with `"status": "running"`?
- [ ] Can websocat connect to WebSocket endpoint with valid credentials?
- [ ] Is server firewall allowing the port inbound?
- [ ] Is IP address and port **correct in driver settings** (exactly as server is listening)?
- [ ] Is terminal ID correct and exists on server?
- [ ] Port in app matches port server is actually listening on?

---

## Server-Side Requirements

For WebSocket connections to work, the backend server must:

1. **Listen on port 9090** (or configured port)
2. **Provide WebSocket endpoint** at `/api/terminals/{id}/ws`
3. **WebSocket and REST APIs** must be on same host/port
4. **Terminal must exist** and be in "running" status
5. **Support Basic Auth or X-API-Token** for authentication

---

## Recommended Actions

### If SocketTimeoutException (connection timeout):
1. **Verify server is running**: `ping [YOUR_SERVER_IP]`
2. **Check port is open**: `curl -v http://[YOUR_SERVER_IP]:[YOUR_PORT]/api/health`
   - Should respond within 3 seconds
   - If hangs for 10+ seconds → port is not open or service not listening
3. **Verify port in app matches actual port** (common issue: configured 9090 but server uses 9003)
4. **Check firewall rules** on server side
5. Note: Timeout was extended from 10s to 15s in commit `62715c4` for slower connections

### If HTTP 404 Error:
1. **Verify terminal ID exists**: Query REST API first
   ```bash
   curl -u username:password http://[IP]:[PORT]/api/terminals/[TERMINAL_ID]
   ```
2. **Check if terminal status is "running"** (must be running to connect WebSocket)
   - If status is "stopped", click Resume button in app (Fix #1 from Phase 1)
3. **Verify WebSocket endpoint exists** on server: `/api/terminals/{id}/ws`
4. **Confirm you're connecting to correct server** (not a different service on same network)

### If HTTP 401/403 (Auth Error):
1. **Verify credentials are correct**: username/password
2. **Check if using API token**: If configured, verify token is valid
3. **Test REST endpoint first** to confirm auth works:
   ```bash
   curl -u username:password http://[IP]:[PORT]/api/health
   ```
4. **If using X-API-Token**, ensure header is properly set

---

## Log Examples

### Successful Connection
```
=== WebSocket Connection Attempt #1 ===
Base URL (original): http://72.60.69.72:9090
Terminal ID: f96313f4-f8bc-4568-b6d4-f05044afc795
WebSocket URL: ws://72.60.69.72:9090/api/terminals/f96313f4-f8bc-4568-b6d4-f05044afc795/ws
Using authentication: Basic Auth
Initiating WebSocket connection (attempt 1)
WebSocket connected
```

### Timeout Error
```
=== WebSocket Connection Attempt #1 ===
...
=== WebSocket Connection Failed ===
Error type: SocketTimeoutException
Error message: failed to connect to /72.60.69.72 (port 9090) after 10000ms
No HTTP response available (network error)
Retry attempt: 0/3
```

### 404 Not Found Error
```
=== WebSocket Connection Failed ===
Error type: ProtocolException
Error message: Expected HTTP 101 response but was '404 Not Found'
HTTP Response Code: 404
HTTP Response Message: Not Found
Retry attempt: 0/3
```

---

## Next Steps

1. **Enable app logging** and attempt connection
2. **Check server logs** for matching request
3. **Compare diagnostic outputs** with this guide
4. **Server investigation** may be needed if everything looks correct on client

The new logging in commit `62715c4` should provide clear visibility into what's happening at each step.

