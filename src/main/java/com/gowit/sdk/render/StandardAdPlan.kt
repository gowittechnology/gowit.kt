package com.gowit.sdk.render

import com.gowit.sdk.capability.CapabilityRefusal
import com.gowit.sdk.capability.CapabilityToken
import com.gowit.sdk.capability.GowitCapabilities
import com.gowit.sdk.capability.RendererCapability
import com.gowit.sdk.model.Ad
import java.net.URI
import java.net.URISyntaxException

/**
 * The semantic role a node fills, bound to a native view by the renderer rather than to a pixel
 * position by the server. Roles are what let one profile produce an appropriate layout on each
 * platform instead of one platform's layout everywhere.
 */
enum class AdSlotRole(val wire: String) {
    ROOT("root"),
    MEDIA("media"),
    HEADLINE("headline"),
    PRICE("price"),
    RATING("rating"),
    SPONSORSHIP_LABEL("sponsorship_label"),
    CALL_TO_ACTION("call_to_action"),
}

/**
 * The bounded declarative grammar for a standard ad.
 *
 * It is bounded on purpose: a node kind that is not in this hierarchy cannot be sent, and a new kind
 * requires a client release. That is the whole difference between declaring capabilities and
 * downloading executable code — which this renderer never does.
 */
sealed class StandardAdNode {
    abstract val role: AdSlotRole

    /**
     * The capability token this node needs in order to be drawn.
     */
    abstract val requiredCapability: String

    /**
     * `primitive.container`
     */
    data class Container(
        override val role: AdSlotRole,
        val children: List<StandardAdNode>,
    ) : StandardAdNode() {
        override val requiredCapability: String = CapabilityToken.Primitive.CONTAINER
    }

    /**
     * `primitive.image`. The URL has already been checked to be an absolute `http`/`https` address by
     * the resolver, so a renderer never fetches from a device-local scheme.
     */
    data class Image(
        override val role: AdSlotRole,
        val url: String,
        val accessibilityLabel: String? = null,
    ) : StandardAdNode() {
        override val requiredCapability: String = CapabilityToken.Primitive.IMAGE
    }

    /**
     * `primitive.text`
     */
    data class Text(
        override val role: AdSlotRole,
        val value: String,
    ) : StandardAdNode() {
        override val requiredCapability: String = CapabilityToken.Primitive.TEXT
    }

    /**
     * `primitive.entity_card` — one promoted entity drawn as a unit.
     */
    data class EntityCard(
        override val role: AdSlotRole,
        val children: List<StandardAdNode>,
    ) : StandardAdNode() {
        override val requiredCapability: String = CapabilityToken.Primitive.ENTITY_CARD
    }

    /**
     * The node's children, empty for the leaf kinds.
     */
    fun childNodes(): List<StandardAdNode> =
        when (this) {
            is Container -> children
            is EntityCard -> children
            is Image -> emptyList()
            is Text -> emptyList()
        }

    /**
     * Every token this node and its descendants need, deduplicated, in first-seen order.
     */
    val requiredCapabilities: List<String>
        get() {
            val seen = mutableListOf<String>()
            collectInto(seen)
            return seen
        }

    private fun collectInto(seen: MutableList<String>) {
        if (!seen.contains(requiredCapability)) {
            seen.add(requiredCapability)
        }
        childNodes().forEach { it.collectInto(seen) }
    }
}

/**
 * What the host may be asked to do when the ad is tapped.
 *
 * The payload is a capability token and an address, and nothing else. There is no field that could
 * name a host selector, a method or a script, so an action can never invoke arbitrary host
 * functionality however it is spelled on the wire.
 */
data class AdActionRequest(
    val token: String,
    val url: String,
)

/**
 * A resolved, executable render plan: a tree this runtime has already been proven able to draw, plus
 * the identity the measurement of it is reported under.
 */
data class StandardAdPlan(
    val adId: String?,
    val root: StandardAdNode,
    val action: AdActionRequest?,
) {
    /**
     * One spoken sentence for the whole ad. The disclosure leads, because a screen-reader user learns
     * it is an ad before they learn what it sells.
     */
    val accessibilitySummary: String
        get() {
            val parts = mutableListOf<String>()
            collectSpokenParts(root, parts)
            return parts.joinToString(", ")
        }

    /**
     * Every capability the plan needs, including the action's.
     */
    val requiredCapabilities: List<String>
        get() {
            val required = root.requiredCapabilities.toMutableList()
            val token = action?.token
            if (token != null && !required.contains(token)) {
                required.add(token)
            }
            return required
        }
}

private fun collectSpokenParts(
    node: StandardAdNode,
    parts: MutableList<String>,
) {
    when (node) {
        is StandardAdNode.Text ->
            if (node.role == AdSlotRole.SPONSORSHIP_LABEL) {
                parts.add(0, node.value)
            } else {
                parts.add(node.value)
            }

        is StandardAdNode.Image -> node.accessibilityLabel?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        is StandardAdNode.Container, is StandardAdNode.EntityCard ->
            node.childNodes().forEach { collectSpokenParts(it, parts) }
    }
}

/**
 * The outcome of resolving one served ad against one capability vector: a plan this runtime can draw,
 * or a structured refusal naming exactly what stopped it.
 */
sealed class StandardAdResolution {
    data class Resolved(val plan: StandardAdPlan) : StandardAdResolution()

    data class Refused(val refusal: CapabilityRefusal) : StandardAdResolution()
}

