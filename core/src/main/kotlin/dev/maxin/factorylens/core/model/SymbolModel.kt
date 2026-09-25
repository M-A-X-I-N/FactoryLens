package dev.maxin.factorylens.core.model

public enum class SymbolKind {
    FUNCTION,
    METHOD,
    CONSTRUCTOR,
    DESTRUCTOR,
    TYPE,
    FIELD,
    VARIABLE,
    NAMESPACE,
    OTHER,
}

public enum class SourceRealm {
    TARGET,
    DEPENDENCY_MOD,
    SML,
    FACTORY_GAME,
    UNREAL_ENGINE,
    GENERATED,
    OTHER_EXTERNAL,
    UNKNOWN,
}

public fun interface SourceRealmClassifier {
    public fun classify(uri: SourceUri): SourceRealm
}

public data class SymbolDescriptor(
    public val id: SymbolId,
    public val displayName: String,
    public val qualifiedName: String? = null,
    public val kind: SymbolKind,
    public val realm: SourceRealm = SourceRealm.UNKNOWN,
    public val navigation: NavigationTargets = NavigationTargets(),
) {
    init {
        require(displayName.isNotBlank()) { "SymbolDescriptor.displayName must not be blank." }
    }
}
