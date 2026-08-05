package com.forsetigames.pulse100;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

final class PulseGameView extends View implements Choreographer.FrameCallback {
    private static final float W = 720f;
    private static final float H = 1280f;
    private static final float PLAY_TOP = 210f;
    private static final float PLAY_BOTTOM = 1080f;

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_PLAY = 1;
    private static final int SCREEN_PAUSE = 2;
    private static final int SCREEN_RESULT = 3;
    private static final int SCREEN_SETTINGS = 4;

    private static final int MODE_CLASSIC = 0;
    private static final int MODE_DAILY = 1;

    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_PRISM = 1;
    private static final int TYPE_CHARGED = 2;
    private static final int WHITE_PULSE = -1;

    private static final int BG = Color.rgb(5, 8, 23);
    private static final int PANEL = Color.rgb(16, 23, 53);
    private static final int TEXT = Color.rgb(247, 248, 255);
    private static final int MUTED = Color.rgb(144, 152, 187);
    private static final int CYAN = Color.rgb(56, 219, 255);
    private static final int PINK = Color.rgb(245, 80, 211);
    private static final int GOLD = Color.rgb(255, 210, 74);
    private static final int VIOLET = Color.rgb(155, 92, 255);
    private static final int GREEN = Color.rgb(83, 238, 153);
    private static final int[] COLORS = {CYAN, PINK, GOLD, VIOLET};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<Pulse> pulses = new ArrayList<>();
    private final ArrayList<Pulse> pendingPulses = new ArrayList<>();
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final ArrayList<Star> stars = new ArrayList<>();
    private final SharedPreferences preferences;
    private final Vibrator vibrator;
    private ToneGenerator tone;

    private final RectF classicButton = new RectF(105, 535, 615, 635);
    private final RectF dailyButton = new RectF(105, 660, 615, 760);
    private final RectF settingsButton = new RectF(235, 795, 485, 865);
    private final RectF pauseButton = new RectF(630, 48, 686, 104);
    private final RectF primaryButton = new RectF(115, 900, 605, 990);
    private final RectF secondaryButton = new RectF(115, 1010, 605, 1085);
    private final RectF homeButton = new RectF(115, 1100, 605, 1168);
    private final RectF vibrationButton = new RectF(120, 470, 600, 560);
    private final RectF effectsButton = new RectF(120, 590, 600, 680);

    private int screen = SCREEN_HOME;
    private int mode = MODE_CLASSIC;
    private int level = 1;
    private int bestRating = 0;
    private int dailyBest = 0;
    private int roundIndex = 0;
    private int roundScore = 0;
    private int roundTarget = 0;
    private int totalScore = 0;
    private int totalTarget = 0;
    private int totalPossible = 0;
    private int combo = 0;
    private int bestCombo = 0;
    private int nextPulseId = 1;
    private long runSeed = 0;
    private String dailyCode = "";
    private boolean roundStarted = false;
    private boolean runWon = false;
    private boolean haptics = true;
    private boolean reducedFx = false;
    private float finishDelay = -1f;

