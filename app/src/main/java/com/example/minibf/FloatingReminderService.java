package com.example.minibf;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Property;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows the pixel character walking in from the right edge of the screen to the centre,
 * where it asks a yes/no question in an oval speech bubble (typed out letter by letter while
 * the text is spoken). After the answer it replies and shows a single tick-box. Ticking it
 * sends the character walking back off the screen and the service stops itself.
 *
 * Requires the "Display over other apps" permission.
 */
public class FloatingReminderService extends Service implements TextToSpeech.OnInitListener {
    private static final String SERVICE_CHANNEL = "pixel_companion_floating_service";
    private static final int SERVICE_NOTIFICATION_ID = 7319;

    /** Must match the card's layout_width in floating_reminder.xml. */
    private static final int CARD_WIDTH_DP = 280;
    private static final long WALK_IN_MS = 2800L;
    private static final long WALK_OUT_MS = 2200L;
    private static final long TYPE_DELAY_MS = 38L;
    /** Safety net: if nobody answers, the overlay removes itself. Reset on every interaction. */
    private static final long IDLE_TIMEOUT_MS = 2 * 60 * 1000L;
    /** Asked by the water character after the YES/NO reply. Slider range: WaterLevels.MAX_GULPS_PER_PROMPT. */
    private static final String GULP_QUESTION = "How many gulps of water did you drink?";

    /** One alarm's complete conversation. */
    private static final class Script {
        final int image;
        final String question, yesReply, noReply, doneLabel;
        final boolean asksGulps;   // water character: follow up with the gulp slider

        Script(int image, String question, String yesReply, String noReply, String doneLabel) {
            this(image, question, yesReply, noReply, doneLabel, false);
        }

        Script(int image, String question, String yesReply, String noReply, String doneLabel, boolean asksGulps) {
            this.asksGulps = asksGulps;
            this.image = image;
            this.question = question;
            this.yesReply = yesReply;
            this.noReply = noReply;
            this.doneLabel = doneLabel;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Animator> motion = new ArrayList<>();

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private View floatingView;
    private View floatingCard;
    private View bubbleGroup;
    private ImageView character;
    private TextView messageView;
    private View choiceRow;
    private Button yesButton, noButton;
    private CheckBox doneCheck;
    private View gulpRow;
    private SeekBar gulpSeek;
    private TextView gulpCount;
    private Button gulpOk;
    private boolean gulpsSubmitted = false;

    private Script script;
    private Animator walkAnimator;
    private Runnable typeRunnable;
    private Runnable idleRunnable;
    private boolean docked = false;     // card is centred in a wrap-content window (draggable)
    private boolean answered = false;
    private boolean leaving = false;

    private TextToSpeech tts;
    private String pendingSpeech;
    private boolean ttsReady = false;

    // ------------------------------------------------------------------ lifecycle

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        tts = new TextToSpeech(this, this);
        createServiceChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // A foreground notification satisfies Android's service execution deadline.
        Notification foreground = new NotificationCompat.Builder(this, SERVICE_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Pixel Companion")
                .setContentText("Showing your reminder character")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(SERVICE_NOTIFICATION_ID, foreground,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, foreground);
        }

        if (intent == null || !ReminderStore.floatingEnabled(this) || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String customMessage = intent.getStringExtra("custom_message");
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            // Bobby reminders use the existing floating character UI. The alarm mascot
            // is only a placeholder; the user can replace the artwork independently.
            script = new Script(R.drawable.alarm_mascot,
                    "It's time for " + customMessage + ".",
                    "Let's get it done!",
                    "Okay, I'll give you a moment.",
                    "Done! Thanks for checking in.");
        } else {
            boolean study = ReminderStore.STUDY.equals(intent.getStringExtra("type"));
            if (study) {
                script = new Script(R.drawable.study_character,
                        "Do you wanna surprise your BF?",
                        "Study hard for that nerd",
                        "Then quit doomscrolling and go study smh",
                        "Okay bbg, Imma lock in 💕");
            } else {
                script = new Script(R.drawable.water_character,
                        "Do you love your BF?",
                        "Then Drink Water",
                        "Then Dilute his memory by drinking water",
                        "Drank it bbg 💕",
                        true);
            }
        }

        removeOverlay();   // a new alarm replaces any overlay that is still showing
        showOverlay();
        resetIdleTimer();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    // ------------------------------------------------------------------ overlay setup

    private void showOverlay() {
        answered = false;
        leaving = false;
        docked = false;
        gulpsSubmitted = false;

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_reminder, null, false);
        floatingCard = floatingView.findViewById(R.id.floatingCard);
        bubbleGroup = floatingView.findViewById(R.id.floatingBubbleGroup);
        character = floatingView.findViewById(R.id.floatingCharacterImage);
        messageView = floatingView.findViewById(R.id.floatingReminderMessage);
        choiceRow = floatingView.findViewById(R.id.floatingChoiceRow);
        yesButton = floatingView.findViewById(R.id.floatingYes);
        noButton = floatingView.findViewById(R.id.floatingNo);
        doneCheck = floatingView.findViewById(R.id.floatingDoneCheck);
        gulpRow = floatingView.findViewById(R.id.floatingGulpRow);
        gulpSeek = floatingView.findViewById(R.id.floatingGulpSeek);
        gulpCount = floatingView.findViewById(R.id.floatingGulpCount);
        gulpOk = floatingView.findViewById(R.id.floatingGulpOk);
        gulpSeek.setMax(WaterLevels.MAX_GULPS_PER_PROMPT);
        gulpSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateGulpCount();
                if (fromUser) resetIdleTimer();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { resetIdleTimer(); }
            @Override public void onStopTrackingTouch(SeekBar bar) { resetIdleTimer(); }
        });
        gulpOk.setOnClickListener(v -> onGulpsConfirmed());