/**
 * Turns one served ad into a plan this runtime can execute, or refuses with the exact tokens that are
 * missing.
 */
object StandardAdResolver {
    /**
     * The token an opaque-markup ad requires. It is an ISOLATION capability, not a primitive: drawing a
     * frame and confining what runs inside it are different claims, and only the second one makes
     * advertiser markup safe to execute.
     */
    const val SANDBOX_TOKEN = "isolation.sandboxed"

    /**
     * The disclosure every standard ad carries. It is part of the tree rather than a decoration the
     * host may forget, so a plan cannot be drawn without it.
     */
    const val SPONSORSHIP_LABEL = "Sponsored"

    /**
     * Four refusals, and each is deliberate:
     *
     * - An unknown **protocol major** is refused explicitly rather than rendered best-effort.
     * - **Opaque markup** is refused rather than drawn. A known absent capability is incompatibility;
     *   it is never degraded and never silently converted into an embedded browser view. This build
     *   declares `isolation.sandboxed = false`, so an ad whose content is advertiser markup cannot be
     *   rendered here.
     * - A **click address this runtime will not open** is refused rather than dropped. Dropping it
     *   would draw a tappable ad that quietly does nothing, which is indistinguishable from a broken
     *   ad; `action.open_url` means an `http`/`https` address, and no other scheme reaches the host.
     * - A **missing primitive** is refused with every token the tree needed and the vector did not
     *   name, not just the first.
     */
    fun resolve(
        ad: Ad,
        capability: RendererCapability,
    ): StandardAdResolution {
        if (capability.protocolMajor != GowitCapabilities.PROTOCOL_MAJOR) {
            return StandardAdResolution.Refused(
                CapabilityRefusal.unsupportedProtocolMajor(capability.protocolMajor),
            )
        }

        if (!ad.html.isNullOrBlank()) {
            return StandardAdResolution.Refused(refuseOpaqueMarkup(capability))
        }

        val rawClick = ad.clickUrl
        val clickUrl = httpUrlOrNull(rawClick)
        if (!rawClick.isNullOrBlank() && clickUrl == null) {
            return StandardAdResolution.Refused(
                CapabilityRefusal.noCompatibleVariant(
                    "The ad's click address is not an absolute http or https address; this runtime will not open it.",
                ),
            )
        }

        val root =
            buildTree(ad) ?: return StandardAdResolution.Refused(
                CapabilityRefusal.noCompatibleVariant(
                    "The ad carries no media this renderer can draw.",
                ),
            )

        val action = clickUrl?.let { buildAction(capability, it) }
        val plan = StandardAdPlan(adId = ad.adId, root = root, action = action)
        val missing = capability.missingCapabilities(plan.requiredCapabilities)
        if (missing.isNotEmpty()) {
            return StandardAdResolution.Refused(CapabilityRefusal.missingCapability(missing))
        }

        return StandardAdResolution.Resolved(plan)
    }

    private fun refuseOpaqueMarkup(capability: RendererCapability): CapabilityRefusal {
        if (capability.isolation?.sandboxed != true) {
            return CapabilityRefusal.missingCapability(
                listOf(SANDBOX_TOKEN),
                "Opaque advertiser markup requires an isolated profile; this runtime declares neither a " +
                    "frame nor a sandbox.",
            )
        }
        return CapabilityRefusal.missingCapability(
            listOf(SANDBOX_TOKEN),
            "Opaque markup is rendered by the isolated WebView profile, not by the declarative " +
                "standard-ad renderer.",
        )
    }

    /**
     * Prefer an in-app browser when the runtime declares one, and fall back to the system URL open. An
     * action is only ever chosen from tokens the vector NAMES; the fall-through still produces
     * `action.open_url` so that a vector declaring no action at all is refused by the capability gate
     * with that token named, rather than silently losing the click.
     */
    private fun buildAction(
        capability: RendererCapability,
        url: String,
    ): AdActionRequest {
        if (capability.declares(CapabilityToken.Action.IN_APP_BROWSER)) {
            return AdActionRequest(CapabilityToken.Action.IN_APP_BROWSER, url)
        }
        return AdActionRequest(CapabilityToken.Action.OPEN_URL, url)
    }

    /**
     * The Android wire model carries no promoted-entity array, so an [StandardAdNode.EntityCard] is
     * never built from an [Ad] today; the node kind exists because the grammar is shared with the iOS
     * client, whose model does carry one, and because a host may compose one itself.
     */
    private fun buildTree(ad: Ad): StandardAdNode? {
        val imageUrl = httpUrlOrNull(ad.imgUrl) ?: return null
        val children =
            listOf(
                StandardAdNode.Image(AdSlotRole.MEDIA, imageUrl, null),
                StandardAdNode.Text(AdSlotRole.SPONSORSHIP_LABEL, SPONSORSHIP_LABEL),
            )
        return StandardAdNode.Container(AdSlotRole.ROOT, children)
    }
}

/**
 * The raw string when it is an absolute `http` or `https` address, and `null` otherwise. Every address
 * that reaches a view — an image to fetch, a destination to open — passes through here first.
 */
internal fun httpUrlOrNull(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val scheme = schemeOf(raw) ?: return null
    if (scheme != "http" && scheme != "https") return null
    return raw
}

private fun schemeOf(raw: String): String? =
    try {
        URI(raw).takeIf { it.isAbsolute }?.scheme?.lowercase()
    } catch (error: URISyntaxException) {
        null
    }
