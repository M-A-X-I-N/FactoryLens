package dev.maxin.factorylens.rider

import com.intellij.openapi.components.Service
import dev.maxin.factorylens.core.FactoryLensProduct

@Service(Service.Level.PROJECT)
public class FactoryLensProjectService {
    public fun productName(): String = FactoryLensProduct.NAME
}
