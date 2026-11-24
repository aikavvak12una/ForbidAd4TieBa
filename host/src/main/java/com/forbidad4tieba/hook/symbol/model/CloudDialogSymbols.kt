package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Restored single-candidate admission and its published DTO; no runtime discovery is needed. */
data class CloudDialogSymbols(
    val admissionMethod: Method,
    val dialogNameField: Field,
    val dialogUrlField: Field,
    val dialogStrategiesField: Field,
    val strategyTypeMethod: Method,
    val resultConstructor: Constructor<*>,
    val contextTimeMethod: Method,
    val timeNameMethod: Method,
)

/** The boolean consumer is retained for exact restoration, but is not a Hook callback dependency. */
internal data class CloudDialogScanSymbols(
    val targets: CloudDialogSymbols,
    val resultAllowedMethod: Method,
)
