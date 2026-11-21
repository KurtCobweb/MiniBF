BOBBY TASK STATUS UPDATE

Notification appearance
- Bobby's reminder notification uses the existing alarm_mascot.jpeg resource as its large icon (placeholder face/avatar).
- It includes two notification actions: "Doing it" and "Change of plan".

Task status behaviour
- New Bobby tasks start as SCHEDULED.
- Tapping "Doing it" changes the saved status to IN_PROGRESS.
- Tapping "Change of plan" changes the saved status to CHANGE_OF_PLAN.
- Open Bobby chat and tap "Today's Progress & Tasks" to see tasks and counts.
- Use "Mark Finished" beside a task to change it to FINISHED.
- A scheduled task whose time has passed is counted as UNFINISHED until its status is changed.
- Tasks marked Change of plan are counted separately, not as unfinished.
- The daily summary counts Finished, In Progress, Unfinished, Change of plan, and Not yet due.

Storage and operation
- Statuses are stored in the local SQLite database bobby_schedule.db.
- Database version 2 adds a status column and upgrades an existing version-1 Bobby database without deleting its reminder rows.
- No network connection is needed for these features.
- Existing study/water recurring reminders remain separate from Bobby's one-time reminders.

Avatar
- The notification large icon currently uses res/drawable-nodpi/alarm_mascot.jpeg.
- Replace that resource with Bobby's chosen face later, keeping the same resource name, or update the resource reference in BobbyAlarmReceiver.java.

Build note
- Android build was not verified in this environment because Gradle 9.6.0 could not be downloaded (network unavailable).

LATEST SCHEDULE / PROGRESS UPDATE
- In Bobby chat, use “View / Edit Schedule & Progress” to open a dedicated schedule and daily progress screen.
- Each saved task has Edit and Delete controls. Edit lets you change the task name, date and time; the old alarm is cancelled and a new one is scheduled.
- The progress summary counts Finished, In Progress, Unfinished, Change of plan, Not yet due, and total tasks for today.
- Every Bobby task has its own alarm and notification ID, so separately timed tasks notify separately.
- Notification text is “It's time to [task action]” and the large notification image uses alarm_mascot as the replaceable Bobby-face placeholder.
- The notification still provides “Doing it” and “Change of plan” actions.
