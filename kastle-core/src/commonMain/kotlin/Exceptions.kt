package org.jetbrains.kastle

/**
 * Thrown when trying to resolve a variable from an expression when it is not declared.
 */
class UndefinedVariableException(variableName: String) : IllegalArgumentException("Undefined variable: $variableName")

/**
 * On an attempt to read a pack that does not exist.
 */
class MissingPackException(pack: PackId) : Exception("Missing pack: $pack")

/**
 * General failure when attempting to read a pack.
 */
class PackReadException(pack: PackId, cause: Throwable) : Exception("Failed to read pack: $pack", cause)