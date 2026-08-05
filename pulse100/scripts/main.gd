extends Node2D

const W := 720.0
const H := 1280.0
const PLAY_TOP := 210.0
const PLAY_BOTTOM := 1080.0
const SAVE_PATH := "user://pulse100.cfg"

const BG := Color("050817")
const PANEL := Color("101735")
const TEXT := Color("f7f8ff")
const MUTED := Color("9098bb")
const CYAN := Color("38dbff")
const PINK := Color("f550d3")
const GOLD := Color("ffd24a")
const VIOLET := Color("9b5cff")
const GREEN := Color("53ee99")
const COLORS := [CYAN, PINK, GOLD, VIOLET]

const TYPE_NORMAL := 0
const TYPE_PRISM := 1
const TYPE_CHARGED := 2
const WHITE_PULSE := -1

enum Screen { HOME, PLAY, PAUSE, RESULT, SETTINGS }
enum Mode { CLASSIC, DAILY }

var screen: Screen = Screen.HOME
var mode: Mode = Mode.CLASSIC
var rng := RandomNumberGenerator.new()
var particles: Array = []
var pulses: Array = []
var sparks: Array = []
var stars: Array = []

var level := 1
var best_rating := 0
var round_index := 0
var round_score := 0
var round_target := 0
var total_score := 0
var total_target := 0
var total_possible := 0
var combo := 0
var best_combo := 0
var round_started := false
var finish_delay := -1.0
var run_won := false
var run_seed := 0
var daily_code := ""
var daily_best := 0
var haptics := true
var reduced_fx := false

var classic_button := Rect2(105, 535, 510, 100)
var daily_button := Rect2(105, 660, 510, 100)
var settings_button := Rect2(235, 795, 250, 70)
var pause_button := Rect2(630, 48, 56, 56)
var primary_button := Rect2(115, 900, 490, 90)
var secondary_button := Rect2(115, 1010, 490, 75)
var home_button := Rect2(115, 1100, 490, 68)

func _ready() -> void:
    _load_save()
    _create_stars()
    queue_redraw()

func _process(delta: float) -> void:
    _update_stars(delta)
    if screen == Screen.PLAY:
        _update_game(delta)
    else:
        _update_sparks(delta)
    queue_redraw()

func _unhandled_input(event: InputEvent) -> void:
    var pos := Vector2.ZERO
    var pressed := false
    if event is InputEventScreenTouch and event.pressed:
        pos = event.position
        pressed = true
    elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
        pos = event.position
        pressed = true
    if pressed:
        _tap(pos)

func _tap(pos: Vector2) -> void:
    match screen:
        Screen.HOME:
            if classic_button.has_point(pos):
                _start_run(Mode.CLASSIC)
            elif daily_button.has_point(pos):
                _start_run(Mode.DAILY)
            elif settings_button.has_point(pos):
                screen = Screen.SETTINGS
        Screen.PLAY:
            if pause_button.has_point(pos):
                screen = Screen.PAUSE
            elif not round_started and pos.y >= PLAY_TOP and pos.y <= PLAY_BOTTOM:
                _start_pulse(pos)
        Screen.PAUSE:
            if primary_button.has_point(pos):
                screen = Screen.PLAY
            elif home_button.has_point(pos):
                screen = Screen.HOME
        Screen.RESULT:
            if primary_button.has_point(pos):
                if run_won:
                    _start_run(mode)
                else:
                    _restart_same_seed()
            elif secondary_button.has_point(pos):
                _start_run(mode)
            elif home_button.has_point(pos):
                screen = Screen.HOME
        Screen.SETTINGS:
            if Rect2(120, 470, 480, 90).has_point(pos):
                haptics = not haptics
                _vibrate(25)
                _save()
            elif Rect2(120, 590, 480, 90).has_point(pos):
                reduced_fx = not reduced_fx
                _save()
            elif home_button.has_point(pos):
                screen = Screen.HOME

func _start_run(new_mode: Mode) -> void:
    mode = new_mode
    screen = Screen.PLAY
    round_index = 0
    total_score = 0
    total_target = 0
    total_possible = 0
    best_combo = 0
    run_won = false
    if mode == Mode.DAILY:
        var date := Time.get_date_dict_from_system()
        daily_code = "%04d%02d%02d" % [date.year, date.month, date.day]
        run_seed = int(daily_code)
    else:
        run_seed = int(Time.get_unix_time_from_system()) ^ (level * 982451653)
    _prepare_round()

