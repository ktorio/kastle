package org.jetbrains.kastle

actual fun shouldReplaceSnapshots(): Boolean =
    System.getProperty("UPDATE_GENERATOR_SNAPSHOTS") != null