    private boolean running = false;
    private long lastFrameNanos = 0;
    private float viewScale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    PulseGameView(Context context) {
        super(context);
        setFocusable(true);
        setKeepScreenOn(true);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        preferences = context.getSharedPreferences("pulse100", Context.MODE_PRIVATE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 35);
        } catch (RuntimeException ignored) {
            tone = null;
        }
        loadSave();
        createStars();
    }

    void resumeLoop() {
        if (running) return;
        running = true;
        lastFrameNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    void pauseLoop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        if (screen == SCREEN_PLAY) screen = SCREEN_PAUSE;
    }

    boolean handleBack() {
        if (screen == SCREEN_HOME) return false;
        if (screen == SCREEN_PLAY) {
            screen = SCREEN_PAUSE;
        } else {
            screen = SCREEN_HOME;
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        pauseLoop();
        if (tone != null) {
            tone.release();
            tone = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        viewScale = Math.min(w / W, h / H);
        offsetX = (w - W * viewScale) * 0.5f;
        offsetY = (h - H * viewScale) * 0.5f;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        float delta = lastFrameNanos == 0 ? 1f / 60f : (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = frameTimeNanos;
        delta = Math.min(delta, 0.05f);
        updateStars(delta);
        if (screen == SCREEN_PLAY) updateGame(delta);
        else updateSparks(delta);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        float x = (event.getX() - offsetX) / viewScale;
        float y = (event.getY() - offsetY) / viewScale;
        tap(x, y);
        return true;
    }

    private void tap(float x, float y) {
        switch (screen) {
            case SCREEN_HOME:
                if (classicButton.contains(x, y)) startRun(MODE_CLASSIC);
                else if (dailyButton.contains(x, y)) startRun(MODE_DAILY);
                else if (settingsButton.contains(x, y)) screen = SCREEN_SETTINGS;
                break;
            case SCREEN_PLAY:
                if (pauseButton.contains(x, y)) screen = SCREEN_PAUSE;
                else if (!roundStarted && y >= PLAY_TOP && y <= PLAY_BOTTOM) startPulse(x, y);
                break;
            case SCREEN_PAUSE:
                if (primaryButton.contains(x, y)) screen = SCREEN_PLAY;
                else if (homeButton.contains(x, y)) screen = SCREEN_HOME;
                break;
            case SCREEN_RESULT:
                if (primaryButton.contains(x, y)) {
                    if (runWon) startRun(mode);
                    else restartSameSeed();
                } else if (secondaryButton.contains(x, y)) {
                    startRun(mode);
                } else if (homeButton.contains(x, y)) {
                    screen = SCREEN_HOME;
                }
                break;
            case SCREEN_SETTINGS:
                if (vibrationButton.contains(x, y)) {
                    haptics = !haptics;
                    vibrate(25);
                    save();
                } else if (effectsButton.contains(x, y)) {
                    reducedFx = !reducedFx;
                    save();
                } else if (homeButton.contains(x, y)) {
                    screen = SCREEN_HOME;
                }
                break;
            default:
                break;
        }
        invalidate();
    }

    private void startRun(int newMode) {
        mode = newMode;
        screen = SCREEN_PLAY;
        roundIndex = 0;
        totalScore = 0;
        totalTarget = 0;
        totalPossible = 0;
        bestCombo = 0;
        runWon = false;
        if (mode == MODE_DAILY) {
            Calendar c = Calendar.getInstance();
            dailyCode = String.format(Locale.US, "%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            runSeed = Long.parseLong(dailyCode);
            dailyBest = preferences.getInt("daily_" + dailyCode, 0);
        } else {
            runSeed = System.currentTimeMillis() ^ ((long) level * 982451653L);
        }
        prepareRound();
        playTone(ToneGenerator.TONE_PROP_ACK, 90);
    }

    private void restartSameSeed() {
        screen = SCREEN_PLAY;
        roundIndex = 0;
        totalScore = 0;
        totalTarget = 0;
        totalPossible = 0;
        bestCombo = 0;
        runWon = false;
        prepareRound();
    }

    private void prepareRound() {
        particles.clear();
        pulses.clear();
        pendingPulses.clear();
        sparks.clear();
        roundScore = 0;
        combo = 0;
        roundStarted = false;
        finishDelay = -1f;
        random.setSeed(runSeed + roundIndex * 104729L);

        int effectiveLevel = mode == MODE_CLASSIC ? level : 9 + roundIndex * 3;
        int count = clamp(25 + effectiveLevel * 2 + roundIndex * 4, 28, 88);
        int colorCount = effectiveLevel >= 16 ? 4 : effectiveLevel >= 8 ? 3 : effectiveLevel >= 4 ? 2 : 1;
        int prismCount = effectiveLevel >= 6 ? Math.min(5, 1 + effectiveLevel / 9) : 0;
        int chargedCount = effectiveLevel >= 12 ? Math.min(4, 1 + effectiveLevel / 14) : 0;

        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.type = TYPE_NORMAL;
            p.radius = range(11f, 16f);
            if (i < prismCount) {
                p.type = TYPE_PRISM;
                p.radius = 18f;
            } else if (i < prismCount + chargedCount) {
                p.type = TYPE_CHARGED;
                p.radius = 19f;
            }
            float angle = range(0f, (float) (Math.PI * 2));
            float speed = range(35f + effectiveLevel, 72f + effectiveLevel * 1.6f);
            p.x = range(40f, W - 40f);
            p.y = range(PLAY_TOP + 35f, PLAY_BOTTOM - 35f);
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            p.colorId = random.nextInt(colorCount);
            p.alive = true;
            p.phase = range(0f, (float) (Math.PI * 2));
            particles.add(p);
        }

        float ratio = colorCount == 1 ? 0.54f : colorCount == 2 ? 0.42f : colorCount == 3 ? 0.32f : 0.27f;
        roundTarget = (int) Math.ceil(count * ratio);
        totalTarget += roundTarget;
        totalPossible += count;
    }

    private void startPulse(float x, float y) {
        roundStarted = true;
        pulses.add(new Pulse(nextPulseId++, x, y, WHITE_PULSE, 132f, 0));
        burst(x, y, TEXT, 18);
        vibrate(20);
        playTone(ToneGenerator.TONE_PROP_BEEP, 70);
    }

    private void updateGame(float delta) {
        moveParticles(delta);
        updatePulses(delta);
        updateSparks(delta);
        if (finishDelay >= 0f) {
            finishDelay -= delta;
            if (finishDelay <= 0f) finishRound();
        } else if (roundStarted && pulses.isEmpty()) {
            finishDelay = 0.65f;
        }
    }

    private void moveParticles(float delta) {
        for (Particle p : particles) {
            if (!p.alive) continue;
            p.x += p.vx * delta;
            p.y += p.vy * delta;
            p.phase += delta * 2f;
            if (p.x < p.radius) {
                p.x = p.radius;
                p.vx = Math.abs(p.vx);
            } else if (p.x > W - p.radius) {
                p.x = W - p.radius;
                p.vx = -Math.abs(p.vx);
            }
            if (p.y < PLAY_TOP + p.radius) {
                p.y = PLAY_TOP + p.radius;
                p.vy = Math.abs(p.vy);
            } else if (p.y > PLAY_BOTTOM - p.radius) {
                p.y = PLAY_BOTTOM - p.radius;
                p.vy = -Math.abs(p.vy);
            }
        }
    }

    private void updatePulses(float delta) {
        pendingPulses.clear();
        Iterator<Pulse> iterator = pulses.iterator();
        while (iterator.hasNext()) {
            Pulse pulse = iterator.next();
            if (!pulse.closing) {
                pulse.radius += pulse.speed * delta;
                if (pulse.radius >= pulse.maxRadius) pulse.closing = true;
            } else {
                pulse.radius -= pulse.speed * 0.48f * delta;
                pulse.alpha -= delta * 1.45f;
            }
            if (pulse.radius <= 0f || pulse.alpha <= 0f) {
                iterator.remove();
                continue;
            }
            testHits(pulse);
        }
        pulses.addAll(pendingPulses);
    }

    private void testHits(Pulse pulse) {
        for (Particle p : particles) {
            if (!p.alive || p.lastPulseId == pulse.id) continue;
            float dx = p.x - pulse.x;
            float dy = p.y - pulse.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            float gap = Math.abs(distance - pulse.radius);
            if (gap > p.radius + 8f) continue;
            if (pulse.colorId != WHITE_PULSE && p.type != TYPE_PRISM && pulse.colorId != p.colorId) continue;
            p.lastPulseId = pulse.id;
            if (p.type == TYPE_CHARGED && p.hits == 0) {
                p.hits = 1;
                burst(p.x, p.y, COLORS[p.colorId], 6);
                continue;
            }
            triggerParticle(p, pulse);
        }
    }

    private void triggerParticle(Particle p, Pulse source) {
        p.alive = false;
        roundScore++;
        totalScore++;
        combo++;
        bestCombo = Math.max(bestCombo, combo);
        int nextColor = p.type == TYPE_PRISM ? WHITE_PULSE : p.colorId;
        float radius = 88f + Math.min(32f, source.generation * 3f);
        if (p.type == TYPE_CHARGED) radius += 25f;
        pendingPulses.add(new Pulse(nextPulseId++, p.x, p.y, nextColor, radius, source.generation + 1));
        burst(p.x, p.y, nextColor == WHITE_PULSE ? TEXT : COLORS[nextColor], reducedFx ? 8 : 16);
        if (combo == 10 || combo == 25 || combo == 50 || combo == 75) {
            vibrate(30);
            playTone(ToneGenerator.TONE_PROP_ACK, 75);
        }
    }

    private void burst(float x, float y, int color, int amount) {
        for (int i = 0; i < amount; i++) {
            float angle = range(0f, (float) (Math.PI * 2));
            float speed = range(50f, 175f);
            Spark spark = new Spark();
            spark.x = x;
            spark.y = y;
            spark.vx = (float) Math.cos(angle) * speed;
            spark.vy = (float) Math.sin(angle) * speed;
            spark.life = range(0.3f, 0.75f);
            spark.maxLife = spark.life;
            spark.size = range(1.5f, 4f);
            spark.color = color;
            sparks.add(spark);
        }
    }

    private void updateSparks(float delta) {
        Iterator<Spark> iterator = sparks.iterator();
        while (iterator.hasNext()) {
            Spark s = iterator.next();
            s.x += s.vx * delta;
            s.y += s.vy * delta;
            s.vx *= 0.96f;
            s.vy *= 0.96f;
            s.life -= delta;
            if (s.life <= 0f) iterator.remove();
        }
    }

    private void finishRound() {
        boolean passed = roundScore >= roundTarget;
        vibrate(passed ? 42 : 18);
        playTone(passed ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK, 110);
        roundIndex++;
        if (roundIndex < 3) prepareRound();
        else finishRun();
    }

    private void finishRun() {
        runWon = totalScore >= totalTarget;
        int rating = rating();
        bestRating = Math.max(bestRating, rating);
        if (mode == MODE_DAILY) dailyBest = Math.max(dailyBest, rating);
        else if (runWon) level++;
        save();
        screen = SCREEN_RESULT;
        vibrate(runWon ? 70 : 25);
        playTone(runWon ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK, 180);
    }

    private int rating() {
        if (totalPossible <= 0) return 0;
        return clamp(Math.round(totalScore * 100f / totalPossible), 0, 100);
    }

    private void createStars() {
        random.setSeed(771337L);
        for (int i = 0; i < 64; i++) {
            Star s = new Star();
            s.x = range(0f, W);
            s.y = range(0f, H);
            s.size = range(0.8f, 2.4f);
            s.alpha = range(0.12f, 0.5f);
            s.speed = range(3f, 10f);
            stars.add(s);
        }
    }

    private void updateStars(float delta) {
        for (Star s : stars) {
            s.y += s.speed * delta;
            if (s.y > H) s.y = 0f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BG);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(viewScale, viewScale);
        drawBackground(canvas);
        if (screen == SCREEN_HOME) drawHome(canvas);
        else if (screen == SCREEN_PLAY) drawPlay(canvas);
        else if (screen == SCREEN_PAUSE) drawPause(canvas);
        else if (screen == SCREEN_RESULT) drawResult(canvas);
        else drawSettings(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        for (Star s : stars) {
            paint.setColor(withAlpha(Color.rgb(115, 148, 255), s.alpha));
            canvas.drawCircle(s.x, s.y, s.size, paint);
        }
        paint.setStrokeWidth(1f);
        paint.setColor(withAlpha(Color.rgb(38, 51, 107), 0.10f));
        for (int y = 235; y < 1110; y += 72) canvas.drawLine(0, y, W, y, paint);
        paint.setColor(withAlpha(Color.rgb(38, 51, 107), 0.08f));
        for (int x = 0; x < 720; x += 72) canvas.drawLine(x, 220, x, 1090, paint);
    }

    private void drawHome(Canvas canvas) {
        centerText(canvas, "PULSE", 165, 82, CYAN, true);
        centerText(canvas, "100", 250, 92, PINK, true);
        centerText(canvas, "UM TOQUE. UMA REAÇÃO.", 332, 23, MUTED, false);
        float cx = W / 2f;
        float cy = 435f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        for (int i = 0; i < 4; i++) {
            paint.setColor(COLORS[i]);
            RectF oval = new RectF(cx - 88 + i * 15, cy - 88 + i * 15, cx + 88 - i * 15, cy + 88 - i * 15);
            canvas.drawArc(oval, -25 + i * 28, 265 - i * 18, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TEXT);
        canvas.drawCircle(cx, cy, 12, paint);
        drawButton(canvas, classicButton, "JOGAR", VIOLET, 27);
        drawButton(canvas, dailyButton, "DESAFIO DIÁRIO", CYAN, 25);
        drawButton(canvas, settingsButton, "AJUSTES", PANEL, 22);
        centerText(canvas, "RECORDE " + bestRating + "/100", 960, 28, GOLD, true);
        centerText(canvas, "NÍVEL " + level, 1005, 22, MUTED, false);
        centerText(canvas, "OFFLINE • UM DEDO • SEM CADASTRO", 1200, 17, MUTED, false);
    }

    private void drawPlay(Canvas canvas) {
        drawText(canvas, mode == MODE_CLASSIC ? "NÍVEL " + level : "DIÁRIO", 35, 62, 24, VIOLET, true);
        centerText(canvas, "RODADA " + (roundIndex + 1) + "/3", 65, 20, MUTED, false);
        centerText(canvas, String.valueOf(roundScore), 132, 68, TEXT, true);
        centerText(canvas, "OBJETIVO " + roundTarget, 177, 22, CYAN, true);
        drawText(canvas, "COMBO", 535, 122, 18, MUTED, false);
        drawText(canvas, combo + "x", 548, 164, 38, PINK, true);
        drawButton(canvas, pauseButton, "Ⅱ", PANEL, 22);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(Color.rgb(5, 8, 26), 0.45f));
        canvas.drawRect(0, PLAY_TOP, W, PLAY_BOTTOM, paint);

        for (Pulse pulse : pulses) drawPulse(canvas, pulse);
        for (Particle particle : particles) if (particle.alive) drawParticle(canvas, particle);
        for (Spark spark : sparks) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(spark.color, Math.max(0f, spark.life / spark.maxLife)));
            canvas.drawCircle(spark.x, spark.y, spark.size, paint);
        }

        if (!roundStarted) {
            float cx = W / 2f;
            float cy = 690f;
            float radius = 30f + (float) Math.sin(System.currentTimeMillis() / 250.0) * 7f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(TEXT);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, 7f, paint);
            centerText(canvas, "TOQUE UMA VEZ", 790, 24, TEXT, true);
            centerText(canvas, "Escolha o ponto perfeito", 832, 18, MUTED, false);
        }
    }

    private void drawPause(Canvas canvas) {
        drawPlay(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(Color.rgb(2, 5, 18), 0.86f));
        canvas.drawRect(0, 0, W, H, paint);
        centerText(canvas, "PAUSADO", 360, 52, TEXT, true);
        drawButton(canvas, primaryButton, "CONTINUAR", VIOLET, 27);
        drawButton(canvas, homeButton, "INÍCIO", PANEL, 24);
    }

    private void drawResult(Canvas canvas) {
        centerText(canvas, "PULSE 100", 155, 55, CYAN, true);
        int resultColor = runWon ? GREEN : PINK;
        centerText(canvas, String.valueOf(rating()), 360, 150, resultColor, true);
        centerText(canvas, "/100", 438, 36, MUTED, false);
        centerText(canvas, runWon ? "VITÓRIA" : "QUASE!", 520, 38, resultColor, true);
        centerText(canvas, totalScore + " reações • combo " + bestCombo + "x", 585, 23, TEXT, false);
        centerText(canvas, "Objetivo total: " + totalTarget, 625, 19, MUTED, false);
        if (mode == MODE_DAILY) {
            centerText(canvas, "DESAFIO " + dailyCode, 690, 22, CYAN, true);
            centerText(canvas, "Melhor de hoje: " + dailyBest + "/100", 730, 19, MUTED, false);
        } else {
            centerText(canvas, "RECORDE: " + bestRating + "/100", 705, 22, GOLD, true);
        }
        drawButton(canvas, primaryButton, runWon ? "JOGAR NOVAMENTE" : "TENTAR NOVAMENTE", VIOLET, 24);
        drawButton(canvas, secondaryButton, "NOVA CONFIGURAÇÃO", PANEL, 22);
        drawButton(canvas, homeButton, "INÍCIO", Color.rgb(23, 29, 56), 22);
        centerText(canvas, "Faça uma captura e desafie seus amigos", 1210, 17, MUTED, false);
    }

    private void drawSettings(Canvas canvas) {
        centerText(canvas, "AJUSTES", 185, 54, TEXT, true);
        drawToggle(canvas, vibrationButton, "VIBRAÇÃO", haptics);
        drawToggle(canvas, effectsButton, "EFEITOS REDUZIDOS", reducedFx);
        drawButton(canvas, homeButton, "VOLTAR", PANEL, 24);
        centerText(canvas, "Pulse 100 v1.0.0", 1215, 17, MUTED, false);
    }

    private void drawPulse(Canvas canvas, Pulse pulse) {
        int color = pulse.colorId == WHITE_PULSE ? TEXT : COLORS[pulse.colorId];
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(14f);
        paint.setColor(withAlpha(color, pulse.alpha * 0.18f));
        canvas.drawCircle(pulse.x, pulse.y, pulse.radius, paint);
        paint.setStrokeWidth(3f);
        paint.setColor(withAlpha(color, pulse.alpha));
        canvas.drawCircle(pulse.x, pulse.y, pulse.radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawParticle(Canvas canvas, Particle p) {
        float radius = p.radius * (1f + (float) Math.sin(p.phase * 2f) * 0.05f);
        if (p.type == TYPE_PRISM) {
            Path path = new Path();
            path.moveTo(p.x, p.y - radius);
            path.lineTo(p.x + radius, p.y);
            path.lineTo(p.x, p.y + radius);
            path.lineTo(p.x - radius, p.y);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(TEXT, 0.12f));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(TEXT);
            canvas.drawPath(path, paint);
            paint.setStrokeWidth(3f);
            for (int i = 0; i < 4; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 2;
                paint.setColor(COLORS[i]);
                canvas.drawLine(p.x, p.y, p.x + (float) Math.cos(angle) * radius, p.y + (float) Math.sin(angle) * radius, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        int color = COLORS[p.colorId];
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, 0.06f));
        canvas.drawCircle(p.x, p.y, radius * 2.1f, paint);
        paint.setColor(withAlpha(color, 0.14f));
        canvas.drawCircle(p.x, p.y, radius * 1.5f, paint);
        paint.setColor(color);
        canvas.drawCircle(p.x, p.y, radius, paint);
        paint.setColor(withAlpha(TEXT, 0.72f));
        canvas.drawCircle(p.x - radius * 0.28f, p.y - radius * 0.30f, radius * 0.27f, paint);
        if (p.type == TYPE_CHARGED) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(TEXT);
            canvas.drawCircle(p.x, p.y, radius + 8f, paint);
            paint.setStyle(Paint.Style.FILL);
            if (p.hits > 0) canvas.drawCircle(p.x, p.y, radius * 0.4f, paint);
        }
    }

    private void drawButton(Canvas canvas, RectF rect, String label, int color, int textSize) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, 0.78f));
        canvas.drawRoundRect(rect, 24, 24, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(lighten(color, 0.28f));
        canvas.drawRoundRect(rect, 24, 24, paint);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        textPaint.setColor(TEXT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, rect.centerX(), baseline, textPaint);
    }

    private void drawToggle(Canvas canvas, RectF rect, String label, boolean enabled) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        canvas.drawRoundRect(rect, 22, 22, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(withAlpha(Color.rgb(77, 102, 191), 0.7f));
        canvas.drawRoundRect(rect, 22, 22, paint);
        paint.setStyle(Paint.Style.FILL);
        drawText(canvas, label, rect.left + 25, rect.centerY() + 9, 23, TEXT, true);
        RectF pill = new RectF(rect.right - 115, rect.top + 24, rect.right - 37, rect.top + 66);
        paint.setColor(enabled ? GREEN : Color.rgb(52, 59, 88));
        canvas.drawRoundRect(pill, 22, 22, paint);
        paint.setColor(TEXT);
        canvas.drawCircle(enabled ? pill.right - 21 : pill.left + 21, pill.centerY(), 16, paint);
    }

    private void centerText(Canvas canvas, String text, float y, float size, int color, boolean bold) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        textPaint.setTypeface(android.graphics.Typeface.create("sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        canvas.drawText(text, W / 2f, y, textPaint);
    }

    private void drawText(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        textPaint.setTypeface(android.graphics.Typeface.create("sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        canvas.drawText(text, x, y, textPaint);
    }

    private void vibrate(int milliseconds) {
        if (!haptics || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(milliseconds);
            }
        } catch (SecurityException ignored) {
        }
    }

    private void playTone(int toneCode, int durationMs) {
        if (tone == null) return;
        try {
            tone.startTone(toneCode, durationMs);
        } catch (RuntimeException ignored) {
        }
    }

    private void loadSave() {
        level = Math.max(1, preferences.getInt("level", 1));
        bestRating = clamp(preferences.getInt("best", 0), 0, 100);
        haptics = preferences.getBoolean("haptics", true);
        reducedFx = preferences.getBoolean("reduced_fx", false);
    }

    private void save() {
        SharedPreferences.Editor editor = preferences.edit()
                .putInt("level", level)
                .putInt("best", bestRating)
                .putBoolean("haptics", haptics)
                .putBoolean("reduced_fx", reducedFx);
        if (!dailyCode.isEmpty()) editor.putInt("daily_" + dailyCode, dailyBest);
        editor.apply();
    }

    private float range(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, float alpha) {
        int a = clamp(Math.round(alpha * 255f), 0, 255);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int lighten(int color, float amount) {
        int r = Color.red(color) + Math.round((255 - Color.red(color)) * amount);
        int g = Color.green(color) + Math.round((255 - Color.green(color)) * amount);
        int b = Color.blue(color) + Math.round((255 - Color.blue(color)) * amount);
        return Color.rgb(clamp(r, 0, 255), clamp(g, 0, 255), clamp(b, 0, 255));
    }

    private static final class Particle {
        float x, y, vx, vy, radius, phase;
        int colorId, type, hits, lastPulseId;
        boolean alive;
    }

    private static final class Pulse {
        final int id;
        final float x, y, maxRadius, speed;
        final int colorId, generation;
        float radius = 3f;
        float alpha = 1f;
        boolean closing = false;

        Pulse(int id, float x, float y, int colorId, float maxRadius, int generation) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.colorId = colorId;
            this.maxRadius = maxRadius;
            this.generation = generation;
            this.speed = 255f;
        }
    }

    private static final class Spark {
        float x, y, vx, vy, life, maxLife, size;
        int color;
    }

    private static final class Star {
        float x, y, size, alpha, speed;
    }
}
