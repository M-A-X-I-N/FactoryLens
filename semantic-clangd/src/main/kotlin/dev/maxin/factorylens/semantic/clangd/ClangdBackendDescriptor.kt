package dev.maxin.factorylens.semantic.clangd

import dev.maxin.factorylens.core.FactoryLensProduct

public object ClangdBackendDescriptor {
    public const val BACKEND_NAME: String = "clangd"

    public fun describe(): String =
        FactoryLensProduct.NAME + " semantic backend: " + BACKEND_NAME
}
