package org.jetbrains.kastle

/**
 * Thrown when trying to resolve a variable from an expression when it is not declared.
 */
class UndefinedVariableException(variableName: String) : IllegalArgumentException("Undefined variable: $variableName")