package com.gowit.sdk.videoAd

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gowit.sdk.core.Logger
import com.gowit.sdk.vast.VASTAd
import com.gowit.sdk.vast.VASTError
import com.gowit.sdk.vast.VASTEventTracker
import com.gowit.sdk.vast.VASTLinear
import com.gowit.sdk.vast.VASTParser
import com.gowit.sdk.vast.VASTTrackingEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A self-contained video ad view that loads, parses, and plays a VAST ad.
 *
 * This view handles the full VAST lifecycle:
 * - Fetches the VAST tag URL
 * - Parses the VAST XML (including Wrapper redirects)
 * - Extracts the best MP4 media file
 * - Plays the video with [MediaPlayer] and a [TextureView]
 * - Fires VAST tracking events (impression, quartiles, complete, click, error)
 * - Shows an "Ad" label badge and an optional mute/unmute button
 * - Auto-plays and pauses based on scroll visibility
 *
 * ## XML Usage
 * ```xml
 * <com.gowit.sdk.videoAd.VideoAdView
 *     android:id="@+id/videoAdView"
 *     android:layout_width="match_parent"
 *     android:layout_height="200dp" />
 * ```
 *
 * ## Kotlin Usage
 * ```kotlin
 * videoAdView.setup(
 *     lifecycleOwner = viewLifecycleOwner,
 *     vastUrl        = ad.vastTag ?: return,
 *     configuration  = VideoAdConfiguration.default,
 *     callbacks      = VideoAdCallbacks(
 *         onAdLoaded    = { vastAd -> /* ready */    },
 *         onAdStarted   = {           /* playing */  },
 *         onAdCompleted = {           /* done */     },
 *         onAdClicked   = { url -> openBrowser(url) },
 *         onError       = { err -> Log.e("VAST", err.message) },
 *     ),
 * )
 * ```
 */
class VideoAdView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {

        // ---- Child views ----
        private lateinit var textureView: TextureView
        private var adLabelView: TextView? = null
        private var muteButton: ImageButton? = null
        private var loadingTextView: TextView? = null
        private var placeholderView: View? = null

        // ---- VAST state ----
        private var vastUrl: String = ""
        private var configuration = VideoAdConfiguration.default
        private var callbacks: VideoAdCallbacks? = null

        private var currentAd: VASTAd? = null
        private var currentLinear: VASTLinear? = null
        private var videoUrl: String? = null

        private var state: VideoAdState = VideoAdState.Idle
            set(value) {
                field = value
                mainHandler.post { callbacks?.onStateChanged?.invoke(value) }
                updateVisibilityForState(value)
            }

        // ---- Playback ----
        private var mediaPlayer: MediaPlayer? = null
        private var surfaceTexture: SurfaceTexture? = null
        private var isMuted: Boolean = true
        private var isVisible: Boolean = false
        private var startEventFired = false

        private val firedQuartiles = mutableSetOf<VASTTrackingEventType>()
        private val firedProgress = mutableSetOf<Double>()

        // ---- Tracking ----
        private val eventTracker = VASTEventTracker.shared
        private val parser = VASTParser()

        // ---- Threading ----
        private val mainHandler = Handler(Looper.getMainLooper())
        private val trackingHandler = Handler(Looper.getMainLooper())
        private var muteButtonHideHandler = Handler(Looper.getMainLooper())
        private var viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // ---- Scroll visibility listener ----
        private val scrollListener =
            ViewTreeObserver.OnScrollChangedListener { checkAndUpdateVisibility() }

        init {
            setupTextureView()
        }

        // MARK: - Public API