func _restart_same_seed() -> void:
    screen = Screen.PLAY
    round_index = 0
    total_score = 0
    total_target = 0
    total_possible = 0
    best_combo = 0
    run_won = false
    _prepare_round()

func _prepare_round() -> void:
    particles.clear()
    pulses.clear()
    sparks.clear()
    round_score = 0
    combo = 0
    round_started = false
    finish_delay = -1.0
    rng.seed = run_seed + round_index * 104729

    var effective_level := level if mode == Mode.CLASSIC else 9 + round_index * 3
    var count := clampi(25 + effective_level * 2 + round_index * 4, 28, 88)
    var color_count := 1
    if effective_level >= 4:
        color_count = 2
    if effective_level >= 8:
        color_count = 3
    if effective_level >= 16:
        color_count = 4
    var prism_count := mini(5, 1 + int(effective_level / 9)) if effective_level >= 6 else 0
    var charged_count := mini(4, 1 + int(effective_level / 14)) if effective_level >= 12 else 0

    for i in range(count):
        var kind := TYPE_NORMAL
        var radius := rng.randf_range(11.0, 16.0)
        if i < prism_count:
            kind = TYPE_PRISM
            radius = 18.0
        elif i < prism_count + charged_count:
            kind = TYPE_CHARGED
            radius = 19.0
        var angle := rng.randf_range(0.0, TAU)
        var speed := rng.randf_range(35.0 + effective_level, 72.0 + effective_level * 1.6)
        particles.append({
            "pos": Vector2(rng.randf_range(40.0, W - 40.0), rng.randf_range(PLAY_TOP + 35.0, PLAY_BOTTOM - 35.0)),
            "vel": Vector2.from_angle(angle) * speed,
            "radius": radius,
            "color": rng.randi_range(0, color_count - 1),
            "kind": kind,
            "alive": true,
            "hits": 0,
            "phase": rng.randf_range(0.0, TAU)
        })

    var ratio := 0.54 if color_count == 1 else 0.42 if color_count == 2 else 0.32 if color_count == 3 else 0.27
    round_target = int(ceil(count * ratio))
    total_target += round_target
    total_possible += count

func _start_pulse(pos: Vector2) -> void:
    round_started = true
    pulses.append(_pulse(pos, WHITE_PULSE, 132.0, 0))
    _burst(pos, TEXT, 18)
    _vibrate(20)

func _pulse(pos: Vector2, color_id: int, max_radius: float, generation: int) -> Dictionary:
    return {"pos": pos, "radius": 3.0, "max": max_radius, "speed": 255.0, "color": color_id, "generation": generation, "closing": false, "alpha": 1.0}

func _update_game(delta: float) -> void:
    _move_particles(delta)
    _update_pulses(delta)
    _update_sparks(delta)
    if finish_delay >= 0.0:
        finish_delay -= delta
        if finish_delay <= 0.0:
            _finish_round()
    elif round_started and pulses.is_empty():
        finish_delay = 0.65

func _move_particles(delta: float) -> void:
    for p in particles:
        if not p["alive"]:
            continue
        p["pos"] += p["vel"] * delta
        p["phase"] += delta * 2.0
        var r: float = p["radius"]
        if p["pos"].x < r:
            p["pos"].x = r
            p["vel"].x = absf(p["vel"].x)
        elif p["pos"].x > W - r:
            p["pos"].x = W - r
            p["vel"].x = -absf(p["vel"].x)
        if p["pos"].y < PLAY_TOP + r:
            p["pos"].y = PLAY_TOP + r
            p["vel"].y = absf(p["vel"].y)
        elif p["pos"].y > PLAY_BOTTOM - r:
            p["pos"].y = PLAY_BOTTOM - r
            p["vel"].y = -absf(p["vel"].y)

func _update_pulses(delta: float) -> void:
    var alive_pulses: Array = []
    for pulse in pulses:
        if not pulse["closing"]:
            pulse["radius"] += pulse["speed"] * delta
            if pulse["radius"] >= pulse["max"]:
                pulse["closing"] = true
        else:
            pulse["radius"] -= pulse["speed"] * 0.48 * delta
            pulse["alpha"] -= delta * 1.45
        if pulse["radius"] > 0.0 and pulse["alpha"] > 0.0:
            alive_pulses.append(pulse)
            _test_hits(pulse)
    pulses = alive_pulses

