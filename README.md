# NexChat Pro — Modern Real-Time Messaging & Calling Application

NexChat Pro is a production-ready, feature-rich messaging and communication platform featuring real-time chat, voice and video calling, 24-hour status stories, hold-to-record voice messaging, and large media sharing powered by Firebase and Cloudinary.

---

## 🌟 Key Features

1. **Authentication & Session**:
   - Email/password authentication, registration, display name, username (@handle), phone number, and forgot password recovery.
   - Branded splash screen (2.5–3.5s timing) with silent session restoration and zero login-screen flash.

2. **One-to-One & Group Chats**:
   - Real-time Firestore synchronized messaging.
   - Read receipts: Sent, Delivered, and Seen (Double Blue Ticks).
   - Message actions: Reply with quoted message preview, Copy text, Delete for Me, Delete for Everyone.
   - Export chat history to formatted text file.

3. **Media & Voice Messages**:
   - Powered by Cloudinary unsigned uploads (support for files up to 1 GB).
   - Instant hold-to-record voice messaging (WhatsApp-style flow: press & hold mic -> release to send).
   - In-chat voice player with audio playback and duration tracker.
   - Image and video previews with full caching via Coil.

4. **24-Hour Status Stories**:
   - WhatsApp-style status updates with photos/videos and captions.
   - Automatic 24-hour expiration filter.
   - Ring indicators for unviewed vs viewed stories.
   - Viewer count indicator for status owners.

5. **Voice & Video Calling**:
   - Real-time one-to-one voice and video call signaling via Firestore (`calls/{callId}`).
   - Screen-off incoming call wake up (`PowerManager.WakeLock`, `setShowWhenLocked(true)`, `setTurnScreenOn(true)`).
   - High-importance notification channel with looping system ringtone and vibration.
   - In-call controls: Mute microphone, speakerphone toggle, toggle video, and call duration counter.

6. **24/7 Background Reliability**:
   - Android foreground service integration (`NexChatCallService`).
   - Battery optimization exemption prompt (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) for OEM skins (Xiaomi, Realme, Vivo, Oppo, Samsung).

---

## 🏗️ Architecture & Backend Configuration

### A. Android Identifiers
- **App Name**: NexChat Pro
- **Application ID**: `com.sayan.nexchatpro`
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 15+)

### B. Firebase Project Configuration
- **Project ID**: `nexchat-pro-8e480`
- **Project Number**: `418090114272`
- **Web/Client API Key**: `AIzaSyAb6XQuA5QqqoglYSvtQ-cjpOuuV3s_vwo`
- **Database URL**: `https://nexchat-pro-8e480-default-rtdb.firebaseio.com`
- **Storage Bucket**: `nexchat-pro-8e480.firebasestorage.app`

### C. Cloudinary Media Configuration
- **Cloud Name**: `emoxfdx6`
- **Upload Preset**: `ml_default` (Unsigned client upload preset)
- **Endpoint**: `https://api.cloudinary.com/v1_1/emoxfdx6/auto/upload`
- **Max File Size**: 1 GB for large video and document attachments.

---

## 🛡️ Firestore Security Rules (`firestore.rules`)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // User Profiles
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Conversations & Messages
    match /conversations/{conversationId} {
      allow read, write: if request.auth != null && 
        request.auth.uid in resource.data.participants;
      allow create: if request.auth != null;

      match /messages/{messageId} {
        allow read, write: if request.auth != null;
      }
    }
    
    // 24-Hour Statuses
    match /statuses/{statusId} {
      allow read, create: if request.auth != null;
      allow update, delete: if request.auth != null && 
        (request.auth.uid == resource.data.userId || request.resource.data.viewers != null);
    }
    
    // Voice & Video Calls Signaling
    match /calls/{callId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

---

## 🚀 Building the APK & AAB

### Local Build:
```bash
# Compile and build debug APK
gradle assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

## 🌐 Web Version & GitHub Pages Deployment

NexChat Pro includes a responsive Web Application in `/web/index.html` (with a root entry at `/index.html`) that connects live to the same Firebase (`nexchat-pro-8e480`) and Cloudinary (`emoxfdx6`) backend.

### How to Deploy from GitHub:
1. In your GitHub repository, click **Settings** (top navigation bar).
2. In the left sidebar, click **Pages**.
3. Under **Build and deployment**:
   - **Source**: Select **GitHub Actions**.
4. The workflow at `.github/workflows/deploy-pages.yml` will automatically build and deploy the web version on every commit.
5. Your live web app URL will be:
   ```
   https://<your-github-username>.github.io/<repository-name>/
   ```
   (e.g., `https://shikhagain1992-oss.github.io/Nexchat_Pro/`)

### How to Download the Android APK from GitHub:
1. Open your GitHub repository in your browser.
2. Click the **Actions** tab.
3. Click the latest workflow run named **"Build NexChat Pro Android APK & AAB"**.
4. Scroll to the **Artifacts** section at the bottom of the summary page.
5. Click **NexChat-Pro-Debug-APK** to download the ready-to-install `.apk` file!