        character.setImageResource(script.image);
        messageView.setText("");

        yesButton.setOnClickListener(v -> onAnswer(true));
        noButton.setOnClickListener(v -> onAnswer(false));
        doneCheck.setOnCheckedChangeListener((b, checked) -> {
            if (!checked) return;
            if (script != null && script.asksGulps && !gulpsSubmitted) {
                onWaterDoneChecked();
            } else {
                finishHappy();
            }
        });
        floatingView.findViewById(R.id.floatingDismiss).setOnClickListener(v -> stopSelf());
        makeDraggable(floatingCard);

        // While walking, the window spans the screen width so the card can slide across it.
        // It is untouchable during the walk so it never blocks the app underneath.
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        windowParams.x = 0;
        windowParams.y = 0;

        floatingCard.setTranslationX(screenWidth());   // start fully off-screen on the right
        windowManager.addView(floatingView, windowParams);
        floatingView.post(this::walkIn);
    }

    private void removeOverlay() {
        cancelMotion();
        if (walkAnimator != null) { walkAnimator.cancel(); walkAnimator = null; }
        if (typeRunnable != null) { handler.removeCallbacks(typeRunnable); typeRunnable = null; }
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) { }
        }
        floatingView = null;
        floatingCard = null;
        if (tts != null) tts.stop();
    }

    // ------------------------------------------------------------------ walking

    private void walkIn() {
        if (floatingView == null) return;
        final int target = (screenWidth() - dp(CARD_WIDTH_DP)) / 2;
        startWalkCycle();
        walkAnimator = ObjectAnimator.ofFloat(floatingCard, View.TRANSLATION_X, screenWidth(), target);
        walkAnimator.setDuration(WALK_IN_MS);
        // Quick start, long gentle ease-out so the character slows down as it arrives.
        walkAnimator.setInterpolator(new PathInterpolator(0.3f, 0f, 0.2f, 1f));
        walkAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (cancelled || floatingView == null) return;
                stopCharacterMotion();
                dockAtCentre();
                popBubble();
            }
        });
        walkAnimator.start();
    }

    /** Shrinks the window to the card and centres it, so only the card receives touches. */
    private void dockAtCentre() {
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        windowParams.gravity = Gravity.CENTER;
        windowParams.x = 0;
        windowParams.y = 0;
        windowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        windowManager.updateViewLayout(floatingView, windowParams);
        // Reset the translation only once the window has actually been re-laid-out (no flicker).
        afterRelayout(() -> {
            floatingCard.setTranslationX(0f);
            docked = true;
        });
    }

    private void walkOut() {
        if (floatingView == null || leaving) return;
        leaving = true;
        docked = false;
        int[] loc = new int[2];
        floatingCard.getLocationOnScreen(loc);
        final int left = loc[0];

        windowParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        windowParams.x = 0;   // keep y so the character leaves along the line it was dragged to
        windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        windowManager.updateViewLayout(floatingView, windowParams);

        afterRelayout(() -> {
            floatingCard.setTranslationX(left);
            startWalkCycle();
            walkAnimator = ObjectAnimator.ofFloat(floatingCard, View.TRANSLATION_X, left, screenWidth());
            walkAnimator.setDuration(WALK_OUT_MS);
            walkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            walkAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(Animator a) { cancelled = true; }
                @Override public void onAnimationEnd(Animator a) { if (!cancelled) stopSelf(); }
            });
            walkAnimator.start();
        });
    }

    /** Runs {@code action} after the next layout pass of the overlay (with a short fallback). */
    private void afterRelayout(final Runnable action) {
        final View v = floatingView;
        final boolean[] done = {false};
        final Runnable once = () -> {
            if (done[0] || floatingView != v) return;
            done[0] = true;
            action.run();
        };
        v.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                ViewTreeObserver vto = v.getViewTreeObserver();
                if (vto.isAlive()) vto.removeOnGlobalLayoutListener(this);
                once.run();
            }
        });
        handler.postDelayed(once, 150);
    }

    // ------------------------------------------------------------------ character motion

    /** Walk cycle: a hop per step, a left/right sway, and a little stretch at the top of each hop. */
    private void startWalkCycle() {
        cancelMotion();
        character.setPivotX(character.getWidth() / 2f);
        character.setPivotY(character.getHeight());
        loop(View.TRANSLATION_Y, 0f, -dpf(9f), 210, new DecelerateInterpolator());
        loop(View.ROTATION, -3.5f, 3.5f, 420, new AccelerateDecelerateInterpolator());
        loop(View.SCALE_Y, 1f, 1.05f, 210, new DecelerateInterpolator());
    }

    /** Talking: a quick, small bounce while the text is being typed. */
    private void startTalkMotion() {
        cancelMotion();
        character.setPivotX(character.getWidth() / 2f);
        character.setPivotY(character.getHeight());
        loop(View.TRANSLATION_Y, 0f, -dpf(2.5f), 120, new AccelerateDecelerateInterpolator());
        loop(View.SCALE_Y, 1f, 1.03f, 120, new AccelerateDecelerateInterpolator());
    }

    private void loop(Property<View, Float> property, float from, float to, long ms, TimeInterpolator ip) {
        ObjectAnimator a = ObjectAnimator.ofFloat(character, property, from, to);
        a.setDuration(ms);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(ip);
        a.start();
        motion.add(a);
    }

    private void cancelMotion() {
        for (Animator a : motion) a.cancel();
        motion.clear();
    }

    /** Stops the looping animators and eases the character back to its resting pose. */
    private void stopCharacterMotion() {
        cancelMotion();
        if (character == null) return;
        character.animate().translationY(0f).rotation(0f).scaleX(1f).scaleY(1f)
                .setDuration(140).setInterpolator(new DecelerateInterpolator()).start();
    }

    // ------------------------------------------------------------------ conversation

    private void popBubble() {
        bubbleGroup.setPivotX(bubbleGroup.getWidth() / 2f);
        bubbleGroup.setPivotY(bubbleGroup.getHeight());   // grows out of the tail, toward the character
        bubbleGroup.setScaleX(0.4f);
        bubbleGroup.setScaleY(0.4f);
        bubbleGroup.setAlpha(0f);
        bubbleGroup.setVisibility(View.VISIBLE);
        bubbleGroup.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(320).setInterpolator(new OvershootInterpolator(1.6f))
                .withEndAction(() -> {
                    if (floatingView == null) return;
                    speak(script.question);
                    typeText(script.question, this::showChoices);
                }).start();
    }

    private void showChoices() {
        if (floatingView == null) return;
        choiceRow.setAlpha(0f);
        choiceRow.setTranslationY(dpf(6f));
        choiceRow.setVisibility(View.VISIBLE);
        choiceRow.animate().alpha(1f).translationY(0f).setDuration(200).start();
    }

    private void onAnswer(boolean yes) {
        if (answered || floatingView == null) return;
        answered = true;
        resetIdleTimer();
        yesButton.setEnabled(false);
        noButton.setEnabled(false);
        choiceRow.animate().alpha(0f).setDuration(120)
                .withEndAction(() -> { if (choiceRow != null) choiceRow.setVisibility(View.GONE); }).start();

        // Tiny "boing" on the bubble to react to the tap.
        bubbleGroup.animate().scaleX(1.04f).scaleY(1.04f).setDuration(90).withEndAction(() -> {
            if (bubbleGroup != null) bubbleGroup.animate().scaleX(1f).scaleY(1f)
                    .setDuration(160).setInterpolator(new OvershootInterpolator(2f)).start();
        }).start();

        String reply = yes ? script.yesReply : script.noReply;
        speak(reply);
        // The reply and the original completion checkbox belong together in bubble 2.
        // Water gulps are deliberately a separate, subsequent bubble.
        typeText(reply, this::showDoneCheck);
    }

    // ---- water: "How many gulps?" slider ------------------------------------------------

    /** The water checkbox advances to a distinct third bubble containing the gulp question. */
    private void onWaterDoneChecked() {
        if (floatingView == null || gulpsSubmitted) return;
        resetIdleTimer();
        doneCheck.setEnabled(false);
        bubbleGroup.animate().alpha(0f).scaleX(0.82f).scaleY(0.82f).setDuration(180)
                .withEndAction(() -> {
                    if (floatingView == null) return;
                    doneCheck.setVisibility(View.GONE);
                    choiceRow.setVisibility(View.GONE);
                    gulpRow.setVisibility(View.GONE);
                    messageView.setText("");
                    bubbleGroup.setScaleX(0.82f);
                    bubbleGroup.setScaleY(0.82f);
                    bubbleGroup.setAlpha(0f);
                    bubbleGroup.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(220).setInterpolator(new OvershootInterpolator(1.2f)).start();
                    speak(GULP_QUESTION);
                    typeText(GULP_QUESTION, this::showGulpRow);
                }).start();
    }

    private void showGulpRow() {
        if (floatingView == null) return;
        gulpSeek.setProgress(Math.min(1, WaterLevels.MAX_GULPS_PER_PROMPT));
        updateGulpCount();
        gulpOk.setEnabled(true);
        gulpSeek.setEnabled(true);
        gulpRow.setAlpha(0f);
        gulpRow.setTranslationY(dpf(6f));
        gulpRow.setVisibility(View.VISIBLE);
        gulpRow.animate().alpha(1f).translationY(0f).setDuration(200).start();
    }

    private void updateGulpCount() {
        int g = gulpSeek.getProgress();
        gulpCount.setText(g + (g == 1 ? " gulp" : " gulps"));
    }

    /** Saves the gulps to the water log (shown on the main screen meter), then says goodbye. */
    private void onGulpsConfirmed() {
        if (gulpsSubmitted || floatingView == null) return;
        gulpsSubmitted = true;
        resetIdleTimer();
        gulpOk.setEnabled(false);
        gulpSeek.setEnabled(false);
        WaterStore.addGulps(this, gulpSeek.getProgress());   // ignores 0
        gulpRow.animate().alpha(0f).setDuration(120)
                .withEndAction(() -> {
                    if (gulpRow != null) gulpRow.setVisibility(View.GONE);
                    // The "Drank it bbg" checkbox was confirmed in bubble 2.
                    finishCharacterConversation();
                }).start();
    }

    private void showDoneCheck() {
        if (floatingView == null) return;
        doneCheck.setText(script.doneLabel);
        doneCheck.setChecked(false);
        doneCheck.setEnabled(true);
        doneCheck.setAlpha(0f);
        doneCheck.setVisibility(View.VISIBLE);
        doneCheck.animate().alpha(1f).setDuration(220).start();
    }

    private void finishHappy() {
        if (floatingView == null || leaving) return;
        resetIdleTimer();
        doneCheck.setEnabled(false);
        finishCharacterConversation();
    }

    private void finishCharacterConversation() {
        if (floatingView == null || leaving) return;
        // Happy hop, then the bubble shrinks away and the character walks off.
        character.setPivotX(character.getWidth() / 2f);
        character.setPivotY(character.getHeight());
        character.animate().translationY(-dpf(18f)).setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (character != null) character.animate().translationY(0f).setDuration(240)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start();
                }).start();
        handler.postDelayed(() -> {
            if (floatingView == null) return;
            bubbleGroup.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(220)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(this::walkOut).start();
        }, 750);
    }

    /**
     * Types {@code full} into the bubble. The unrevealed part is drawn transparent rather than
     * missing, so the bubble keeps its final size and never jumps while typing.
     */
    private void typeText(final String full, final Runnable onDone) {
        if (typeRunnable != null) handler.removeCallbacks(typeRunnable);
        setRevealed(full, 0);
        startTalkMotion();
        final int[] shown = {0};
        typeRunnable = new Runnable() {
            @Override public void run() {
                if (floatingView == null) return;
                shown[0]++;
                // Never split an emoji's surrogate pair.
                if (shown[0] < full.length() && Character.isHighSurrogate(full.charAt(shown[0] - 1))) shown[0]++;
                int count = Math.min(shown[0], full.length());
                setRevealed(full, count);
                if (count < full.length()) {
                    char last = full.charAt(count - 1);
                    long delay = TYPE_DELAY_MS + ((last == ',' || last == '.') ? 140 : 0);
                    handler.postDelayed(this, delay);
                } else {
                    stopCharacterMotion();
                    if (onDone != null) onDone.run();
                }
            }
        };
        handler.postDelayed(typeRunnable, 120);
    }

    private void setRevealed(String full, int count) {
        SpannableString s = new SpannableString(full);
        if (count < full.length()) {
            s.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), count, full.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        messageView.setText(s);
    }

    // ------------------------------------------------------------------ dragging / idle

    private void makeDraggable(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;

            @Override public boolean onTouch(View v, MotionEvent event) {
                if (!docked || leaving || floatingView == null) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = windowParams.x;
                        startY = windowParams.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        resetIdleTimer();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        windowParams.x = startX + (int) (event.getRawX() - touchX);
                        windowParams.y = startY + (int) (event.getRawY() - touchY);
                        try { windowManager.updateViewLayout(floatingView, windowParams); }
                        catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                }
                return false;
            }
        });
    }

    private void resetIdleTimer() {
        if (idleRunnable != null) handler.removeCallbacks(idleRunnable);
        idleRunnable = this::stopSelf;
        handler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS);
    }

    // ------------------------------------------------------------------ speech

    private void speak(String text) {
        pendingSpeech = text;
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pixel-reminder-" + System.currentTimeMillis());
        }
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            tts.setLanguage(Locale.getDefault());
            tts.setPitch(0.5f);
            tts.setSpeechRate(0.99f);
            if (Build.VERSION.SDK_INT >= 21) {
                tts.setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
            // Only catch up if speech was requested before the engine was ready.
            if (pendingSpeech != null && floatingView != null) speak(pendingSpeech);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void createServiceChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(SERVICE_CHANNEL,
                    "Floating reminder display", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Temporary service notification while a reminder character is displayed");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private int screenWidth() { return getResources().getDisplayMetrics().widthPixels; }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private float dpf(float value) { return value * getResources().getDisplayMetrics().density; }
}