        /**
         * Load and play a VAST ad. Attaches the lifecycle so playback pauses when
         * the owner is stopped and resumes when it is started again.
         *
         * @param lifecycleOwner The [LifecycleOwner] that owns this view (Activity or Fragment)
         * @param vastUrl The VAST tag URL to fetch and parse
         * @param configuration Visual and playback configuration (default: [VideoAdConfiguration.default])
         * @param callbacks Callbacks for ad lifecycle events
         */
        fun setup(
            lifecycleOwner: LifecycleOwner,
            vastUrl: String,
            configuration: VideoAdConfiguration = VideoAdConfiguration.default,
            callbacks: VideoAdCallbacks? = null,
        ) {
            this.vastUrl = vastUrl
            this.configuration = configuration
            this.callbacks = callbacks
            this.isMuted = configuration.isMutedByDefault

            applyCornerRadius()
            setupOverlays()
            isMuted = configuration.isMutedByDefault

            lifecycleOwner.lifecycleScope.launch {
                loadAd()
            }
        }

        /** Pause playback programmatically */
        fun pause() {
            if (state == VideoAdState.Playing) {
                mediaPlayer?.pause()
                state = VideoAdState.Paused
            }
        }

        /** Resume playback programmatically */
        fun resume() {
            if (state == VideoAdState.Paused || state == VideoAdState.Ready) {
                performPlay()
            }
        }

        /** Toggle mute/unmute */
        fun toggleMute() {
            isMuted = !isMuted
            val volume = if (isMuted) 0f else 1f
            mediaPlayer?.setVolume(volume, volume)
            updateMuteButtonIcon()
            showMuteButtonBriefly()

            currentLinear?.trackingEvents?.let { events ->
                if (isMuted) eventTracker.fireMute(events) else eventTracker.fireUnmute(events)
            }
        }

        /** Returns the current [VideoAdState] */
        fun getState(): VideoAdState = state

