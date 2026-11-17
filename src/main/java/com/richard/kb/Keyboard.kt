package com.richard.kb

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// Altura das teclas em dp. Mude aqui para deixar o teclado mais baixo ou mais alto.
private const val KEY_H = 40

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// Estilos de tecla
private const val NORMAL = 0
private const val SPECIAL = 1
private const val SHORTCUT = 2
private const val ACCENT = 3

// Tema escuro estilo Gboard
private object Cor {
    val FUNDO = Color.parseColor("#1B1C1E")
    val TECLA = Color.parseColor("#46484C")
    val TECLA_DOWN = Color.parseColor("#62656A")
    val TECLA_TEXTO = Color.parseColor("#F1F3F4")
    val ESPECIAL = Color.parseColor("#2C2E31")
    val ESPECIAL_DOWN = Color.parseColor("#3D4044")
    val ESPECIAL_TEXTO = Color.parseColor("#C4C7C5")
    val ATALHO = Color.parseColor("#2A3B5C")
    val ATALHO_DOWN = Color.parseColor("#3A4F78")
    val ATALHO_TEXTO = Color.parseColor("#A8C7FA")
    val ATALHO_SUB = Color.parseColor("#8FB0E8")
    val ATIVO = Color.parseColor("#8AB4F8")
    val ATIVO_DOWN = Color.parseColor("#A8C7FA")
    val ATIVO_TEXTO = Color.parseColor("#0A2A66")
    val BALAO = Color.parseColor("#5F6368")
}

private class K(
    val label: String,
    val style: Int,
    val weight: Float,
    val sub: String? = null,
    val sp: Float = 16f,
    val preview: Boolean = false,
    val repeat: Boolean = false,
    val onTap: () -> Unit
)

