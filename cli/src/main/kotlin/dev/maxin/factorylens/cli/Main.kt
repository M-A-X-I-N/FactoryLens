package dev.maxin.factorylens.cli

import dev.maxin.factorylens.core.FactoryLensProduct
import dev.maxin.factorylens.semantic.clangd.ClangdBackendDescriptor

public fun main() {
    println(FactoryLensProduct.NAME + " CLI scaffold")
    println(ClangdBackendDescriptor.describe())
}
