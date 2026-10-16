package app.oxygen.core.markdown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.oxygen.ui.theme.OxType
import app.oxygen.ui.theme.OxygenColors
object CodeHighlighter {
    enum class Kind { Keyword, Str, Comment, Number }
    data class Span(val start: Int, val end: Int, val kind: Kind)
    private class LangSpec(
        val keywords: Set<String>,
        val lineComment: String?,
        val blockComment: Pair<String, String>?,
        val hashComment: Boolean = false,
    )
    private val commonKeywords = setOf(
        "if", "else", "for", "while", "return", "break", "continue", "class", "interface", "object",
        "fun", "val", "var", "def", "function", "const", "let", "new", "import", "from", "export",
        "package", "public", "private", "protected", "internal", "static", "final", "open", "override",
        "abstract", "sealed", "data", "enum", "struct", "type", "typealias", "when", "match", "case",
        "switch", "try", "catch", "finally", "throw", "throws", "extends", "implements", "in", "is",
        "as", "null", "nil", "None", "True", "False", "true", "false", "this", "self", "super",
        "void", "int", "float", "double", "char", "bool", "boolean", "string", "String", "long", "short",
        "byte", "async", "await", "yield", "lambda", "with", "pass", "raise", "except", "global",
        "nonlocal", "del", "assert", "and", "or", "not", "elif", "then", "end", "do", "module",
        "use", "pub", "mut", "impl", "trait", "where", "loop", "unsafe", "extern", "crate", "fn",
        "go", "defer", "chan", "select", "range", "var", "func", "map", "goto", "signed", "unsigned",
        "sizeof", "typedef", "union", "register", "volatile", "namespace", "using", "template",
        "virtual", "friend", "operator", "explicit", "inline", "constexpr", "auto", "let", "guard",
        "extension", "protocol", "instanceof", "typeof", "delete", "of", "get", "set", "init",
        "require", "module", "declare", "readonly", "satisfies", "keyof", "infer", "never", "unknown",
        "any", "number", "object", "symbol", "bigint", "void", "true", "false", "undefined",
        "select", "insert", "update", "delete", "where", "join", "left", "right", "inner", "outer",
        "group", "order", "limit", "offset", "values", "into", "table", "create", "alter", "drop",
        "primary", "foreign", "references", "distinct", "having", "union", "all", "exists", "between",
        "like", "index", "on", "and", "or", "not", "null", "is", "as", "asc", "desc",
        "echo", "fi", "done", "esac", "local", "export", "source", "alias", "sudo", "cd", "then",
        "elif", "elif", "elif",
    )
    private val languages: Map<String, LangSpec> = buildMap {
        fun put(name: String, line: String?, block: Pair<String, String>?) {
            put(name, LangSpec(commonKeywords, line, block))
        }
        for (name in listOf("kotlin", "kt", "java", "c", "cpp", "c++", "csharp", "cs", "javascript", "js", "typescript", "ts", "tsx", "jsx", "go", "golang", "rust", "rs", "swift", "dart", "php", "scala", "groovy", "gradle", "json5")) {
            put(name, "//", "/*" to "*/")
        }
        for (name in listOf("python", "py", "ruby", "rb", "yaml", "yml", "toml", "sh", "bash", "zsh", "shell", "dockerfile", "makefile", "make", "r", "ini", "conf")) {
            put(name, "#", null)
        }
        put("json", null, null)
        put("sql", "--", "/*" to "*/")
        put("css", null, "/*" to "*/")
        put("scss", "//", "/*" to "*/")
        put("html", null, "<!--" to "-->")
        put("xml", null, "<!--" to "-->")
        put("lua", "--", null)
        put("haskell", "--", "{-" to "-}")
        put("elixir", "#", null)
        put("erlang", "%", null)
        put("clojure", ";", null)
        put("lisp", ";", null)
    }
    private val regexCache = HashMap<String, Regex>()
    fun highlight(code: String, language: String?): List<Span> {
        if (code.length > 60_000) return emptyList()
        val key = language?.lowercase()?.trim() ?: return emptyList()
        val spec = languages[key] ?: return emptyList()
        val regex = regexCache.getOrPut(key) { buildRegex(spec) }
        val spans = ArrayList<Span>()
        for (match in regex.findAll(code)) {
            val range = match.range
            val group = match.groups
            val kind = when {
                spec.lineComment != null && group[1]?.range == range -> Kind.Comment
                spec.blockComment != null && group[2]?.range == range -> Kind.Comment
                group[3]?.range == range -> Kind.Str
                group[4]?.range == range -> Kind.Number
                else -> Kind.Keyword
            }
            spans += Span(range.first, range.last + 1, kind)
        }
        return spans
    }
    fun annotate(code: String, language: String?, colors: OxygenColors, dark: Boolean): AnnotatedString {
        val spans = highlight(code, language)
        if (spans.isEmpty()) {
            return buildAnnotatedString { withStyle(SpanStyle(fontFamily = OxType.code.fontFamily)) { append(code) } }
        }
        return buildAnnotatedString {
            var cursor = 0
            for (span in spans) {
                if (span.start > cursor) append(code, cursor, span.start)
                withStyle(
                    SpanStyle(
                        fontFamily = OxType.code.fontFamily,
                        color = spanColor(span.kind, colors, dark),
                    ),
                ) {
                    append(code, span.start, span.end)
                }
                cursor = span.end
            }
            if (cursor < code.length) append(code, cursor, code.length)
        }
    }
    private fun spanColor(kind: Kind, colors: OxygenColors, dark: Boolean) = when (kind) {
        Kind.Keyword -> if (dark) androidx.compose.ui.graphics.Color(0xFFC4B5FD) else androidx.compose.ui.graphics.Color(0xFF7C3AED)
        Kind.Str -> if (dark) androidx.compose.ui.graphics.Color(0xFF86EFAC) else androidx.compose.ui.graphics.Color(0xFF15803D)
        Kind.Comment -> colors.textMuted
        Kind.Number -> if (dark) androidx.compose.ui.graphics.Color(0xFFFCD34D) else androidx.compose.ui.graphics.Color(0xFFB45309)
    }
    private fun buildRegex(spec: LangSpec): Regex {
        val parts = ArrayList<String>()
        parts += spec.lineComment?.let { "(?m)(${Regex.escape(it)}.*)" } ?: "(?!x)x"
        parts += spec.blockComment?.let { "(${Regex.escape(it.first)}[\\s\\S]*?${Regex.escape(it.second)})" } ?: "(?!x)x"
        parts += "(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`)"
        parts += "(\\b0[xX][0-9a-fA-F]+\\b|\\b\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?[fFlLdDuU]?\\b)"
        val keywords = spec.keywords.joinToString("|") { Regex.escape(it) }
        parts += "(?<![\\w$])($keywords)(?![\\w$])"
        return Regex(parts.joinToString("|"))
    }
}
