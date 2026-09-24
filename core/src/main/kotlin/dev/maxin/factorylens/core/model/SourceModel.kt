package dev.maxin.factorylens.core.model

/**
 * Zero-based source coordinate.
 *
 * FactoryLens keeps coordinates independent from Rider and clangd object models.
 * Frontends/backends are responsible for adapting their native coordinate types.
 */
public data class SourcePosition(
    public val line: Int,
    public val column: Int,
) {
    init {
        require(line >= 0) { "SourcePosition.line must be zero or greater." }
        require(column >= 0) { "SourcePosition.column must be zero or greater." }
    }
}

public data class SourceRange(
    public val start: SourcePosition,
    public val end: SourcePosition,
) {
    init {
        require(!end.isBefore(start)) {
            "SourceRange.end must not be before SourceRange.start."
        }
    }
}

public data class SourceLocation(
    public val uri: SourceUri,
    public val range: SourceRange? = null,
)

public data class NavigationTargets(
    public val declaration: SourceLocation? = null,
    public val definition: SourceLocation? = null,
    public val callSite: SourceLocation? = null,
) {
    public fun preferred(): SourceLocation? =
        callSite ?: definition ?: declaration
}

private fun SourcePosition.isBefore(other: SourcePosition): Boolean =
    line < other.line || (line == other.line && column < other.column)
