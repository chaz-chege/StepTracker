# Widget fix

## What was wrong (from reading the code)

1. The old layout needed close to 300 dp of height but the widget was declared 110 dp tall, so on a
   normal 2-row widget the bottom was cut off.
2. The service rebuilt the widget on every step with setBackgroundColor. That replaced the rounded
   gradient card with a flat rectangle and left the picture with square corners. It also flattened
   the START and STOP buttons and did not re-attach their click handlers, which some launchers
   (MIUI included) treat as "no click handler".
3. Six of the seven character pictures sit in drawable/ and not drawable-nodpi/. Android scales
   them up for the screen density: 1367 x 768 becomes about 2700 x 1500, roughly 13 to 17 MB
   per picture on a Redmi-class screen, decoded inside the launcher process.
4. The widget loaded a 3.7 MB Japanese font for Latin-only text.
5. Fixed sp text sizes overflow when the phone font size is raised.

## What changed

- WidgetRenderer.kt (new): builds the whole widget in one place, click handlers included.
- StepTrackerWidget.kt: now only calls WidgetRenderer (also re-draws on resize).
- widget_step_tracker.xml: one layout for every size. Stats are three columns instead of three
  full rows. Short widgets hide the quote, then the title, then the stats row, so nothing is
  clipped (sizes in dp: stats from 150, title from 165, quote from 200). Text auto-shrinks to fit.
  Buttons are rounded and tinted per character.
- The background picture is drawn at the size it is shown (about 1 MB) with rounded corners.
- step_tracker_widget_info.xml: default size 3 x 2 cells, minimum 140 x 110 dp.
- rampart_one.ttf: reduced from 3.7 MB to 180 KB (all Latin letters, digits and punctuation kept;
  the emoji and the baht sign were never in this font and still fall back to the system font).

## Two manual steps

1. In StepListenerService.kt replace the whole updateWidget function with:

    private fun updateWidget(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && (now - lastWidgetUpdateMs < UI_THROTTLE_MS || !isScreenOn())) {
            uiDirty = true
            return
        }
        lastWidgetUpdateMs = now
        uiDirty = false
        WidgetRenderer.update(this)
    }

   (If your copy of the service differs, keep your own throttling and replace only what builds the
   RemoteViews with the single line WidgetRenderer.update(this).) Then delete the imports that are
   no longer used: AppWidgetManager, ComponentName, RemoteViews, View, Color, toArgb, BandageWhite,
   OnigiriBlack. Android Studio greys them out.

2. Move the six pictures to the no-density folder (PowerShell, from the project root):

    cd app\src\main\res
    New-Item -ItemType Directory -Force drawable-nodpi | Out-Null
    "chopper","luffy","law","nami","sanji","shanks" | ForEach-Object { Move-Item "drawable\${_}_bg.png" drawable-nodpi\ }

   zoro_bg.png is already there. No code changes needed, R.drawable names stay the same. This helps
   the app screens too, not just the widget.

Then remove the widget from the home screen and add it again so the launcher inflates the new layout.

## Not verified

I cannot run Android here. The code compiles against the Android 34 API and the layout was checked
for well-formed XML, allowed widget view classes and matching ids, but I have not seen it rendered
on a Redmi. Send a screenshot if anything still looks off.
