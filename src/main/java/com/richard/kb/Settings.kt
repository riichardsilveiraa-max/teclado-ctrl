package com.richard.kb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

// Ajustes salvos no aparelho
class Cfg(
    val vibrate: Boolean = false,
    val preview: Boolean = true,
    val shortcuts: Boolean = true,
    val toolbar: Boolean = true,
    val codeRow: Boolean = true,
    val keyH: Int = 40,
    val fontPct: Int = 100,
    val theme: Int = 0
)

object Prefs {
    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("kb", Context.MODE_PRIVATE)

    fun load(c: Context): Cfg {
        val s = sp(c)
        return Cfg(
            vibrate = s.getBoolean("vibrate", false),
            preview = s.getBoolean("preview", true),
            shortcuts = s.getBoolean("shortcuts", true),
            toolbar = s.getBoolean("toolbar", true),
            codeRow = s.getBoolean("coderow", true),
            keyH = s.getInt("keyh", 40),
            fontPct = s.getInt("fontpct", 100),
            theme = s.getInt("theme", 0)
        )
    }
}

// Tela que abre pelo ícone do app: ativar o teclado e mudar os ajustes
class SetupActivity : Activity() {

    private lateinit var sp: SharedPreferences
    private lateinit var box: LinearLayout

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun text(t: String, size: Float, bold: Boolean = false, top: Int = 0) {
        val tv = TextView(this)
        tv.text = t
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top)
        box.addView(tv, lp)
    }

    private fun addSwitch(label: String, key: String, def: Boolean) {
        val s = Switch(this)
        s.text = label
        s.isChecked = sp.getBoolean(key, def)
        s.setOnCheckedChangeListener { _, v -> sp.edit().putBoolean(key, v).apply() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        box.addView(s, lp)
    }

    private fun addSeek(label: String, key: String, min: Int, max: Int, def: Int, suffix: String) {
        val cur = sp.getInt(key, def)
        val t = TextView(this)
        t.text = "$label: $cur$suffix"
        val s = SeekBar(this)
        s.max = max - min
        s.progress = cur - min
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + min
                t.text = "$label: $v$suffix"
                if (fromUser) sp.edit().putInt(key, v).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(14)
        box.addView(t, lp)
        box.addView(s)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = Prefs.sp(this)
        val pad = dp(20)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, pad)
        scroll.addView(box)

        text("Teclado Top", 24f, true)

        val b1 = Button(this)
        b1.text = "1. Ativar o teclado"
        b1.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        val b2 = Button(this)
        b2.text = "2. Escolher o teclado"
        b2.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        box.addView(b1)
        box.addView(b2)

        text("Ajustes", 18f, true, 18)
        text("Valem na próxima vez que o teclado abrir.", 13f)
        addSwitch("Vibrar ao tocar nas teclas", "vibrate", false)
        addSwitch("Balão de prévia da tecla", "preview", true)
        addSwitch("Linha de atalhos (Ctrl+A, C, V, X, Z)", "shortcuts", true)
        addSwitch("Linha Fn, Esc, Tab, Ctrl, Alt e setas", "toolbar", true)
        addSwitch("Linha de símbolos de código", "coderow", true)
        addSeek("Altura das teclas", "keyh", 32, 52, 40, " dp")
        addSeek("Tamanho do texto", "fontpct", 80, 130, 100, "%")

        text("Tema", 16f, true, 18)
        val rg = RadioGroup(this)
        val names = listOf("Escuro", "Preto (AMOLED)", "Claro")
        for (i in names.indices) {
            val rb = RadioButton(this)
            rb.text = names[i]
            rb.id = 1000 + i
            rg.addView(rb)
        }
        rg.check(1000 + sp.getInt("theme", 0))
        rg.setOnCheckedChangeListener { _, id -> sp.edit().putInt("theme", id - 1000).apply() }
        box.addView(rg)

        text("Dicas", 16f, true, 18)
        text(
            "• Toque longo numa letra: acentos e números (q = 1, w = 2...)\n" +
                "• Deslize na barra de espaço: move o cursor\n" +
                "• Toque longo no espaço ou no globo: trocar de teclado\n" +
                "• Toque duplo em Ctrl, Alt ou Shift: trava\n" +
                "• Toque longo em Ctrl+V: histórico da área de transferência\n" +
                "• Fn: F1 a F12, Home, End, PgUp, PgDn, Ins e Del",
            14f
        )

        text("Campo de teste", 16f, true, 18)
        val t = EditText(this)
        t.hint = "Digite aqui"
        box.addView(t)

        setContentView(scroll)
    }
}
