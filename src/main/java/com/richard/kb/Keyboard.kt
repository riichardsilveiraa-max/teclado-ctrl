package com.richard.kb

import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// Estilos de tecla
private const val NORMAL = 0
private const val SPECIAL = 1
private const val SHORTCUT = 2
private const val ACCENT = 3
private const val FLAT = 4

private class K(
    val label: String,
    val style: Int,
    val weight: Float,
    val sub: String? = null,
    val sp: Float = 16f,
    val preview: Boolean = false,
    val repeat: Boolean = false,
    val variants: List<String> = emptyList(),
    val swipe: Boolean = false,
    val onLong: (() -> Unit)? = null,
    val onTap: () -> Unit
)

class CtrlKeyboardService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var cfg = Cfg()
    private var pal: Palette = Themes.escuro

    private var frame: FrameLayout? = null
    private var kbBox: LinearLayout? = null
    private var balao: TextView? = null
    private var stripView: View? = null

    private var layer = "abc" // abc, s1, s2, num, fn, emoji, clip
    private var field = 0 // 0 normal, 1 e-mail, 2 endereço web
    private var isPassword = false
    private var emojiCat = 0

    private var ctrl = false
    private var alt = false
    private var shift = false
    private var ctrlLock = false
    private var altLock = false
    private var capsLock = false
    private var lastCtrl = 0L
    private var lastAlt = 0L
    private var lastShift = 0L

    // Estado do toque atual
    private var repeatAction: (() -> Unit)? = null
    private var curKey: K? = null
    private var curView: View? = null
    private var longFired = false
    private var swiped = false
    private var swipeX = 0f
    private var lastX = 0f

    // Faixa de variações (toque longo)
    private var varActive = false
    private var varList: List<String> = emptyList()
    private var varItems: List<TextView> = emptyList()
    private var varSel = 0
    private var varLeft = 0
    private var varItemW = 1

    // Histórico da área de transferência (só na memória)
    private val clips = ArrayList<String>()
    private var clipMgr: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { grabClip() }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatAction?.invoke()
            handler.postDelayed(this, 50)
        }
    }
    private val longRunnable = Runnable { fireLong() }
    private val hideBalao = Runnable { balao?.visibility = View.GONE }

    // ---------- Ciclo de vida ----------

    override fun onCreate() {
        super.onCreate()
        clipMgr = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipMgr?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipMgr?.removePrimaryClipChangedListener(clipListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        cfg = Prefs.load(this)
        pal = Themes.get(cfg.theme)

        val f = FrameLayout(this)
        f.setBackgroundColor(pal.fundo)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(6), dp(8), dp(6), dp(8))
        f.addView(box, FrameLayout.LayoutParams(MATCH, WRAP))

        val b = TextView(this)
        b.gravity = Gravity.CENTER
        b.setTextColor(pal.balaoTexto)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        b.background = rounded(pal.balao, 10)
        b.elevation = dp(4).toFloat()
        b.visibility = View.GONE
        f.addView(b, FrameLayout.LayoutParams(dp(48), dp(56)))

        frame = f
        kbBox = box
        balao = b
        render()
        return f
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        cfg = Prefs.load(this)
        pal = Themes.get(cfg.theme)
        frame?.setBackgroundColor(pal.fundo)
        balao?.background = rounded(pal.balao, 10)
        balao?.setTextColor(pal.balaoTexto)

        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        if (!restarting) {
            ctrl = false
            alt = false
            shift = false
            ctrlLock = false
            altLock = false
            capsLock = false
            layer = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) "num" else "abc"
        }
        isPassword = (cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        field = if (cls == InputType.TYPE_CLASS_TEXT) {
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> 1
                InputType.TYPE_TEXT_VARIATION_URI -> 2
                else -> 0
            }
        } else 0

        window?.window?.navigationBarColor = pal.fundo
        grabClip()
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopRepeat()
        hideVariants()
        hideBalaoNow()
    }

    // ---------- Área de transferência ----------

    private fun grabClip() {
        try {
            val c = clipMgr?.primaryClip ?: return
            if (c.itemCount == 0) return
            val ex = c.description?.extras
            if (ex != null && ex.getBoolean("android.content.extra.IS_SENSITIVE", false)) return
            val t = c.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (t.isBlank() || t.length > 2000) return
            clips.remove(t)
            clips.add(0, t)
            while (clips.size > 20) clips.removeAt(clips.size - 1)
        } catch (e: Exception) {
            // sem acesso à área de transferência agora
        }
    }

    // ---------- Envio de teclas ----------

    private fun metaState(): Int {
        var m = 0
        if (ctrl) m = m or KeyEvent.META_CTRL_ON
        if (alt) m = m or KeyEvent.META_ALT_ON
        if (shift && !capsLock) m = m or KeyEvent.META_SHIFT_ON
        return m
    }

    private fun sendKey(code: Int, meta: Int = 0) {
        val ic = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun commit(t: String) {
        currentInputConnection?.commitText(t, 1)
    }

    // Ctrl, Alt e Shift valem só para a próxima tecla, a não ser que estejam travados
    private fun afterKey() {
        var changed = false
        if (ctrl && !ctrlLock) {
            ctrl = false
            changed = true
        }
        if (alt && !altLock) {
            alt = false
            changed = true
        }
        if (shift && !capsLock) {
            shift = false
            changed = true
        }
        if (changed) render()
    }

    private fun keyCodeFor(s: String): Int {
        if (s.length != 1) return 0
        val c = s[0].lowercaseChar()
        return when (c) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (c - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            ',' -> KeyEvent.KEYCODE_COMMA
            '.' -> KeyEvent.KEYCODE_PERIOD
            '/' -> KeyEvent.KEYCODE_SLASH
            '-' -> KeyEvent.KEYCODE_MINUS
            '=' -> KeyEvent.KEYCODE_EQUALS
            ';' -> KeyEvent.KEYCODE_SEMICOLON
            '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            '\\' -> KeyEvent.KEYCODE_BACKSLASH
            '\'' -> KeyEvent.KEYCODE_APOSTROPHE
            '`' -> KeyEvent.KEYCODE_GRAVE
            else -> 0
        }
    }

    private fun typeChar(s: String) {
        if (currentInputConnection == null) return
        val code = keyCodeFor(s)
        if ((ctrl || alt) && code != 0) sendKey(code, metaState()) else commit(s)
        afterKey()
    }

    private fun enter() {
        if (ctrl || alt || (shift && !capsLock)) {
            sendKey(KeyEvent.KEYCODE_ENTER, metaState())
        } else if (!sendDefaultEditorAction(true)) {
            sendKey(KeyEvent.KEYCODE_ENTER)
        }
        afterKey()
    }

    private fun tapCtrl() {
        val now = SystemClock.uptimeMillis()
        if (ctrlLock) {
            ctrlLock = false
            ctrl = false
        } else if (ctrl && now - lastCtrl < 400) {
            ctrlLock = true
        } else {
            ctrl = !ctrl
        }
        lastCtrl = now
        render()
    }

    private fun tapAlt() {
        val now = SystemClock.uptimeMillis()
        if (altLock) {
            altLock = false
            alt = false
        } else if (alt && now - lastAlt < 400) {
            altLock = true
        } else {
            alt = !alt
        }
        lastAlt = now
        render()
    }

    private fun tapShift() {
        val now = SystemClock.uptimeMillis()
        if (capsLock) {
            capsLock = false
            shift = false
        } else if (shift && now - lastShift < 400) {
            capsLock = true
        } else {
            shift = !shift
        }
        lastShift = now
        render()
    }

    private fun goTo(l: String) {
        layer = l
        if (!capsLock) shift = false
        render()
    }

    private fun picker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun nextIme() {
        if (Build.VERSION.SDK_INT >= 28) {
            if (!switchToNextInputMethod(false)) picker()
        } else {
            picker()
        }
    }

    // ---------- Variações (toque longo) ----------

    private fun variantsFor(c: String): List<String> {
        val isLetter = c.length == 1 && c[0].isLetter()
        if (isLetter) {
            if (layer != "abc") return emptyList()
            val out = ArrayList<String>()
            val d = Data.digitFor[c]
            if (d != null) out.add(d)
            val a = Data.accents[c]
            if (a != null) out.addAll(a)
            return out
        }
        if (c == "." && field != 0) return Data.web
        return Data.symbols[c] ?: emptyList()
    }

    private fun showVariants(v: View, list: List<String>, rawX: Float) {
        val f = frame ?: return
        hideVariants()
        val itemW = dp(42)
        val h = dp(50)

        val s = LinearLayout(this)
        s.orientation = LinearLayout.HORIZONTAL
        s.background = rounded(pal.balao, 12)
        s.setPadding(dp(4), dp(4), dp(4), dp(4))
        s.elevation = dp(6).toFloat()

        val items = ArrayList<TextView>()
        for (t in list) {
            val tv = TextView(this)
            tv.gravity = Gravity.CENTER
            tv.text = t
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            tv.setTextColor(pal.balaoTexto)
            s.addView(tv, LinearLayout.LayoutParams(itemW, MATCH))
            items.add(tv)
        }

        val w = itemW * list.size + dp(8)
        val loc = IntArray(2)
        val floc = IntArray(2)
        v.getLocationInWindow(loc)
        f.getLocationInWindow(floc)
        var x = loc[0] - floc[0]
        // teclas da metade direita abrem a faixa para a esquerda
        if (x + v.width / 2 > f.width / 2) x = x + v.width - w
        val maxX = if (f.width - w > 0) f.width - w else 0
        x = x.coerceIn(0, maxX)
        val y = maxOf(0, loc[1] - floc[1] - h - dp(6))

        val lp = FrameLayout.LayoutParams(w, h)
        lp.leftMargin = x
        lp.topMargin = y
        f.addView(s, lp)

        stripView = s
        varItems = items
        varList = list
        f.getLocationOnScreen(floc)
        varLeft = floc[0] + x + dp(4)
        varItemW = itemW
        varActive = true
        updateSel(rawX)
    }

    private fun updateSel(rawX: Float) {
        if (!varActive || varList.isEmpty()) return
        val idx = ((rawX - varLeft) / varItemW).toInt().coerceIn(0, varList.size - 1)
        varSel = idx
        for (i in varItems.indices) {
            varItems[i].background = if (i == idx) rounded(pal.ativo, 8) else null
            varItems[i].setTextColor(if (i == idx) pal.ativoTexto else pal.balaoTexto)
        }
    }

    private fun hideVariants() {
        val s = stripView
        if (s != null) frame?.removeView(s)
        stripView = null
        varActive = false
    }

    // ---------- Desenho do teclado ----------

    private fun render() {
        val box = kbBox ?: return
        box.removeAllViews()
        val kh = dp(cfg.keyH)

        fun put(v: View, h: Int) {
            val lp = LinearLayout.LayoutParams(MATCH, h)
            if (box.childCount > 0) lp.topMargin = dp(6)
            box.addView(v, lp)
        }

        fun row(keys: List<K>) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.isBaselineAligned = false
            for (k in keys) {
                val lp = LinearLayout.LayoutParams(0, kh, k.weight)
                lp.setMargins(dp(2), 0, dp(2), 0)
                r.addView(makeKey(k), lp)
            }
            put(r, WRAP)
        }

        // Linha de atalhos
        if (cfg.shortcuts) {
            row(listOf(
                shortcut("Ctrl+A", "tudo", KeyEvent.KEYCODE_A),
                shortcut("Ctrl+C", "copiar", KeyEvent.KEYCODE_C),
                shortcut("Ctrl+V", "colar", KeyEvent.KEYCODE_V, onLong = { goTo("clip") }),
                shortcut("Ctrl+X", "cortar", KeyEvent.KEYCODE_X),
                shortcut("Ctrl+Z", "desfazer", KeyEvent.KEYCODE_Z)
            ))
        }

        // Fn, Esc, Tab, Ctrl, Alt e setas
        if (cfg.toolbar) {
            row(listOf(
                special("Fn", active = layer == "fn") { goTo(if (layer == "fn") "abc" else "fn") },
                special("Esc") { sendKey(KeyEvent.KEYCODE_ESCAPE, metaState()); afterKey() },
                special("Tab") { sendKey(KeyEvent.KEYCODE_TAB, metaState()); afterKey() },
                modKey("Ctrl", ctrl, ctrlLock) { tapCtrl() },
                modKey("Alt", alt, altLock) { tapAlt() },
                special("←", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_LEFT, metaState()) },
                special("↑", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_UP, metaState()) },
                special("↓", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_DOWN, metaState()) },
                special("→", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, metaState()) }
            ))
        }

        // Linha de símbolos de código (rola para o lado)
        if (layer == "abc" && cfg.codeRow) {
            val hs = HorizontalScrollView(this)
            hs.isHorizontalScrollBarEnabled = false
            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.HORIZONTAL
            inner.isBaselineAligned = false
            for (s in Data.codeKeys) {
                val lp = LinearLayout.LayoutParams(dp(38), kh)
                lp.setMargins(dp(2), 0, dp(2), 0)
                inner.addView(makeKey(ch(s, SPECIAL, 16f)), lp)
            }
            hs.addView(inner)
            put(hs, WRAP)
        }

        when (layer) {
            "s1" -> {
                row(chars("1234567890"))
                row(chars("@#\$_&-+()/"))
                row(listOf(layerKey("=\\<", "s2")) + chars("*\"':;!?") + backKey())
                row(listOf(
                    layerKey("ABC", "abc"), punct(","), numKey(), spaceKey(4f), punct("."), enterKey()
                ))
            }
            "s2" -> {
                row(chars("~`|•√π÷×§Δ"))
                row(chars("£¢€¥^°={}\\"))
                row(listOf(layerKey("?123", "s1")) + chars("%©®™✓[]") + backKey())
                row(listOf(
                    layerKey("ABC", "abc"), punct("<"), numKey(), spaceKey(4f), punct(">"), enterKey()
                ))
            }
            "num" -> {
                row(listOf(punct("+", 1.5f), ch("1"), ch("2"), ch("3"), backKey()))
                row(listOf(punct("-", 1.5f), ch("4"), ch("5"), ch("6"), spaceKey(1.5f, SPECIAL)))
                row(listOf(punct("*", 1.5f), ch("7"), ch("8"), ch("9"), punct("/", 1.5f)))
                row(listOf(layerKey("ABC", "abc"), punct(","), ch("0"), punct("."), enterKey()))
            }
            "fn" -> {
                row((1..6).map { fKey(it) })
                row((7..12).map { fKey(it) })
                row(listOf(
                    navKey("Home", KeyEvent.KEYCODE_MOVE_HOME),
                    navKey("End", KeyEvent.KEYCODE_MOVE_END),
                    navKey("PgUp", KeyEvent.KEYCODE_PAGE_UP),
                    navKey("PgDn", KeyEvent.KEYCODE_PAGE_DOWN),
                    navKey("Ins", KeyEvent.KEYCODE_INSERT),
                    navKey("Del", KeyEvent.KEYCODE_FORWARD_DEL)
                ))
                row(listOf(
                    layerKey("ABC", "abc"),
                    shortcut("Ctrl+S", "salvar", KeyEvent.KEYCODE_S),
                    shortcut("Ctrl+F", "buscar", KeyEvent.KEYCODE_F),
                    shortcut("Ctrl+Y", "refazer", KeyEvent.KEYCODE_Y),
                    shortcut("Ctrl+/", "comentar", KeyEvent.KEYCODE_SLASH),
                    K("📋", SPECIAL, 1f, sp = 16f) { goTo("clip") },
                    backKey()
                ))
            }
            "emoji" -> {
                val sv = ScrollView(this)
                sv.isVerticalScrollBarEnabled = false
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                val list = Data.emoji[emojiCat].second
                var i = 0
                while (i < list.size) {
                    val r = LinearLayout(this)
                    r.orientation = LinearLayout.HORIZONTAL
                    r.isBaselineAligned = false
                    for (j in 0 until 8) {
                        val lp = LinearLayout.LayoutParams(0, kh, 1f)
                        lp.setMargins(dp(1), 0, dp(1), 0)
                        if (i + j < list.size) r.addView(makeKey(emojiKey(list[i + j])), lp) else r.addView(View(this), lp)
                    }
                    col.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
                    i += 8
                }
                sv.addView(col)
                put(sv, 3 * kh + 2 * dp(6))
                row(listOf(layerKey("ABC", "abc")) + (0 until Data.emoji.size).map { catKey(it) } + backKey())
            }
            "clip" -> {
                val items = ArrayList<K>()
                for (idx in 0 until 6) items.add(clipItem(clips.getOrNull(idx), idx))
                row(items.subList(0, 2))
                row(items.subList(2, 4))
                row(items.subList(4, 6))
                row(listOf(
                    layerKey("ABC", "abc"),
                    special("Limpar histórico", weight = 3f) { clips.clear(); render() },
                    backKey()
                ))
            }
            else -> {
                row(chars("qwertyuiop"))
                row(chars("asdfghjkl"))
                row(listOf(shiftKey()) + chars("zxcvbnm") + backKey())
                row(bottomAbc())
            }
        }
    }

    private fun bottomAbc(): List<K> {
        val mid = when (field) {
            1 -> ch("@", SPECIAL, 17f)
            2 -> ch("/", SPECIAL, 17f)
            else -> K("😀", SPECIAL, 1f, sp = 16f) { goTo("emoji") }
        }
        return listOf(
            layerKey("?123", "s1", 1.3f),
            K("🌐", SPECIAL, 1f, sp = 16f, onLong = { picker() }) { nextIme() },
            mid,
            punct(","),
            spaceKey(3.2f),
            punct("."),
            enterKey()
        )
    }

    // ---------- Fábrica de teclas ----------

    private fun shortcut(label: String, sub: String, code: Int, onLong: (() -> Unit)? = null) =
        K(label, SHORTCUT, 1f, sub = sub, sp = 12f, onLong = onLong) {
            sendKey(code, KeyEvent.META_CTRL_ON)
            afterKey()
        }

    private fun special(
        label: String,
        weight: Float = 1f,
        sp: Float = 13f,
        active: Boolean = false,
        repeat: Boolean = false,
        onTap: () -> Unit
    ) = K(label, if (active) ACCENT else SPECIAL, weight, sp = sp, repeat = repeat, onTap = onTap)

    private fun modKey(label: String, on: Boolean, lock: Boolean, tap: () -> Unit) =
        if (lock) K(label, ACCENT, 1f, sub = "fixo", sp = 11f, onTap = tap)
        else K(label, if (on) ACCENT else SPECIAL, 1f, sp = 13f, onTap = tap)

    private fun ch(c: String, style: Int = NORMAL, sp: Float = 16f, weight: Float = 1f): K {
        val letter = c.length == 1 && c[0].isLetter()
        val up = shift && layer == "abc" && letter
        val label = if (up) c.uppercase() else c
        val vars = variantsFor(c).map { if (up) it.uppercase() else it }
        return K(label, style, weight, sp = sp, preview = true, variants = vars) { typeChar(label) }
    }

    private fun chars(s: String): List<K> = s.map { ch(it.toString()) }

    private fun punct(c: String, weight: Float = 1f): K = ch(c, SPECIAL, 17f, weight)

    private fun layerKey(label: String, target: String, weight: Float = 1.5f) =
        special(label, weight = weight) { goTo(target) }

    private fun numKey() = K("12", SPECIAL, 1f, sub = "34", sp = 12f) { goTo("num") }

    private fun shiftKey() =
        if (capsLock) K("⇧", ACCENT, 1.5f, sub = "caps", sp = 14f) { tapShift() }
        else special("⇧", weight = 1.5f, sp = 18f, active = shift) { tapShift() }

    private fun backKey() =
        special("⌫", weight = 1.5f, sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DEL, metaState()) }

    private fun spaceKey(weight: Float, style: Int = NORMAL) =
        K("espaço", style, weight, sp = 13f, swipe = true, onLong = { picker() }) { typeChar(" ") }

    private fun enterKey() = K("↵", ACCENT, 1.5f, sp = 32f) { enter() }

    private fun fKey(n: Int) =
        special("F$n") { sendKey(KeyEvent.KEYCODE_F1 + n - 1, metaState()); afterKey() }

    private fun navKey(label: String, code: Int) =
        special(label, sp = 12f, repeat = true) { sendKey(code, metaState()) }

    private fun emojiKey(e: String) = K(e, FLAT, 1f, sp = 22f) { typeChar(e) }

    private fun catKey(i: Int) =
        K(Data.emoji[i].first, if (i == emojiCat) ACCENT else SPECIAL, 1f, sp = 18f) {
            emojiCat = i
            render()
        }

    private fun clipItem(t: String?, idx: Int): K {
        if (t == null) return K(if (idx == 0) "Nada copiado ainda" else "", SPECIAL, 1f, sp = 13f) { }
        val one = t.replace("\n", " ").trim()
        val label = if (one.length > 22) one.substring(0, 22) + "…" else one
        return K(label, NORMAL, 1f, sp = 13f) {
            commit(t)
            goTo("abc")
        }
    }

    private fun rounded(color: Int, r: Int = 8): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(r).toFloat()
        return d
    }

    private fun keyBg(normal: Int, down: Int): StateListDrawable {
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), rounded(down))
        s.addState(intArrayOf(), rounded(normal))
        return s
    }

    private fun makeKey(k: K): TextView {
        val (bg, down, fg) = when (k.style) {
            SPECIAL -> Triple(pal.especial, pal.especialDown, pal.especialTexto)
            SHORTCUT -> Triple(pal.atalho, pal.atalhoDown, pal.atalhoTexto)
            ACCENT -> Triple(pal.ativo, pal.ativoDown, pal.ativoTexto)
            FLAT -> Triple(Color.TRANSPARENT, pal.especial, pal.teclaTexto)
            else -> Triple(pal.tecla, pal.teclaDown, pal.teclaTexto)
        }
        val tv = TextView(this)
        tv.gravity = Gravity.CENTER
        tv.includeFontPadding = false
        tv.setTextColor(fg)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, k.sp * cfg.fontPct / 100f)
        tv.background = keyBg(bg, down)
        tv.isClickable = true

        val sub = k.sub
        if (sub != null) {
            val s = SpannableString(k.label + "\n" + sub)
            if (k.style == SHORTCUT) {
                val from = k.label.length + 1
                s.setSpan(RelativeSizeSpan(0.9f), from, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(ForegroundColorSpan(pal.atalhoSub), from, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tv.text = s
        } else {
            tv.text = k.label
        }

        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> touchDown(v, k, e)
                MotionEvent.ACTION_MOVE -> touchMove(k, e)
                MotionEvent.ACTION_UP -> touchUp(v, k, e)
                MotionEvent.ACTION_CANCEL -> touchCancel(v)
            }
            true
        }
        return tv
    }

    // ---------- Toque ----------

    private fun touchDown(v: View, k: K, e: MotionEvent) {
        v.isPressed = true
        if (cfg.vibrate) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        curKey = k
        curView = v
        longFired = false
        swiped = false
        swipeX = e.rawX
        lastX = e.rawX
        if (k.preview && cfg.preview && !isPassword) showBalao(v, k.label)
        if (k.repeat) {
            k.onTap()
            repeatAction = k.onTap
            handler.postDelayed(repeatRunnable, 400)
        }
        if (k.variants.isNotEmpty()) {
            handler.postDelayed(longRunnable, 320)
        } else if (k.onLong != null) {
            handler.postDelayed(longRunnable, 450)
        }
    }

    private fun touchMove(k: K, e: MotionEvent) {
        lastX = e.rawX
        if (varActive) {
            updateSel(e.rawX)
            return
        }
        if (k.swipe) {
            val dx = e.rawX - swipeX
            if (!swiped && abs(dx) > dp(10)) {
                swiped = true
                handler.removeCallbacks(longRunnable)
            }
            if (swiped) {
                val step = dp(14).toFloat()
                val n = (dx / step).toInt()
                if (n != 0) {
                    val code = if (n > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                    for (i in 0 until abs(n)) sendKey(code, metaState())
                    swipeX += n * step
                }
            }
        }
    }

    private fun touchUp(v: View, k: K, e: MotionEvent) {
        handler.removeCallbacks(longRunnable)
        v.isPressed = false
        stopRepeat()
        hideBalaoSoon()
        val slop = dp(6)
        val inside = e.x >= -slop && e.x <= v.width + slop && e.y >= -slop && e.y <= v.height + slop
        if (varActive) {
            val t = varList[varSel]
            hideVariants()
            typeChar(t)
        } else if (longFired || swiped) {
            // ação já executada pelo toque longo ou pelo deslize
        } else if (k.repeat) {
            afterKey()
        } else if (inside) {
            k.onTap()
        }
    }

    private fun touchCancel(v: View) {
        handler.removeCallbacks(longRunnable)
        v.isPressed = false
        stopRepeat()
        hideBalaoSoon()
        hideVariants()
    }

    private fun fireLong() {
        val k = curKey ?: return
        val v = curView ?: return
        longFired = true
        hideBalaoNow()
        if (k.variants.isNotEmpty()) {
            if (cfg.vibrate) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showVariants(v, k.variants, lastX)
        } else {
            k.onLong?.invoke()
        }
    }

    private fun stopRepeat() {
        handler.removeCallbacks(repeatRunnable)
        repeatAction = null
    }

    // ---------- Balão de prévia da tecla ----------

    private fun showBalao(v: View, text: String) {
        val f = frame ?: return
        val b = balao ?: return
        handler.removeCallbacks(hideBalao)
        val loc = IntArray(2)
        val floc = IntArray(2)
        v.getLocationInWindow(loc)
        f.getLocationInWindow(floc)
        val w = v.width + dp(14)
        b.layoutParams = FrameLayout.LayoutParams(w, dp(56))
        val maxX = if (f.width - w > 0) f.width - w else 0
        val x = (loc[0] - floc[0] - dp(7)).coerceIn(0, maxX)
        val y = maxOf(0, loc[1] - floc[1] - dp(60))
        b.text = text
        b.translationX = x.toFloat()
        b.translationY = y.toFloat()
        b.visibility = View.VISIBLE
    }

    private fun hideBalaoSoon() {
        handler.removeCallbacks(hideBalao)
        handler.postDelayed(hideBalao, 110)
    }

    private fun hideBalaoNow() {
        handler.removeCallbacks(hideBalao)
        balao?.visibility = View.GONE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