func _test_hits(pulse: Dictionary) -> void:
    for p in particles:
        if not p["alive"]:
            continue
        var gap := absf(pulse["pos"].distance_to(p["pos"]) - pulse["radius"])
        if gap > p["radius"] + 8.0:
            continue
        if pulse["color"] != WHITE_PULSE and p["kind"] != TYPE_PRISM and pulse["color"] != p["color"]:
            continue
        if p["kind"] == TYPE_CHARGED and p["hits"] == 0:
            p["hits"] = 1
            _burst(p["pos"], COLORS[p["color"]], 6)
            continue
        _trigger(p, pulse)

func _trigger(p: Dictionary, source: Dictionary) -> void:
    p["alive"] = false
    round_score += 1
    total_score += 1
    combo += 1
    best_combo = maxi(best_combo, combo)
    var next_color: int = WHITE_PULSE if p["kind"] == TYPE_PRISM else p["color"]
    var radius := 88.0 + minf(32.0, source["generation"] * 3.0)
    if p["kind"] == TYPE_CHARGED:
        radius += 25.0
    pulses.append(_pulse(p["pos"], next_color, radius, source["generation"] + 1))
    _burst(p["pos"], TEXT if next_color == WHITE_PULSE else COLORS[next_color], 8 if reduced_fx else 16)
    if combo == 10 or combo == 25 or combo == 50 or combo == 75:
        _vibrate(30)

func _burst(pos: Vector2, color: Color, amount: int) -> void:
    for i in range(amount):
        var angle := rng.randf_range(0.0, TAU)
        sparks.append({"pos": pos, "vel": Vector2.from_angle(angle) * rng.randf_range(50.0, 175.0), "life": rng.randf_range(0.3, 0.75), "size": rng.randf_range(1.5, 4.0), "color": color})

func _update_sparks(delta: float) -> void:
    var alive: Array = []
    for s in sparks:
        s["pos"] += s["vel"] * delta
        s["vel"] *= 0.96
        s["life"] -= delta
        if s["life"] > 0.0:
            alive.append(s)
    sparks = alive

func _finish_round() -> void:
    _vibrate(42 if round_score >= round_target else 18)
    round_index += 1
    if round_index < 3:
        _prepare_round()
    else:
        _finish_run()

func _finish_run() -> void:
    run_won = total_score >= total_target
    var rating := _rating()
    best_rating = maxi(best_rating, rating)
    if mode == Mode.DAILY:
        daily_best = maxi(daily_best, rating)
    elif run_won:
        level += 1
    _save()
    screen = Screen.RESULT
    _vibrate(70 if run_won else 25)

func _rating() -> int:
    return clampi(int(round(float(total_score) / maxf(1.0, float(total_possible)) * 100.0)), 0, 100)

func _create_stars() -> void:
    rng.seed = 771337
    for i in range(64):
        stars.append({"pos": Vector2(rng.randf_range(0.0, W), rng.randf_range(0.0, H)), "size": rng.randf_range(0.8, 2.4), "alpha": rng.randf_range(0.12, 0.5), "speed": rng.randf_range(3.0, 10.0)})

func _update_stars(delta: float) -> void:
    for s in stars:
        s["pos"].y += s["speed"] * delta
        if s["pos"].y > H:
            s["pos"].y = 0.0

func _vibrate(ms: int) -> void:
    if haptics:
        Input.vibrate_handheld(ms)

func _draw() -> void:
    draw_rect(Rect2(0, 0, W, H), BG)
    _draw_background()
    match screen:
        Screen.HOME:
            _draw_home()
        Screen.PLAY:
            _draw_play()
        Screen.PAUSE:
            _draw_play()
            draw_rect(Rect2(0, 0, W, H), Color(0.01, 0.02, 0.07, 0.84))
            _center("PAUSADO", 360, 52, TEXT)
            _button(primary_button, "CONTINUAR", VIOLET)
            _button(home_button, "INÍCIO", PANEL)
        Screen.RESULT:
            _draw_result()
        Screen.SETTINGS:
            _draw_settings()

func _draw_background() -> void:
    for s in stars:
        draw_circle(s["pos"], s["size"], Color(0.45, 0.58, 1.0, s["alpha"]))
    for y in range(235, 1110, 72):
        draw_line(Vector2(0, y), Vector2(W, y), Color(0.15, 0.2, 0.42, 0.10), 1.0)
    for x in range(0, 720, 72):
        draw_line(Vector2(x, 220), Vector2(x, 1090), Color(0.15, 0.2, 0.42, 0.08), 1.0)

