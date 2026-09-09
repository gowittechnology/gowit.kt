package com.gowit.sdk.capability

/**
 * The namespaced capability tokens this client understands.
 *
 * The token set is deliberately **open**. A reader must ignore a token it does not know rather than
 * refuse the vector that carries it, so these constants are the tokens this build can justify — not a
 * closed universe.
 *
 * New primitives, privileged actions and unsupported codecs require a client release; changing a
 * supported recipe does not. That asymmetry is the reason the client declares capabilities rather than
 * a version.
 */
object CapabilityToken {
    /**
     * What the runtime can DRAW. Namespace `primitive.*`.
     */
    object Primitive {
        const val IMAGE = "primitive.image"
        const val TEXT = "primitive.text"
        const val CONTAINER = "primitive.container"
        const val ENTITY_CARD = "primitive.entity_card"
        const val VIDEO = "primitive.video"
        const val HTML_FRAME = "primitive.html_frame"
    }

    /**
     * What the runtime can DO on a click. Namespace `action.*`.
     *
     * An action the runtime does not declare makes the variant incompatible; it does **not** make the
     * click a no-op, and an unknown action name may never invoke arbitrary host functionality.
     */
    object Action {
        const val OPEN_URL = "action.open_url"
        const val ENTITY_DESTINATION = "action.entity_destination"
        const val IN_APP_BROWSER = "action.in_app_browser"
        const val HOST_CALLBACK = "action.host_callback"
    }

    /**
     * What the runtime can MEASURE and report. Namespace `observation.*`.
     *
     * A renderer without completion measurement cannot advertise completion coverage: omitting
     * [VIDEO_COMPLETION] from the declared vector is what refuses a variant whose profile requires it.
     */
    object Observation {
        const val ROOT_IMPRESSION = "observation.root_impression"
        const val VIEWABILITY = "observation.viewability"
        const val CLICK = "observation.click"
        const val ENTITY_EXPOSURE = "observation.entity_exposure"
        const val VIDEO_COMPLETION = "observation.video_completion"
        const val FOREGROUND_STATE = "observation.foreground_state"
    }
}