class CtrlKeyboardService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var frame: FrameLayout? = null
    private var kbBox: LinearLayout? = null
    private var balao: TextView? = null

    private var layer = "abc" // abc, s1, s2, num
    private var ctrl = false
    private var alt = false
    private var shift = false
    private var repeatAction: (() -> Unit)? = null

    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatAction?.invoke()
            handler.postDelayed(this, 50)
        }
    }

    private val hideBalao = Runnable { balao?.visibility = View.GONE }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val f = FrameLayout(this)
        f.setBackgroundColor(Cor.FUNDO)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(6), dp(8), dp(6), dp(8))
        f.addView(box, FrameLayout.LayoutParams(MATCH, WRAP))

        val b = TextView(this)
        b.gravity = Gravity.CENTER
        b.setTextColor(Color.WHITE)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        b.background = rounded(Cor.BALAO)
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
        if (!restarting) {
            ctrl = false
            alt = false
            shift = false
            val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
            layer = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) "num" else "abc"
        }
        window?.window?.navigationBarColor = Cor.FUNDO
        render()
    }

    // ---------- Envio de teclas ----------

    private fun metaState(): Int {
        var m = 0
        if (ctrl) m = m or KeyEvent.META_CTRL_ON
        if (alt) m = m or KeyEvent.META_ALT_ON
        if (shift) m = m or KeyEvent.META_SHIFT_ON
        return m
    }

    private fun sendKey(code: Int, meta: Int = 0) {
        val ic = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    // Ctrl, Alt e Shift valem só para a próxima tecla
    private fun afterKey() {
        if (ctrl || alt || shift) {
            ctrl = false
            alt = false
            shift = false
            render()
        }
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
        val ic = currentInputConnection ?: return
        val code = keyCodeFor(s)
        if ((ctrl || alt) && code != 0) sendKey(code, metaState()) else ic.commitText(s, 1)
        afterKey()
    }

    private fun enter() {
        if (ctrl || alt || shift) {
            sendKey(KeyEvent.KEYCODE_ENTER, metaState())
        } else if (!sendDefaultEditorAction(true)) {
            sendKey(KeyEvent.KEYCODE_ENTER)
        }
        afterKey()
    }

    private fun goTo(l: String) {
        layer = l
        shift = false
        render()
    }

    // ---------- Desenho do teclado ----------

    private fun render() {
        val box = kbBox ?: return
        box.removeAllViews()

        fun row(keys: List<K>) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (k in keys) {
                val lp = LinearLayout.LayoutParams(0, dp(KEY_H), k.weight)
                lp.setMargins(dp(2), 0, dp(2), 0)
                r.addView(makeKey(k), lp)
            }
            val rlp = LinearLayout.LayoutParams(MATCH, WRAP)
            if (box.childCount > 0) rlp.topMargin = dp(6)
            box.addView(r, rlp)
        }

        // Linha de atalhos
        row(listOf(
            shortcut("Ctrl+A", "tudo", KeyEvent.KEYCODE_A),
            shortcut("Ctrl+C", "copiar", KeyEvent.KEYCODE_C),
            shortcut("Ctrl+V", "colar", KeyEvent.KEYCODE_V),
            shortcut("Ctrl+X", "cortar", KeyEvent.KEYCODE_X),
            shortcut("Ctrl+Z", "desfazer", KeyEvent.KEYCODE_Z)
        ))

        // Esc, Tab, Ctrl, Alt e setas
        row(listOf(
            special("Esc") { sendKey(KeyEvent.KEYCODE_ESCAPE, metaState()); afterKey() },
            special("Tab") { sendKey(KeyEvent.KEYCODE_TAB, metaState()); afterKey() },
            special("Ctrl", active = ctrl) { ctrl = !ctrl; render() },
            special("Alt", active = alt) { alt = !alt; render() },
            special("←", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_LEFT, metaState()) },
            special("↑", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_UP, metaState()) },
            special("↓", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_DOWN, metaState()) },
            special("→", sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, metaState()) }
        ))

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
            else -> {
                row(chars("qwertyuiop"))
                row(chars("asdfghjkl"))
                row(listOf(shiftKey()) + chars("zxcvbnm") + backKey())
                row(listOf(layerKey("?123", "s1"), punct(","), spaceKey(4f), punct("."), enterKey()))
            }
        }
    }

    // ---------- Fábrica de teclas ----------

    private fun shortcut(label: String, sub: String, code: Int) =
        K(label, SHORTCUT, 1f, sub = sub, sp = 12f) {
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

    private fun ch(c: String, style: Int = NORMAL, sp: Float = 16f, weight: Float = 1f): K {
        val label = if (shift && layer == "abc") c.uppercase() else c
        return K(label, style, weight, sp = sp, preview = true) { typeChar(label) }
    }

    private fun chars(s: String): List<K> = s.map { ch(it.toString()) }

    private fun punct(c: String, weight: Float = 1f): K = ch(c, SPECIAL, 17f, weight)

    private fun layerKey(label: String, target: String) =
        special(label, weight = 1.5f) { goTo(target) }

    private fun numKey() = K("12", SPECIAL, 1f, sub = "34", sp = 12f) { goTo("num") }

    private fun shiftKey() =
        special("⇧", weight = 1.5f, sp = 18f, active = shift) { shift = !shift; render() }

    private fun backKey() =
        special("⌫", weight = 1.5f, sp = 18f, repeat = true) { sendKey(KeyEvent.KEYCODE_DEL, metaState()) }

    private fun spaceKey(weight: Float, style: Int = NORMAL) =
        K("espaço", style, weight, sp = 13f) { typeChar(" ") }

    private fun enterKey() = K("↵", ACCENT, 1.5f, sp = 18f) { enter() }

    private fun rounded(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(8).toFloat()
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
            SPECIAL -> Triple(Cor.ESPECIAL, Cor.ESPECIAL_DOWN, Cor.ESPECIAL_TEXTO)
            SHORTCUT -> Triple(Cor.ATALHO, Cor.ATALHO_DOWN, Cor.ATALHO_TEXTO)
            ACCENT -> Triple(Cor.ATIVO, Cor.ATIVO_DOWN, Cor.ATIVO_TEXTO)
            else -> Triple(Cor.TECLA, Cor.TECLA_DOWN, Cor.TECLA_TEXTO)
        }
        val tv = TextView(this)
        tv.gravity = Gravity.CENTER
        tv.includeFontPadding = false
        tv.setTextColor(fg)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, k.sp)
        tv.background = keyBg(bg, down)
        tv.isClickable = true

        val sub = k.sub
        if (sub != null) {
            val s = SpannableString(k.label + "\n" + sub)
            if (k.style == SHORTCUT) {
                val from = k.label.length + 1
                s.setSpan(RelativeSizeSpan(0.9f), from, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(ForegroundColorSpan(Cor.ATALHO_SUB), from, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tv.text = s
        } else {
            tv.text = k.label
        }

        val slop = dp(6)
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (k.preview) showBalao(v, k.label)
                    if (k.repeat) {
                        k.onTap()
                        repeatAction = k.onTap
                        handler.postDelayed(repeatRunnable, 400)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val inside = e.x >= -slop && e.x <= v.width + slop &&
                        e.y >= -slop && e.y <= v.height + slop
                    v.isPressed = false
                    stopRepeat()
                    hideBalaoSoon()
                    if (k.repeat) afterKey() else if (inside) k.onTap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                    hideBalaoSoon()
                }
            }
            true
        }
        return tv
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
        val y = loc[1] - floc[1] - dp(60)
        b.text = text
        b.translationX = x.toFloat()
        b.translationY = y.toFloat()
        b.visibility = View.VISIBLE
    }

    private fun hideBalaoSoon() {
        handler.removeCallbacks(hideBalao)
        handler.postDelayed(hideBalao, 110)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}

// Tela simples para ativar e escolher o teclado depois de instalar o APK
class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, pad)

        val b1 = Button(this)
        b1.text = "1. Ativar o teclado"
        b1.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }

        val b2 = Button(this)
        b2.text = "2. Escolher o teclado"
        b2.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        val t = EditText(this)
        t.hint = "Campo de teste"

        box.addView(b1)
        box.addView(b2)
        box.addView(t)
        setContentView(box)
    }
}