func _draw_home() -> void:
    _center("PULSE", 165, 82, CYAN)
    _center("100", 250, 92, PINK)
    _center("UM TOQUE. UMA REAÇÃO.", 332, 23, MUTED)
    var c := Vector2(W / 2.0, 435)
    for i in range(4):
        draw_arc(c, 88.0 - i * 15.0, -0.4 + i * 0.5, 4.2 + i * 0.2, 48, Color(COLORS[i], 0.9), 4.0)
    draw_circle(c, 12, TEXT)
    _button(classic_button, "JOGAR", VIOLET)
    _button(daily_button, "DESAFIO DIÁRIO", CYAN)
    _button(settings_button, "AJUSTES", PANEL)
    _center("RECORDE %d/100" % best_rating, 960, 28, GOLD)
    _center("NÍVEL %d" % level, 1005, 22, MUTED)
    _center("OFFLINE • UM DEDO • SEM CADASTRO", 1200, 17, MUTED)

func _draw_play() -> void:
    _text("NÍVEL %d" % level if mode == Mode.CLASSIC else "DIÁRIO", Vector2(35, 62), 24, VIOLET)
    _center("RODADA %d/3" % (round_index + 1), 65, 20, MUTED)
    _center("%d" % round_score, 132, 68, TEXT)
    _center("OBJETIVO %d" % round_target, 177, 22, CYAN)
    _text("COMBO", Vector2(535, 122), 18, MUTED)
    _text("%dx" % combo, Vector2(548, 164), 38, PINK)
    _button(pause_button, "Ⅱ", PANEL, 22)
    draw_rect(Rect2(0, PLAY_TOP, W, PLAY_BOTTOM - PLAY_TOP), Color(0.02, 0.03, 0.10, 0.45))
    for pulse in pulses:
        var color: Color = TEXT if pulse["color"] == WHITE_PULSE else COLORS[pulse["color"]]
        draw_arc(pulse["pos"], pulse["radius"], 0.0, TAU, 64, Color(color, pulse["alpha"] * 0.18), 14.0)
        draw_arc(pulse["pos"], pulse["radius"], 0.0, TAU, 64, Color(color, pulse["alpha"]), 3.0)
    for p in particles:
        if p["alive"]:
            _draw_particle(p)
    for s in sparks:
        draw_circle(s["pos"], s["size"], Color(s["color"], clampf(s["life"] / 0.75, 0.0, 1.0)))
    if not round_started:
        var c := Vector2(W / 2.0, 690)
        var r := 30.0 + sin(Time.get_ticks_msec() / 250.0) * 7.0
        draw_arc(c, r, 0, TAU, 40, TEXT, 3.0)
        draw_circle(c, 7.0, TEXT)
        _center("TOQUE UMA VEZ", 790, 24, TEXT)
        _center("Escolha o ponto perfeito", 832, 18, MUTED)

func _draw_particle(p: Dictionary) -> void:
    var r: float = p["radius"] * (1.0 + sin(p["phase"] * 2.0) * 0.05)
    if p["kind"] == TYPE_PRISM:
        var pts := PackedVector2Array()
        for i in range(4):
            pts.append(p["pos"] + Vector2.from_angle(PI * 0.25 + i * PI * 0.5) * r)
        draw_colored_polygon(pts, Color(1, 1, 1, 0.12))
        for i in range(4):
            draw_line(p["pos"], pts[i], COLORS[i], 3.0)
            draw_line(pts[i], pts[(i + 1) % 4], TEXT, 2.0)
        return
    var color: Color = COLORS[p["color"]]
    draw_circle(p["pos"], r * 2.1, Color(color, 0.06))
    draw_circle(p["pos"], r * 1.5, Color(color, 0.14))
    draw_circle(p["pos"], r, color)
    draw_circle(p["pos"] - Vector2(r * 0.28, r * 0.30), r * 0.27, Color(1, 1, 1, 0.7))
    if p["kind"] == TYPE_CHARGED:
        draw_arc(p["pos"], r + 8.0, 0, TAU, 28, TEXT, 2.0)
        if p["hits"] > 0:
            draw_circle(p["pos"], r * 0.4, TEXT)

