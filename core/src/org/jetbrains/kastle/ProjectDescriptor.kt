package org.jetbrains.kastle

data class ProjectDescriptor(
    val name: String,
    val group: String,
    val properties: Map<String, Any>,
    val features: List<FeatureId>,
)