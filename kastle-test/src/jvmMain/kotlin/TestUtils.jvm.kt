package org.jetbrains.kastle

private const val UPDATE_GENERATOR_SNAPSHOTS_KEY = "UPDATE_GENERATOR_SNAPSHOTS"

actual fun shouldReplaceSnapshots(): Boolean =
    System.getProperty(UPDATE_GENERATOR_SNAPSHOTS_KEY) != null ||
            System.getenv(UPDATE_GENERATOR_SNAPSHOTS_KEY).toBooleanStrictOrNull() ?: false