func _draw_result() -> void:
    _center("PULSE 100", 155, 55, CYAN)
    var color := GREEN if run_won else PINK
    _center("%d" % _rating(), 360, 150, color)
    _center("/100", 438, 36, MUTED)
    _center("VITÓRIA" if run_won else "QUASE!", 520, 38, color)
    _center("%d reações • combo %dx" % [total_score, best_combo], 585, 23, TEXT)
    _center("Objetivo total: %d" % total_target, 625, 19, MUTED)
    if mode == Mode.DAILY:
        _center("DESAFIO %s" % daily_code, 690, 22, CYAN)
        _center("Melhor de hoje: %d/100" % daily_best, 730, 19, MUTED)
    else:
        _center("RECORDE: %d/100" % best_rating, 705, 22, GOLD)
    _button(primary_button, "PRÓXIMO" if run_won else "TENTAR NOVAMENTE", VIOLET)
    _button(secondary_button, "NOVA CONFIGURAÇÃO", PANEL)
    _button(home_button, "INÍCIO", Color("171d38"))
    _center("Faça uma captura e desafie seus amigos", 1210, 17, MUTED)

func _draw_settings() -> void:
    _center("AJUSTES", 185, 54, TEXT)
    _toggle(Rect2(120, 470, 480, 90), "VIBRAÇÃO", haptics)
    _toggle(Rect2(120, 590, 480, 90), "EFEITOS REDUZIDOS", reduced_fx)
    _button(home_button, "VOLTAR", PANEL)
    _center("Pulse 100 v1.0.0", 1215, 17, MUTED)

func _toggle(rect: Rect2, label: String, enabled: bool) -> void:
    draw_rect(rect, PANEL)
    draw_rect(rect, Color(0.3, 0.4, 0.75, 0.7), false, 2.0)
    _text(label, rect.position + Vector2(25, 55), 23, TEXT)
    var pill := Rect2(rect.end.x - 115, rect.position.y + 24, 78, 42)
    draw_rect(pill, GREEN if enabled else Color("343b58"))
    draw_circle(Vector2(pill.end.x - 21 if enabled else pill.position.x + 21, pill.get_center().y), 16, TEXT)

func _button(rect: Rect2, label: String, color: Color, size: int = 27) -> void:
    draw_rect(rect, Color(color, 0.76))
    draw_rect(rect, color.lightened(0.25), false, 2.0)
    var font := ThemeDB.fallback_font
    var text_size := font.get_string_size(label, HORIZONTAL_ALIGNMENT_LEFT, -1, size)
    draw_string(font, rect.position + Vector2((rect.size.x - text_size.x) / 2.0, (rect.size.y + text_size.y) / 2.0 - 3.0), label, HORIZONTAL_ALIGNMENT_LEFT, -1, size, TEXT)

func _center(value: String, y: float, size: int, color: Color) -> void:
    var font := ThemeDB.fallback_font
    var width := font.get_string_size(value, HORIZONTAL_ALIGNMENT_LEFT, -1, size).x
    draw_string(font, Vector2((W - width) / 2.0, y), value, HORIZONTAL_ALIGNMENT_LEFT, -1, size, color)

func _text(value: String, pos: Vector2, size: int, color: Color) -> void:
    draw_string(ThemeDB.fallback_font, pos, value, HORIZONTAL_ALIGNMENT_LEFT, -1, size, color)

func _load_save() -> void:
    var cfg := ConfigFile.new()
    if cfg.load(SAVE_PATH) == OK:
        level = int(cfg.get_value("progress", "level", 1))
        best_rating = int(cfg.get_value("progress", "best", 0))
        haptics = bool(cfg.get_value("settings", "haptics", true))
        reduced_fx = bool(cfg.get_value("settings", "reduced_fx", false))
        var date := Time.get_date_dict_from_system()
        var today := "%04d%02d%02d" % [date.year, date.month, date.day]
        daily_best = int(cfg.get_value("daily", today, 0))

func _save() -> void:
    var cfg := ConfigFile.new()
    cfg.load(SAVE_PATH)
    cfg.set_value("progress", "level", level)
    cfg.set_value("progress", "best", best_rating)
    cfg.set_value("settings", "haptics", haptics)
    cfg.set_value("settings", "reduced_fx", reduced_fx)
    if not daily_code.is_empty():
        cfg.set_value("daily", daily_code, daily_best)
    cfg.save(SAVE_PATH)
