package com.richard.kb

import android.graphics.Color

// Paleta de cores de um tema
class Palette(
    val fundo: Int,
    val tecla: Int,
    val teclaDown: Int,
    val teclaTexto: Int,
    val especial: Int,
    val especialDown: Int,
    val especialTexto: Int,
    val atalho: Int,
    val atalhoDown: Int,
    val atalhoTexto: Int,
    val atalhoSub: Int,
    val ativo: Int,
    val ativoDown: Int,
    val ativoTexto: Int,
    val balao: Int,
    val balaoTexto: Int
)

object Themes {
    private fun c(s: String): Int = Color.parseColor(s)

    // 0 = Escuro (estilo Gboard)
    val escuro = Palette(
        c("#1B1C1E"), c("#46484C"), c("#62656A"), c("#F1F3F4"),
        c("#2C2E31"), c("#3D4044"), c("#C4C7C5"),
        c("#2A3B5C"), c("#3A4F78"), c("#A8C7FA"), c("#8FB0E8"),
        c("#8AB4F8"), c("#A8C7FA"), c("#0A2A66"),
        c("#5F6368"), c("#FFFFFF")
    )

    // 1 = Preto (AMOLED)
    val preto = Palette(
        c("#000000"), c("#2B2B2D"), c("#4A4A4E"), c("#F1F3F4"),
        c("#161618"), c("#2B2B2D"), c("#BDBFC1"),
        c("#14233D"), c("#223A63"), c("#A8C7FA"), c("#8FB0E8"),
        c("#8AB4F8"), c("#A8C7FA"), c("#0A2A66"),
        c("#3C3C40"), c("#FFFFFF")
    )

    // 2 = Claro
    val claro = Palette(
        c("#E3E5E8"), c("#FFFFFF"), c("#C9CCD1"), c("#1F1F1F"),
        c("#CDD0D5"), c("#B7BBC1"), c("#2B2D30"),
        c("#D3E3FD"), c("#B5CDF5"), c("#0B3B8C"), c("#2F5FB5"),
        c("#1A73E8"), c("#4A90F0"), c("#FFFFFF"),
        c("#FFFFFF"), c("#1F1F1F")
    )

    fun get(i: Int): Palette = when (i) {
        1 -> preto
        2 -> claro
        else -> escuro
    }
}

object Data {
    // Toque longo nas letras da primeira linha mostra o número (como no Gboard)
    val digitFor: Map<String, String> = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0"
    )

    // Variações com acento (toque longo na letra)
    val accents: Map<String, List<String>> = mapOf(
        "a" to listOf("á", "à", "â", "ã", "ä", "å", "ª"),
        "e" to listOf("é", "è", "ê", "ë"),
        "i" to listOf("í", "ì", "î", "ï"),
        "o" to listOf("ó", "ò", "ô", "õ", "ö", "º"),
        "u" to listOf("ú", "ù", "û", "ü"),
        "c" to listOf("ç"),
        "n" to listOf("ñ"),
        "y" to listOf("ý", "ÿ")
    )

    // Variações dos símbolos (toque longo)
    val symbols: Map<String, List<String>> = mapOf(
        "." to listOf("…", ",", "?", "!", ":", ";", "-", "_"),
        "," to listOf(";", ":"),
        "-" to listOf("–", "—", "•", "_"),
        "_" to listOf("-", "–", "—"),
        "\$" to listOf("R\$", "€", "£", "¥", "¢"),
        "'" to listOf("‘", "’", "‚"),
        "\"" to listOf("“", "”", "„", "«", "»"),
        "?" to listOf("¿"),
        "!" to listOf("¡"),
        "%" to listOf("‰", "°"),
        "=" to listOf("≠", "≈", "≤", "≥", "±"),
        "/" to listOf("\\", "|"),
        "(" to listOf("[", "{", "<"),
        ")" to listOf("]", "}", ">"),
        "<" to listOf("«", "‹", "≤"),
        ">" to listOf("»", "›", "≥"),
        "+" to listOf("±", "×", "÷"),
        "*" to listOf("×", "•", "†"),
        "#" to listOf("№"),
        "&" to listOf("§"),
        "@" to listOf("©", "®", "™")
    )

    // Sufixos para campos de e-mail e endereço
    val web: List<String> = listOf(".com", ".com.br", ".org", ".net")

    // Linha de símbolos de código (rola para o lado)
    val codeKeys: List<String> = listOf(
        "(", ")", "[", "]", "{", "}", "<", ">", ";", ":", "=", "\"", "'", "/", "\\", "|",
        "&", "*", "+", "-", "_", "!", "?", "#", "\$", "%", "^", "~", "@", "`"
    )

    // Emojis por categoria: ícone da aba e lista
    val emoji: List<Pair<String, List<String>>> = listOf(
        "😀" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😍", "🥰", "😘",
            "😋", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺", "😢",
            "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔",
            "🤭", "🤫", "😶", "😐", "😑", "😬", "🙄", "😯", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🤢",
            "🤮", "🤧", "😷", "🤒", "🤕", "🤠", "🤓", "🧐", "😈", "👿", "💀", "👻", "👽", "🤖", "💩"
        ),
        "👍" to listOf(
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖖",
            "👋", "🤝", "🙏", "✍️", "💪", "👏", "🙌", "👐", "🤲", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤",
            "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "🔥", "⭐", "✨", "💯", "✅",
            "❌", "⚠️", "❓", "❗", "💤", "💬", "👀", "🧠", "🎉", "🎊", "🏆", "🥇"
        ),
        "🐶" to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
            "🙉", "🙊", "🐔", "🐧", "🐦", "🦆", "🦉", "🐴", "🦄", "🐝", "🦋", "🐢", "🐍", "🐙", "🐬", "🐳",
            "🌹", "🌻", "🌳", "🍎", "🍌", "🍇", "🍓", "🍉", "🍍", "🥑", "🍕", "🍔", "🍟", "🌭", "🍿", "🍩",
            "🍪", "🎂", "🍰", "🍫", "🍺", "🍷", "☕", "🥤", "🍳", "🥩"
        ),
        "⚽" to listOf(
            "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🎮", "🎲", "🎯", "🎵", "🎶", "🎤", "🎧", "🎸", "📱", "💻",
            "⌨️", "🖥️", "💡", "🔋", "📷", "📞", "⏰", "📌", "📎", "✏️", "📚", "💰", "💳", "🚗", "✈️", "🚀",
            "🏠", "📍", "🔒", "🔑", "🔔", "📢", "📧", "📝", "📊", "📈", "📉", "💼", "🛒", "🎁", "⏳", "🔍",
            "⚙️", "🔧"
        )
    )
}
