PIXEL COMPANION — INTEGRATED MINIBF PROJECT

WHAT WAS ADDED
- MainActivity.java: interactive schedule editor and default schedule loading.
- ReminderStore.java: persistent multi-slot model (24-hour hour/minute fields and weekday mask).
- ReminderScheduler.java: schedules each enabled slot using AlarmManager; uses exact alarms when permitted and an inexact fallback otherwise.
- ReminderReceiver.java: shows the matching Study or Water pixel character notification and schedules the next repeat.
- RescheduleReceiver.java: restores alarms after reboot, time change, or time-zone change.
- AndroidManifest.xml: launcher Activity, receiver declarations and required permissions.
- Existing XML layouts, three supplied character images and default_schedule.xml are retained.

DEFAULTS
Edit app/src/main/res/xml/default_schedule.xml before first install:
Study: 08:00, 10:00, 16:00
Water: 09:00, 11:00, 13:00, 15:00, 17:00
Each slot has hour, minute, days and enabled attributes. Time is stored as hour 0–23 and minute 0–59, not as a single timestamp string. User changes are saved in SharedPreferences and are not overwritten on app restart.

OPEN / BUILD
1. Extract the ZIP.
2. Open the MiniBF folder (the folder containing settings.gradle.kts) in Android Studio.
3. Allow Gradle sync. The project uses the original Gradle wrapper and dependency versions from the uploaded project.
4. Build > Make Project, then run on an Android 14+ device (the supplied project has minSdk 34).

ANDROID PERMISSIONS / BACKGROUND BEHAVIOR
- On Android 13+, allow notification permission when prompted.
- On Android 12+, allow the exact-alarm special access when the app asks. If denied, the app falls back to inexact alarms, which Android may delay.
- Android may impose vendor-specific battery restrictions. If reminders are delayed on a particular phone, set the app battery use to Unrestricted/Allow background activity in system settings.
- This app does not keep a permanent foreground service running. AlarmManager wakes the app for scheduled events, which is more battery-efficient for clock-time reminders.
- After a reboot, the boot receiver rebuilds the schedule. Device time and time-zone changes also trigger rescheduling.

LIMITATIONS / NOTES
- Notifications are delivered by Android; force-stopping the app prevents alarms until the app is opened again.
- Notification permission must be granted or reminders can fire without a visible notification.
- The first run defaults are seeded once and persisted. Editing default_schedule.xml later does not replace an already saved user schedule; clear app data or uninstall/reinstall to reseed defaults.
- The project archive supplied here could not be Gradle-built in this environment because its Gradle 9.6.0 distribution was not cached and network access to services.gradle.org was unavailable. Please run Gradle Sync/Build in Android Studio and share any exact error output if one appears.


FLOATING PIXEL CHARACTERS (added)
---------------------------------
When a scheduled reminder fires, enabling "Show floating characters and speak
reminders" lets the matching character slide in near the right edge, gently
float, show a reminder message, and speak it using Android TextToSpeech. The
overlay is draggable, has a Dismiss control, and auto-closes after 18 seconds.

Android permission required:
  Settings > Apps > Special app access > Display over other apps > MiniBF > Allow

The app asks to open this settings page when the floating-character switch is
enabled. If permission is not granted, the regular notification still works.
Android also shows a short-lived foreground-service notification while the
floating character is displayed. The feature is opt-in and is disabled by
default until the user enables it.

Implementation files:
  res/layout/floating_reminder.xml
  res/drawable/floating_reminder_background.xml
  java/.../FloatingReminderService.java