        // MARK: - View Lifecycle

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            viewTreeObserver?.addOnScrollChangedListener(scrollListener)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            viewTreeObserver?.removeOnScrollChangedListener(scrollListener)
            trackingHandler.removeCallbacksAndMessages(null)
            muteButtonHideHandler.removeCallbacksAndMessages(null)
            releaseMediaPlayer()
        }

        override fun onVisibilityChanged(
            changedView: View,
            visibility: Int,
        ) {
            super.onVisibilityChanged(changedView, visibility)
            checkAndUpdateVisibility()
        }

        // MARK: - Texture View Setup

        private fun setupTextureView() {
            textureView =
                TextureView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    surfaceTextureListener = surfaceTextureListener()
                    setOnClickListener { handleTap() }
                }
            addView(textureView)
        }

        private fun surfaceTextureListener() =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    surfaceTexture = surface
                    // If the video URL was already resolved before the surface was ready, set it now
                    videoUrl?.let { attachSurfaceToPlayer(surface, it) }
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    surfaceTexture = null
                    return false
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }

        // MARK: - Overlays

        private fun setupOverlays() {
            setupLoadingView()
            if (configuration.showAdLabel) setupAdLabel()
            setupMuteButton()
        }

        private fun setupLoadingView() {
            when (val behavior = configuration.loadingBehavior) {
                is VideoAdLoadingBehavior.Hidden -> { /* no-op */ }
                is VideoAdLoadingBehavior.Text -> {
                    loadingTextView =
                        TextView(context).apply {
                            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                            gravity = Gravity.CENTER
                            text = behavior.message
                            setTextColor(Color.GRAY)
                            visibility = View.GONE
                        }
                    addView(loadingTextView)
                }
                is VideoAdLoadingBehavior.ColorPlaceholder -> {
                    placeholderView =
                        View(context).apply {
                            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                            setBackgroundColor(behavior.colorInt)
                            visibility = View.GONE
                        }
                    addView(placeholderView)
                }
            }
        }

        private fun setupAdLabel() {
            adLabelView =
                TextView(context).apply {
                    text = configuration.adLabelText
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    background =
                        GradientDrawable().apply {
                            setColor(Color.argb(153, 0, 0, 0)) // 60% black
                        }
                    layoutParams =
                        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                            gravity = Gravity.TOP or Gravity.START
                            setMargins(dp(configuration.muteButtonPaddingDp), dp(configuration.muteButtonPaddingDp), 0, 0)
                        }
                }
            addView(adLabelView)
        }

        private fun setupMuteButton() {
            if (configuration.muteButtonBehavior == VideoAdMuteButtonBehavior.ALWAYS_HIDE) return

            muteButton =
                ImageButton(context).apply {
                    val size = dp(configuration.muteButtonSizeDp)
                    layoutParams =
                        LayoutParams(size, size).apply {
                            gravity = cornerGravity(configuration.muteButtonCorner)
                            val pad = dp(configuration.muteButtonPaddingDp)
                            // Offset horizontally when ad label is in the same top-start corner
                            val adLabelOffset = if (configuration.showAdLabel && configuration.muteButtonCorner == VideoAdMuteButtonCorner.TOP_START) {
                                dp(configuration.muteButtonSizeDp + configuration.muteButtonPaddingDp + 8)
                            } else {
                                pad
                            }
                            when (configuration.muteButtonCorner) {
                                VideoAdMuteButtonCorner.TOP_START -> setMargins(adLabelOffset, pad, 0, 0)
                                VideoAdMuteButtonCorner.TOP_END -> setMargins(0, pad, pad, 0)
                                VideoAdMuteButtonCorner.BOTTOM_START -> setMargins(pad, 0, 0, pad)
                                VideoAdMuteButtonCorner.BOTTOM_END -> setMargins(0, 0, pad, pad)
                            }
                        }
                    setBackgroundColor(Color.TRANSPARENT)
                    updateMuteButtonIcon(this)
                    alpha = if (configuration.muteButtonBehavior == VideoAdMuteButtonBehavior.ALWAYS_SHOW) 1f else 0f
                    setOnClickListener { toggleMute() }
                }
            addView(muteButton)
        }

        private fun cornerGravity(corner: VideoAdMuteButtonCorner): Int =
            when (corner) {
                VideoAdMuteButtonCorner.TOP_START -> Gravity.TOP or Gravity.START
                VideoAdMuteButtonCorner.TOP_END -> Gravity.TOP or Gravity.END
                VideoAdMuteButtonCorner.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
                VideoAdMuteButtonCorner.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
            }

        // MARK: - Ad Loading

        private suspend fun loadAd() {
            if (state != VideoAdState.Idle) return
            state = VideoAdState.Loading
            showLoadingState()

            try {
                Logger.d("VideoAdView: fetching VAST from $vastUrl")
                val response = parser.fetchAndParse(vastUrl, configuration.maxWrapperDepth, configuration.requestTimeoutSeconds)
                val ad = response.firstAd ?: throw VASTError.NoAdsFound
                val linear = ad.inLine?.creatives?.firstOrNull()?.linear ?: throw VASTError.InvalidMediaFile
                val mediaFile = linear.bestMediaFile() ?: throw VASTError.InvalidMediaFile

                currentAd = ad
                currentLinear = linear
                videoUrl = mediaFile.url

                mainHandler.post {
                    hideLoadingState()
                    // Fire impression tracking
                    ad.inLine?.let { inLine -> eventTracker.fireImpressions(inLine.impressions) }
                    callbacks?.onAdLoaded?.invoke(ad)
                    state = VideoAdState.Ready

                    // If the surface is already available, set up the player immediately
                    surfaceTexture?.let { surface ->
                        attachSurfaceToPlayer(surface, mediaFile.url)
                    }
                    // Otherwise surfaceTextureListener.onSurfaceTextureAvailable will call attachSurfaceToPlayer
                }
            } catch (e: VASTError) {
                Logger.e("VideoAdView: VAST error", e)
                mainHandler.post { handleError(e) }
            } catch (e: Exception) {
                Logger.e("VideoAdView: unexpected error", e)
                mainHandler.post { handleError(VASTError.Unknown(e.message ?: "Unknown error")) }
            }
        }

        // MARK: - Player Setup

        private fun attachSurfaceToPlayer(
            surface: SurfaceTexture,
            url: String,
        ) {
            releaseMediaPlayer()

            mediaPlayer =
                MediaPlayer().apply {
                    try {
                        setSurface(Surface(surface))
                        setAudioStreamType(AudioManager.STREAM_MUSIC)
                        setDataSource(context, Uri.parse(url))
                        setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                        isLooping = configuration.postAdBehavior == VideoAdPostAdBehavior.REPLAY

                        setOnPreparedListener { mp ->
                            Logger.d("VideoAdView: player prepared")
                            if (isVisible && configuration.autoPlay) {
                                performPlay()
                            } else if (!isVisible && state == VideoAdState.Loading) {
                                state = VideoAdState.Ready
                            }
                        }

                        setOnCompletionListener { handleCompletion() }

                        setOnErrorListener { _, what, extra ->
                            handleError(VASTError.Unknown("MediaPlayer error $what/$extra"))
                            true
                        }

                        prepareAsync()
                    } catch (e: Exception) {
                        handleError(VASTError.Unknown(e.message ?: "Player setup failed"))
                    }
                }
        }

        // MARK: - Playback Control

        private fun performPlay() {
            val mp = mediaPlayer ?: return
            if (!mp.isPlaying) {
                mp.start()
                if (!startEventFired) {
                    startEventFired = true
                    currentLinear?.trackingEvents?.let { eventTracker.fireStart(it) }
                    callbacks?.onAdStarted?.invoke()
                }
                state = VideoAdState.Playing
                scheduleTrackingUpdates()
            }
        }

        private fun handleCompletion() {
            trackingHandler.removeCallbacksAndMessages(null)
            currentLinear?.trackingEvents?.let { eventTracker.fireComplete(it) }
            callbacks?.onAdCompleted?.invoke()

            when (configuration.postAdBehavior) {
                VideoAdPostAdBehavior.REPLAY -> {
                    mediaPlayer?.seekTo(0)
                    firedQuartiles.clear()
                    firedProgress.clear()
                    startEventFired = false
                    state = VideoAdState.Completed
                    if (isVisible && configuration.autoPlay) performPlay()
                }
                VideoAdPostAdBehavior.REFRESH_AD -> {
                    refreshAd()
                }
                VideoAdPostAdBehavior.SHOW_LAST_FRAME -> {
                    state = VideoAdState.Completed
                }
                VideoAdPostAdBehavior.HIDE -> {
                    releaseMediaPlayer()
                    state = VideoAdState.Hidden
                    visibility = View.GONE
                }
            }
        }

        private fun refreshAd() {
            releaseMediaPlayer()
            currentAd = null
            currentLinear = null
            videoUrl = null
            firedQuartiles.clear()
            firedProgress.clear()
            startEventFired = false
            state = VideoAdState.Idle

            viewScope.launch { loadAd() }
        }

        // MARK: - Visibility

        private fun checkAndUpdateVisibility() {
            val rect = android.graphics.Rect()
            val globallyVisible = getGlobalVisibleRect(rect)
            val nowVisible =
                if (!globallyVisible || height == 0) {
                    false
                } else {
                    val visibleFraction = rect.height().toFloat() / height.toFloat()
                    visibleFraction >= configuration.visibilityThreshold
                }

            if (nowVisible == isVisible) return
            isVisible = nowVisible

            if (isVisible) {
                showMuteButtonBriefly()
                if (configuration.autoPlay && (state == VideoAdState.Ready || state == VideoAdState.Paused)) {
                    performPlay()
                }
            } else {
                if (state == VideoAdState.Playing) {
                    mediaPlayer?.pause()
                    state = VideoAdState.Paused
                    trackingHandler.removeCallbacksAndMessages(null)
                    currentLinear?.trackingEvents?.let { eventTracker.firePause(it) }
                }
            }
        }

        // MARK: - Tap Handling

        private fun handleTap() {
            showMuteButtonBriefly()

            val clickThrough = currentLinear?.videoClicks?.clickThrough ?: return
            currentLinear?.videoClicks?.let { eventTracker.fireClickTracking(it) }
            callbacks?.onAdClicked?.invoke(clickThrough)

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clickThrough))
                context.startActivity(intent)
            } catch (e: Exception) {
                Logger.e("VideoAdView: failed to open click-through URL", e)
            }
        }

        // MARK: - Mute Button

        private fun showMuteButtonBriefly() {
            val btn = muteButton ?: return
            if (configuration.muteButtonBehavior != VideoAdMuteButtonBehavior.AUTOMATIC) return

            btn.animate().alpha(1f).setDuration(200).start()
            muteButtonHideHandler.removeCallbacksAndMessages(null)
            muteButtonHideHandler.postDelayed({
                btn.animate().alpha(0f).setDuration(300).start()
            }, 3000)
        }

        private fun updateMuteButtonIcon(button: ImageButton? = muteButton) {
            button ?: return
            // Use a simple Unicode label since we can't bundle assets in the SDK.
            // Replace this with your own mute/unmute image assets if desired.
            button.contentDescription = if (isMuted) "Unmute" else "Mute"
            button.setBackgroundColor(Color.argb(153, 0, 0, 0))
        }

        // MARK: - Tracking Timer

        private fun scheduleTrackingUpdates() {
            trackingHandler.removeCallbacksAndMessages(null)
            val runnable =
                object : Runnable {
                    override fun run() {
                        val mp = mediaPlayer ?: return
                        if (state != VideoAdState.Playing) return
                        try {
                            val currentMs = mp.currentPosition.toLong()
                            val durationMs = mp.duration.toLong()
                            if (durationMs > 0) {
                                val currentSec = currentMs / 1000.0
                                val durationSec = durationMs / 1000.0
                                currentLinear?.trackingEvents?.let { events ->
                                    eventTracker.checkQuartileEvents(currentSec, durationSec, events, firedQuartiles)
                                    eventTracker.checkProgressEvents(currentSec, durationSec, events, firedProgress)
                                }
                            }
                        } catch (_: IllegalStateException) { /* MediaPlayer was released */ }
                        trackingHandler.postDelayed(this, 500)
                    }
                }
            trackingHandler.postDelayed(runnable, 500)
        }

        // MARK: - Error Handling

        private fun handleError(error: VASTError) {
            Logger.e("VideoAdView error: ${error.message}", error)

            currentAd?.inLine?.let { inLine ->
                eventTracker.fireErrors(inLine.errors, error.vastErrorCode)
            }

            callbacks?.onError?.invoke(error)

            state =
                when (error) {
                    is VASTError.NoAdsFound -> VideoAdState.NoAd
                    else -> VideoAdState.Error(error.message ?: "Unknown error")
                }
        }

        // MARK: - Loading State UI

        private fun showLoadingState() {
            when (configuration.loadingBehavior) {
                is VideoAdLoadingBehavior.Hidden -> textureView.visibility = View.INVISIBLE
                is VideoAdLoadingBehavior.Text -> {
                    textureView.visibility = View.INVISIBLE
                    loadingTextView?.visibility = View.VISIBLE
                }
                is VideoAdLoadingBehavior.ColorPlaceholder -> {
                    textureView.visibility = View.INVISIBLE
                    placeholderView?.visibility = View.VISIBLE
                }
            }
        }

        private fun hideLoadingState() {
            textureView.visibility = View.VISIBLE
            loadingTextView?.visibility = View.GONE
            placeholderView?.visibility = View.GONE
        }

        private fun updateVisibilityForState(s: VideoAdState) {
            when (s) {
                is VideoAdState.Hidden -> mainHandler.post { visibility = View.GONE }
                is VideoAdState.NoAd -> mainHandler.post { visibility = View.GONE }
                else -> { /* keep visible */ }
            }
        }

        // MARK: - Cleanup

        private fun releaseMediaPlayer() {
            trackingHandler.removeCallbacksAndMessages(null)
            mediaPlayer?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
            mediaPlayer = null
        }

        private fun applyCornerRadius() {
            if (configuration.cornerRadiusDp > 0) {
                clipToOutline = true
                val r = dp(configuration.cornerRadiusDp).toFloat()
                outlineProvider =
                    object : android.view.ViewOutlineProvider() {
                        override fun getOutline(
                            view: View,
                            outline: android.graphics.Outline,
                        ) {
                            outline.setRoundRect(0, 0, view.width, view.height, r)
                        }
                    }
            }
        }

        // MARK: - Helpers

        private fun dp(value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }
