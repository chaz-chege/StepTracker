# StepTracker tuning pass

Drop these files over your project (same paths). Nothing else changed. Build, then follow
"Test on the Redmi 10A" at the bottom.

## Bugs fixed (verified by reading the code, not by running the app)

1. Saved sessions always had 0 km. stopSession() zeroed the distance before building the
   SessionRecord, so history showed no distance and the "Pirate Hunter" 5 km bounty could never
   be earned. Numbers are now captured first.
2. Session calories were whole-day steps times 0.04, not the session. Now steps in the session
   times a per-step figure built from your weight and height.
3. The wake lock only ran if "Keep CPU Awake" was on (default off) and then expired after 10
   minutes and was never renewed. With no hardware step sensor the accelerometer stops
   delivering when the CPU sleeps, so screen-off walking was missed. The lock is now held
   whenever software counting is in use and renewed by a 30 s ticker.
4. A phone with a step-detector sensor but no step-counter sensor was double counted (detector
   plus accelerometer). Exactly one source is registered now.
5. After a reboot the hardware counter restarts at zero. The old check (new < old) missed the
   case where the new total was already bigger. Reboots are now detected with BOOT_COUNT.
6. Yesterday's totals were thrown away at midnight, so the weekly chart only showed steps from
   started sessions. Daily totals are stored now. The HUD also never reset at midnight until
   the next step.
7. Lifetime steps grew by 1 per batch, not per step, so batched hardware steps were undercounted.
   Milestones also used exact equality and could be skipped.
8. Sessions mixed network and GPS fixes, which added phantom distance. GPS only now, with a
   jitter filter, and step-based distance takes over indoors or without a fix.
9. Service state was touched from the main thread, the sensor thread and a coroutine at once.
   Everything now runs on the sensor thread.
10. On Android 14+ the service crashed if activity-recognition was denied. It now declares only
    foreground types whose permission is granted and stops cleanly otherwise.
11. Widget, sensor and GPS callbacks are wrapped so one failure cannot kill counting.
12. Totals were written with apply() and could be lost on a process kill. They are committed
    on the background thread and also saved when the app is swiped away or the service ends.
13. A manual session left running forever. It now ends after 60 minutes without steps.
14. Silent verification sessions were shown as active training in the UI. They no longer are.

## New step detector (StepDetector.kt)

Replaces the inline accelerometer logic. 50 Hz sampling (was the slow UI rate), sensor
timestamps instead of wall clock, adaptive threshold, 5 regular peaks before counting starts
(then credited retroactively), cadence tracking, missed-step recovery. Tuned with a simulator
(see StepDetectorTest.kt). Synthetic results at 50 Hz: normal walk 0%, brisk jog -1.3%,
walk-stop-walk -2.2%, sitting still 0 steps, rough-road vehicle 5 to 8 false steps in 5 min.
These come from generated signals, not from your phone.

## Other changes

- Notification opens the app when tapped.
- Manifest: removed SCHEDULE_EXACT_ALARM (inexact midnight alarm is enough), allowBackup=false
  (a restored hardware baseline would corrupt counting on a new phone), service restarts after
  an app update or reinstall.
- Sentinel worker and boot receiver check permission and cannot crash on a refused start.
- Xiaomi/Redmi first-run setup guide (KeepAliveGuide.kt) and a "Background setup guide" button
  in Settings.
- Settings changes now reach the service (daily goal, weights) without a restart.

## Test on the Redmi 10A

1. Open Settings. It shows whether the phone has a hardware step counter or "software
   fallback". Tell me which one you see.
2. Do the guide: Autostart ON, Battery saver "No restrictions", lock the app in Recents.
3. Walk exactly 100 steps with the phone in your pocket and screen off. Compare.
4. Repeat in hand, and sitting on a matatu or bus for 10 minutes (expect near zero).
5. Leave it for an hour in your pocket, then check the count. Check Settings > Battery for the
   app's drain.
