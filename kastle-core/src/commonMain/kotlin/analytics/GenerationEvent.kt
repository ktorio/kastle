package org.jetbrains.kastle.analytics

import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.PackagingStyle
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.VariableId

/**
 * Represents a project generation event for analytics tracking.
 *
 * @property projectName Name of the generated project
 * @property projectGroup Group/package of the generated project
 * @property packaging Packaging style used (FLAT or NESTED)
 * @property packs List of packs used in the generation
 * @property properties Map of property values used per pack
 * @property additionalParameters Additional parameters extracted from the request (e.g., userAgent, productName, userId).
 *           See [RequestMappings] for details.
 */
data class GenerationEvent(
    val projectName: String,
    val projectGroup: String,
    val packaging: PackagingStyle,
    val packs: List<PackId>,
    val properties: Map<VariableId, String>,
    val additionalParameters: Map<String, String> = emptyMap(),
) {
    companion object {
        fun from(
            descriptor: ProjectDescriptor,
            additionalParameters: Map<String, String> = emptyMap()
        ): GenerationEvent {
            return GenerationEvent(
                projectName = descriptor.name,
                projectGroup = descriptor.group,
                packaging = descriptor.packaging,
                packs = descriptor.packs,
                properties = descriptor.properties,
                additionalParameters = additionalParameters,
            )
        }
    }
}
