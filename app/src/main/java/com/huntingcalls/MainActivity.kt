package com.huntingcalls

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.android.play.core.review.ReviewManagerFactory
import com.huntingcalls.ui.theme.AllinOneCallsTheme
import com.huntingcalls.ui.theme.Green
import com.huntingcalls.ui.theme.Green2
import com.huntingcalls.ui.theme.Green3
import com.huntingcalls.ui.theme.Green4
import com.huntingcalls.ui.theme.YellowLight
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF7D9E49.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF607939.toInt())
        )
        setContent {
            AllinOneCallsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    val purchaseHelper = remember { PurchaseHelper(this@MainActivity) }
                    HuntingCallsApp(purchaseHelper)
                }
            }
        }
    }
}

data class SoundItem(val name: String, val duration: String, val file: String) {
    val key: String = file.removeSuffix(".aac")
    val rawName: String = key.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    val requiresPurchase: Boolean = !SoundAccessCatalog.isAlwaysFree(file)
}

data class Subcategory(val title: String, val image: String, val items: List<SoundItem>) {
    val imageResourceName: String = image.lowercase().replace(Regex("[^a-z0-9_]"), "_")
}

data class Animal(val name: String, val prefix: String, val subcategories: List<Subcategory>) {
    val totalSounds: Int = subcategories.sumOf { it.items.size }
}

private object SoundAccessCatalog {
    private val alwaysFreeSoundStemsByPrefix = mapOf(
        "AlligatorCroc" to setOf("alligatorbabycalling", "alligatorbreath1", "alligatorbreath2", "alligatorgrowl1", "alligatorgrowlhissing"),
        "Badger" to setOf("call", "chatter", "growl"), "Bear" to setOf("blackbearchewlick", "blackbearcubdistress1", "blackbearcubdistress2", "blackbearlick1", "blackbearlick2"),
        "Beaver" to setOf("call1", "call2", "call3"), "Bobcat" to setOf("distress", "growl1", "growl2"), "Cougar" to setOf("cubspurrwhine", "growl1", "growl2", "growl3", "growl4"),
        "Coyote" to setOf("alerts", "bark1", "bark2", "bark3", "barkyelp"), "Crane" to setOf("sandhillcrane1", "sandhillcrane2", "sandhillcrane3", "sandhillcrane4", "sandhillcrane5"),
        "Crow" to setOf("buster", "caw1", "caw2", "caw3", "caw4"), "Deer" to setOf("buckchallenge", "buckdistress", "buckgrunt1", "buckgrunt2", "buckgrunt3"),
        "Dove" to setOf("collareddovecalls", "collareddovesong1", "collareddovesong2", "collareddovesong3", "collareddovesong4"),
        "Duck" to setOf("mallardbabychirp1", "mallardbabychirp2", "mallardcomebackcall1", "mallardcomebackcall2", "mallardcompetition"),
        "Elk" to setOf("brayingshriek", "bugle", "buglegrunt", "chirp", "guttural"), "Fox" to setOf("redfoxbabysqueak", "redfoxcry1", "redfoxcry2", "redfoxcry3", "redfoxcry4"),
        "Goat" to setOf("bleat1", "bleat2", "bleat3", "bleat4", "bleat5"), "Goose" to setOf("canadagoosecall1", "canadagoosecall2", "canadagoosecall3", "canadagoosecluck", "canadagoosecomeback1"),
        "Groundhog" to setOf("alarm1", "alarm2", "scream1"), "Grouse" to setOf("blackgrouse", "duskygrouse1", "duskygrouse2", "duskygrouse3"),
        "HogPigBoar" to setOf("distress1", "distress2", "feeding1", "feeding2", "grunt"), "Lion" to setOf("attack", "fight1", "fight2", "fussplay", "gargle"),
        "Lynx" to setOf("growl1", "growl2", "growl3"), "Moose" to setOf("bullcallmoan1", "bullcallmoan2", "bullchallenge", "bullgrunt1", "bullgrunt2"),
        "NutriaOtter" to setOf("nutriacall1", "nutriacall2", "nutriacall3"), "Partridge" to setOf("call1", "call2", "call3"), "Pheasant" to setOf("cackle1", "cackle2", "call1", "call2", "call3"),
        "PrairieChicken" to setOf("greaterprairiechicken1", "greaterprairiechicken2", "greaterprairiechicken3"), "PrairieDog" to setOf("call1", "call2", "call3"),
        "Ptarmigan" to setOf("rockptarmigancall1", "rockptarmigancall2", "rockptarmigancall3", "rockptarmigancall4", "rockptarmigancall5"),
        "Quail" to setOf("bobwhitequailcall1", "bobwhitequailcall2", "bobwhitequailcall3", "bobwhitequailcall4", "bobwhitequailcall5"),
        "RabbitHare" to setOf("cottontaildistress1", "cottontaildistress2", "cottontaildistresscoyote", "cottontaildistresscrows", "cottontaildistressdeathcry"),
        "Raccoon" to setOf("chitter1", "chitter2", "chitter3", "chitter4", "cry"), "Sheep" to setOf("bleat1", "bleat2", "bleat3", "bleat4", "bleat5"),
        "Skunk" to setOf("baby", "growl"), "Squirrel" to setOf("angry1", "angry2", "angry3", "angry4", "chatter1"),
        "Turkey" to setOf("cackle1", "cackle2", "cackle3", "cackle4", "cackle5"), "Wolf" to setOf("bark", "growl1", "growl2", "growl3", "growlbark1"), "Woodcock" to setOf("calls", "display", "song1")
    )

    fun isAlwaysFree(file: String): Boolean {
        val parts = file.removeSuffix(".aac").split("_", limit = 2)
        return parts.size == 2 && alwaysFreeSoundStemsByPrefix[parts[0]]?.contains(parts[1]) == true
    }
}

private fun loadAnimals(context: Context): List<Animal> {
    val json = context.assets.open("list.json").bufferedReader().use { it.readText() }
    val array = JSONArray(json)
    return List(array.length()) { animalIndex ->
        val animalJson = array.getJSONObject(animalIndex)
        val subArray = animalJson.getJSONArray("subcategories")
        val subcategories = List(subArray.length()) { subIndex ->
            val subJson = subArray.getJSONObject(subIndex)
            val itemsArray = subJson.getJSONArray("items")
            Subcategory(
                title = subJson.getString("title"),
                image = subJson.getString("image"),
                items = List(itemsArray.length()) { itemIndex ->
                    val item = itemsArray.getJSONObject(itemIndex)
                    SoundItem(item.getString("name"), item.getString("duration"), item.getString("file"))
                }
            )
        }
        Animal(animalJson.getString("name"), animalJson.getString("prefix"), subcategories)
    }
}

private fun loadAnimalDescriptions(context: Context): Map<String, String> {
    val json = context.assets.open("animal_descriptions.json").bufferedReader().use { it.readText() }
    val obj = JSONObject(json)
    return obj.keys().asSequence().associateWith { obj.getString(it) }
}

private fun resourceId(context: Context, type: String, name: String): Int =
    context.resources.getIdentifier(name, type, context.packageName)

private data class AnimalGroup(val category: String, val animals: List<Animal>)

private fun groupedAnimals(animals: List<Animal>): List<AnimalGroup> {
    val groups = listOf(
        "Big Game" to setOf("Bear", "Deer", "Elk", "Moose", "Hog, Pig & Boar", "Goat", "Sheep", "Alligator & Croc"),
        "Predators" to setOf("Cougar", "Lion", "Coyote", "Wolf"),
        "Furbearers" to setOf("Beaver", "Bobcat", "Fox", "Lynx", "Badger", "Nutria & Otter"),
        "Small Game" to setOf("Rabbit & Hare", "Squirrel", "Groundhog", "Prairie Dog", "Raccoon", "Skunk", "Crow"),
        "Upland Game Birds" to setOf("Grouse", "Woodcock", "Pheasant", "Prairie Chicken", "Partridge", "Ptarmigan", "Quail", "Dove", "Turkey"),
        "Waterfowl" to setOf("Duck", "Goose", "Crane")
    )
    return groups.mapNotNull { (category, names) ->
        val items = animals.filter { it.name in names }.sortedBy { it.name }
        if (items.isEmpty()) null else AnimalGroup(category, items)
    }
}

private val selectedAmount = mutableIntStateOf(0)
private var mediaNoise: MediaPlayer? = null
private var noise = false

private data class ActiveCall(val title: String, val isWaiting: Boolean = false, val resume: () -> Unit = {})

class PlaybackCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mediaSession = MediaSession(appContext, "Hunting Calls")
    private val activeCalls = mutableMapOf<String, ActiveCall>()

    var activeCallIdentifiers by mutableStateOf(setOf<String>())
        private set
    var stopAllSignal by mutableIntStateOf(0)
        private set
    private var lastActiveID: String? = null

    init {
        createNotificationChannel()
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { mainHandler.post { lastActiveID?.let { activeCalls[it]?.resume?.invoke() }; updateNowPlayingInfo() } }
            override fun onPause() { mainHandler.post { stopAll() } }
            override fun onStop() { mainHandler.post { stopAll() } }
        })
    }

    fun registerStart(id: String, title: String, resume: () -> Unit = {}) {
        activeCalls[id] = ActiveCall(title, false, resume)
        activeCallIdentifiers = activeCalls.keys.toSet()
        lastActiveID = id
        updateNowPlayingInfo()
    }

    fun updateWaitingState(id: String, isWaiting: Boolean) {
        activeCalls[id]?.let { activeCalls[id] = it.copy(isWaiting = isWaiting) }
        updateNowPlayingInfo()
    }

    fun registerStop(id: String) {
        activeCalls.remove(id)
        activeCallIdentifiers = activeCalls.keys.toSet()
        if (lastActiveID == id) lastActiveID = activeCalls.keys.lastOrNull()
        updateNowPlayingInfo()
    }

    fun stopAll() {
        activeCalls.clear()
        activeCallIdentifiers = emptySet()
        stopAllSignal += 1
        updateNowPlayingInfo()
    }

    fun release() { mediaSession.release(); notificationManager.cancel(NOTIFICATION_ID) }

    private fun updateNowPlayingInfo() {
        if (activeCalls.isEmpty()) {
            mediaSession.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY).setState(PlaybackState.STATE_STOPPED, 0L, 0f).build())
            mediaSession.isActive = false
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        val waitingCount = activeCalls.values.count { it.isWaiting }
        val playingCount = activeCalls.size - waitingCount
        val title = if (activeCalls.size == 1) activeCalls.values.first().let { if (it.isWaiting) "${it.title} (waiting)" else it.title } else buildList {
            if (playingCount > 0) add("$playingCount playing")
            if (waitingCount > 0) add("$waitingCount waiting")
        }.joinToString(", ")
        val state = if (playingCount > 0) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, "Hunting Calls").build())
        mediaSession.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP).setState(state, 0L, if (playingCount > 0) 1f else 0f).build())
        mediaSession.isActive = true
        showMediaNotification(title, state)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(NOTIFICATION_CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Hunting calls playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }

    private fun showMediaNotification(title: String, playbackState: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: Intent(appContext, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(appContext, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(appContext, NOTIFICATION_CHANNEL_ID) else Notification.Builder(appContext)
        val notification = builder.setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText("Hunting Calls").setContentIntent(contentIntent).setCategory(Notification.CATEGORY_TRANSPORT).setVisibility(Notification.VISIBILITY_PUBLIC).setShowWhen(false).setOngoing(playbackState == PlaybackState.STATE_PLAYING).setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)).build()
        try { notificationManager.notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) {}
    }

    private companion object { const val NOTIFICATION_CHANNEL_ID = "playback"; const val NOTIFICATION_ID = 1001 }
}

val LocalPlaybackCoordinator = staticCompositionLocalOf<PlaybackCoordinator> { error("PlaybackCoordinator is not provided") }

@Composable
fun rememberPlaybackCoordinator(): PlaybackCoordinator {
    val context = LocalContext.current
    val coordinator = remember { PlaybackCoordinator(context.applicationContext) }
    DisposableEffect(coordinator) { onDispose { coordinator.release() } }
    return coordinator
}

class PreviewAccessState(context: Context) {
    private val prefs = context.getSharedPreferences("preview_access", Context.MODE_PRIVATE)
    private val prefsKey = "all_in_one_used_sound_previews"
    private var usedKeys by mutableStateOf(prefs.getStringSet(prefsKey, emptySet())?.toSet() ?: emptySet())
    private var activeKeys by mutableStateOf(setOf<String>())
    fun canPreview(key: String): Boolean = key !in usedKeys || key in activeKeys
    fun beginPreview(key: String) { if (key !in usedKeys) { usedKeys = usedKeys + key; prefs.edit().putStringSet(prefsKey, usedKeys).apply() }; activeKeys = activeKeys + key }
    fun finishPreview(key: String) { activeKeys = activeKeys - key }
}

class ReviewRequestState(context: Context) {
    private val prefs = context.getSharedPreferences("review_request", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    fun incrementSoundPlay(activity: Activity?) {
        if (activity == null) return
        val count = prefs.getInt("sound_play_count", 0) + 1
        prefs.edit().putInt("sound_play_count", count).apply()
        val attempts = prefs.getInt("review_request_count", 0)
        val last = prefs.getLong("last_review_request_at", 0L)
        val elapsed = System.currentTimeMillis() - last
        val shouldAsk = when (attempts) { 0 -> count >= 10; 1 -> count >= 50 && elapsed >= 14.daysInMillis(); 2 -> count >= 150 && elapsed >= 45.daysInMillis(); else -> false }
        if (shouldAsk) {
            prefs.edit().putInt("review_request_count", attempts + 1).putLong("last_review_request_at", System.currentTimeMillis()).apply()
            mainHandler.postDelayed({ requestReview(activity) }, 1000L)
        }
    }
    private fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request -> if (request.isSuccessful) manager.launchReviewFlow(activity, request.result) }
    }
}

private fun Int.daysInMillis(): Long = this * 24L * 60L * 60L * 1000L
private tailrec fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

@Composable
private fun ScreenFrame(
    isMainScreen: Boolean,
    title: String,
    imageName: String? = null,
    hasInfoButton: Boolean = false,
    purchaseHelper: PurchaseHelper? = null,
    onBack: () -> Unit = {},
    onInfo: () -> Unit = {},
    onUnlock: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        CustomNavigationBar(
            isMainScreen = isMainScreen,
            title = title,
            imageName = imageName,
            hasInfoButton = hasInfoButton,
            purchaseHelper = purchaseHelper,
            onBack = onBack,
            onInfo = onInfo,
            onUnlock = onUnlock
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 100.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun CustomNavigationBar(
    isMainScreen: Boolean,
    title: String,
    imageName: String?,
    hasInfoButton: Boolean,
    purchaseHelper: PurchaseHelper?,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val consumeEnabled by purchaseHelper?.consumeEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Green2)
            .statusBarsPadding()
            .height(38.dp)
    ) {
        if (isMainScreen) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = YellowLight,
                modifier = Modifier.padding(start = 16.dp)
            )
        } else {
            Box(Modifier.width(80.dp), contentAlignment = Alignment.CenterStart) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp).size(44.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = YellowLight, modifier = Modifier.size(28.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!isMainScreen) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.width(200.dp)
            ) {
                val imageId = imageName?.let { resourceId(context, "drawable", it) } ?: 0
                if (imageId != 0) {
                    Image(
                        painterResource(imageId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = YellowLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(if (isMainScreen) Modifier else Modifier.width(80.dp))
                .padding(end = 8.dp)
        ) {
            if (isMainScreen || hasInfoButton) {
                IconButton(onClick = onInfo, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = "Info", tint = YellowLight, modifier = Modifier.size(26.dp))
                }
            }
            if (isMainScreen && !consumeEnabled) {
                IconButton(onClick = onUnlock, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Unlock", tint = YellowLight, modifier = Modifier.size(26.dp))
                }
            } else if (!isMainScreen && !hasInfoButton) {
                Spacer(Modifier.size(44.dp))
            }
        }
    }
}

@Composable
fun HuntingCallsApp(purchaseHelper: PurchaseHelper) {
    val context = LocalContext.current
    val animals = remember { loadAnimals(context) }
    val descriptions = remember { loadAnimalDescriptions(context) }
    val previewAccessState = remember { PreviewAccessState(context.applicationContext) }
    val playbackCoordinator = rememberPlaybackCoordinator()
    var selectedAnimal by remember { mutableStateOf<Animal?>(null) }
    var showHowToUse by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }

    if (selectedAmount.intValue != 0) {
        if (!noise) { noise = true; mediaNoise = MediaPlayer.create(context, R.raw.noise); mediaNoise?.isLooping = true; mediaNoise?.start() }
    } else {
        noise = false; mediaNoise?.stop(); mediaNoise?.release(); mediaNoise = null
    }

    CompositionLocalProvider(LocalPlaybackCoordinator provides playbackCoordinator) {
        Box(Modifier.fillMaxSize()) {
            selectedAnimal?.let { animal ->
                AnimalDetailScreen(
                    animal = animal,
                    infoText = descriptions[animal.name],
                purchaseHelper = purchaseHelper,
                previewAccessState = previewAccessState,
                onBack = { selectedAnimal = null },
                onShowInfo = { showInfo = true },
                onShowPaywall = { showPaywall = true }
            )
            } ?: AnimalListScreen(
                animals = animals,
                purchaseHelper = purchaseHelper,
                onAnimalClick = { selectedAnimal = it },
                onShowHowToUse = { showHowToUse = true },
                onShowPaywall = { showPaywall = true }
            )
            Fab()
            if (showHowToUse) HowToUseOverlay(onDismiss = { showHowToUse = false })
            if (showInfo && selectedAnimal != null) InfoOverlay(title = selectedAnimal!!.name, text = descriptions[selectedAnimal!!.name].orEmpty(), onDismiss = { showInfo = false })
            if (showPaywall) PaywallOverlay(purchaseHelper = purchaseHelper, onDismiss = { showPaywall = false })
        }
    }
}

@Composable
private fun AnimalListScreen(animals: List<Animal>, purchaseHelper: PurchaseHelper, onAnimalClick: (Animal) -> Unit, onShowHowToUse: () -> Unit, onShowPaywall: () -> Unit) {
    ScreenFrame(
        isMainScreen = true,
        title = "Hunting Calls",
        purchaseHelper = purchaseHelper,
        onInfo = onShowHowToUse,
        onUnlock = onShowPaywall
    ) {
        groupedAnimals(animals).forEach { group ->
            Text(
                group.category,
                style = MaterialTheme.typography.titleLarge,
                color = YellowLight,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            group.animals.forEach { animal -> AnimalRow(animal, onClick = { onAnimalClick(animal) }) }
            if (group != groupedAnimals(animals).last()) {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AnimalDetailScreen(animal: Animal, infoText: String?, purchaseHelper: PurchaseHelper, previewAccessState: PreviewAccessState, onBack: () -> Unit, onShowInfo: () -> Unit, onShowPaywall: () -> Unit) {
    BackHandler(onBack = onBack)
    val consumeEnabled by purchaseHelper.consumeEnabled.collectAsState(false)
    val productName by purchaseHelper.productName.collectAsState("")
    val statusText by purchaseHelper.statusText.collectAsState("")
    val hasProductLoadingError = statusText == "No Matching Products Found" || statusText == "Billing Client Connection Failure" || statusText == "Billing Client Connection Lost" || statusText == "Product Not Loaded"
    val unlockButtonText = when { hasProductLoadingError -> "Product loading error"; productName == "Searching..." -> "Loading..."; else -> "Unlock All Calls - $productName" }

    ScreenFrame(
        isMainScreen = false,
        title = animal.name,
        imageName = animal.subcategories.firstOrNull()?.imageResourceName.orEmpty(),
        hasInfoButton = !infoText.isNullOrBlank(),
        onBack = onBack,
        onInfo = onShowInfo
    ) {
        animal.subcategories.forEachIndexed { subcategoryIndex, subcategory ->
            if (animal.subcategories.size > 1) HeaderTitle(subcategory.title, subcategory.imageResourceName)
            val freeItems = subcategory.items.filter { !it.requiresPurchase }
            val paidItems = subcategory.items.filter { it.requiresPurchase }
            if (consumeEnabled) {
                subcategory.items.forEach { SoundItemView(it) }
            } else {
                freeItems.forEach { SoundItemView(it) }
                if (shouldShowUnlockPrompt(subcategoryIndex, animal, freeItems, paidItems)) InlineUnlockPrompt(onShowPaywall)
                paidItems.forEach { sound -> PreviewSoundItemView(sound, previewAccessState, onShowPaywall) }
            }
        }
    }
}

private fun shouldShowUnlockPrompt(index: Int, animal: Animal, freeItems: List<SoundItem>, paidItems: List<SoundItem>): Boolean {
    if (paidItems.isEmpty()) return false
    val previous = animal.subcategories.take(index).flatMap { it.items }
    return (freeItems.isNotEmpty() || previous.any { !it.requiresPurchase }) && previous.none { it.requiresPurchase }
}

@Composable
private fun AnimalRow(animal: Animal, onClick: () -> Unit) {
    val context = LocalContext.current
    val playbackCoordinator = LocalPlaybackCoordinator.current
    val imageName = animal.subcategories.firstOrNull()?.imageResourceName.orEmpty()
    val imageId = resourceId(context, "drawable", imageName)
    val isAnimalSoundActive = playbackCoordinator.activeCallIdentifiers.any { it.startsWith("${animal.prefix}_") }
    Box(Modifier.padding(bottom = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Green)
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .padding(end = 10.dp)
        ) {
            Thumbnail(imageId)
            Column(Modifier.weight(1f).padding(start = 15.dp)) {
                Text(animal.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${animal.totalSounds} sounds", style = MaterialTheme.typography.bodyLarge, color = Green3)
            }
            if (isAnimalSoundActive) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Playing",
                    tint = YellowLight,
                    modifier = Modifier.size(45.dp).padding(7.dp)
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = YellowLight, modifier = Modifier.size(45.dp))
        }
    }
}

@Composable
private fun DetailHeader(animal: Animal, infoText: String?, onBack: () -> Unit, onShowInfo: () -> Unit) {
    val context = LocalContext.current
    val imageId = resourceId(context, "drawable", animal.subcategories.firstOrNull()?.imageResourceName.orEmpty())
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp).statusBarsPadding().fillMaxWidth()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = YellowLight, modifier = Modifier.size(30.dp)) }
        Thumbnail(imageId)
        Text(animal.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 14.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!infoText.isNullOrBlank()) IconButton(onClick = onShowInfo) { Icon(Icons.Rounded.Info, contentDescription = "Info", tint = YellowLight) }
    }
}

@Composable
private fun HeaderTitle(title: String, imageName: String?, appliesStatusBarPadding: Boolean = false) {
    val context = LocalContext.current
    val imageId = imageName?.let { resourceId(context, "drawable", it) } ?: 0
    val base = if (appliesStatusBarPadding) Modifier.statusBarsPadding() else Modifier
    Row(verticalAlignment = Alignment.CenterVertically, modifier = base) {
        if (imageId != 0) Thumbnail(imageId) else Box(Modifier.size(75.dp).clip(RoundedCornerShape(20.dp)).background(Green4), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Folder, contentDescription = null, tint = YellowLight, modifier = Modifier.size(38.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Thumbnail(imageId: Int) {
    Box(Modifier.size(75.dp).shadow(5.dp, RoundedCornerShape(20.dp), clip = true).clip(RoundedCornerShape(20.dp)).background(Green3.copy(alpha = 0.3f))) {
        if (imageId != 0) Image(painterResource(imageId), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun PreviewSoundItemView(sound: SoundItem, previewAccessState: PreviewAccessState, onLockedClick: () -> Unit) {
    if (previewAccessState.canPreview(sound.key)) {
        SoundItemView(sound, allowsLoop = false, onPlaybackStart = { previewAccessState.beginPreview(sound.key) }, onPlaybackEnd = { previewAccessState.finishPreview(sound.key) })
    } else {
        ItemLock(sound.name, sound.duration, onLockedClick)
    }
}

@Composable
private fun SoundItemView(sound: SoundItem, allowsLoop: Boolean = true, onPlaybackStart: () -> Unit = {}, onPlaybackEnd: () -> Unit = {}) {
    val context = LocalContext.current
    val rawId = resourceId(context, "raw", sound.rawName)
    if (rawId != 0) Item(sound.name, sound.duration, rawId, sound.key, allowsLoop, onPlaybackStart, onPlaybackEnd) else ItemLock(sound.name, sound.duration, null)
}

private fun formatTime(seconds: Int): String {
    val safeSeconds = max(0, seconds)
    return "%d:%02d".format(safeSeconds / 60, safeSeconds % 60)
}

@Composable
fun Item(name: String, duration: String, file: Int, itemKey: String, allowsLoop: Boolean = true, onPlaybackStart: () -> Unit = {}, onPlaybackEnd: () -> Unit = {}) {
    var sliderPosition by remember { mutableStateOf(0f) }
    var selected by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(false) }
    var reportedPlaybackStart by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val playbackCoordinator = LocalPlaybackCoordinator.current
    val reviewRequestState = remember { ReviewRequestState(context.applicationContext) }
    val activity = remember(context) { context.findActivity() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val stopAllSignal = playbackCoordinator.stopAllSignal
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    var waitRunnable: Runnable? by remember { mutableStateOf(null) }
    var waitEndsAtMillis by remember { mutableStateOf(0L) }
    var playbackRemaining by remember { mutableIntStateOf(0) }
    var waitRemaining by remember { mutableIntStateOf(0) }

    fun reportPlaybackStartIfNeeded() { if (!reportedPlaybackStart) { reportedPlaybackStart = true; onPlaybackStart() } }
    fun reportPlaybackEndIfNeeded() { if (reportedPlaybackStart) { reportedPlaybackStart = false; onPlaybackEnd() } }
    fun cancelWait() { waitRunnable?.let { mainHandler.removeCallbacks(it) }; waitRunnable = null }
    fun releasePlayback(decrementCounter: Boolean) {
        val wasSelected = selected
        cancelWait()
        if (mediaPlayer != null) { try { mediaPlayer?.stop() } catch (_: IllegalStateException) {}; mediaPlayer?.release(); mediaPlayer = null }
        if (decrementCounter && wasSelected && selectedAmount.intValue > 0) selectedAmount.intValue--
        selected = false; waiting = false; waitRemaining = 0; playbackRemaining = 0
        if (wasSelected) playbackCoordinator.registerStop(itemKey)
        reportPlaybackEndIfNeeded()
    }
    fun restartPlayback() { val currentPlayer = mediaPlayer ?: return; cancelWait(); currentPlayer.seekTo(0); currentPlayer.start(); waiting = false; playbackRemaining = ceil(currentPlayer.duration / 1000.0).toInt(); playbackCoordinator.updateWaitingState(itemKey, false) }
    fun scheduleNextLoop() {
        cancelWait(); val delaySeconds = max(0, Math.round(sliderPosition).toInt())
        if (delaySeconds == 0) { restartPlayback(); return }
        waiting = true; waitRemaining = delaySeconds; waitEndsAtMillis = System.currentTimeMillis() + delaySeconds * 1000L; playbackCoordinator.updateWaitingState(itemKey, true)
        val runnable = Runnable { if (selected && loop) restartPlayback() }
        waitRunnable = runnable; mainHandler.postDelayed(runnable, delaySeconds * 1000L)
    }
    fun startPlayback() {
        cancelWait(); val newPlayer = MediaPlayer.create(context, file) ?: return
        mediaPlayer?.release(); mediaPlayer = newPlayer
        newPlayer.setOnCompletionListener { if (allowsLoop && loop && selected) scheduleNextLoop() else releasePlayback(decrementCounter = true) }
        newPlayer.start(); selectedAmount.intValue++; waiting = false; selected = true; playbackRemaining = ceil(newPlayer.duration / 1000.0).toInt()
        playbackCoordinator.registerStart(itemKey, name) { if (!selected) startPlayback() else if (waiting) restartPlayback() }
        reviewRequestState.incrementSoundPlay(activity); reportPlaybackStartIfNeeded()
    }

    LaunchedEffect(stopAllSignal) { if (stopAllSignal != 0 && selected) releasePlayback(decrementCounter = true) }
    LaunchedEffect(selected, waiting, mediaPlayer) { while (selected) { if (waiting) waitRemaining = max(0, ceil((waitEndsAtMillis - System.currentTimeMillis()) / 1000.0).toInt()) else mediaPlayer?.let { playbackRemaining = max(0, ceil((it.duration - it.currentPosition) / 1000.0).toInt()) }; delay(1000) } }
    DisposableEffect(Unit) { onDispose { if (selected || mediaPlayer != null || reportedPlaybackStart) releasePlayback(decrementCounter = selected) } }

    Box(Modifier.padding(bottom = 20.dp)) {
        Box(Modifier.shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (selected) Green2 else Green).clickable { if (!selected) startPlayback() else releasePlayback(decrementCounter = true) }) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp).fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, overflow = TextOverflow.Ellipsis, maxLines = 2)
                        Text(if (selected && !waiting) formatTime(playbackRemaining) else duration, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (allowsLoop) IconButton(onClick = { loop = !loop; if (waiting) releasePlayback(decrementCounter = true) }) { Icon(Icons.Rounded.Repeat, contentDescription = "Repeat", modifier = Modifier.size(40.dp).alpha(if (loop) 1f else 0.5f), tint = YellowLight) }
                        Box(Modifier.width(45.dp), contentAlignment = Alignment.Center) {
                            if (!selected) Icon(Icons.Rounded.PlayArrow, contentDescription = "Start", modifier = Modifier.size(45.dp).alpha(0.5f), tint = YellowLight)
                            else if (!waiting) Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Playing", modifier = Modifier.size(40.dp), tint = YellowLight)
                            else Icon(Icons.Outlined.HourglassEmpty, contentDescription = "Waiting", modifier = Modifier.size(35.dp), tint = YellowLight)
                        }
                    }
                }
                if (allowsLoop && loop) {
                    Box(Modifier.height(46.dp)) {
                        LegacyLoopSlider(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            valueRange = 0f..1800f,
                            onValueChangeFinished = { if (waiting && selected) scheduleNextLoop() }
                        )
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("0:00", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(20.dp), tint = YellowLight); Spacer(Modifier.width(6.dp)); Text(if (waiting) formatTime(waitRemaining) else formatTime(Math.round(sliderPosition).toInt()), style = MaterialTheme.typography.bodyLarge, color = YellowLight) }
                            Text("30:00", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyLoopSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1800f
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbRadiusPx = with(density) { 10.dp.toPx() }
        val trackStrokePx = with(density) { 4.dp.toPx() }
        val trackStartPx = thumbRadiusPx
        val trackEndPx = max(trackStartPx + 1f, widthPx - thumbRadiusPx)
        val trackWidthPx = trackEndPx - trackStartPx
        val range = valueRange.endInclusive - valueRange.start
        val fraction = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
        val thumbCenterX = trackStartPx + trackWidthPx * fraction

        fun updateFromX(x: Float) {
            val newFraction = ((x - trackStartPx) / trackWidthPx).coerceIn(0f, 1f)
            onValueChange(valueRange.start + range * newFraction)
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(widthPx, valueRange.start, valueRange.endInclusive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        updateFromX(down.position.x)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                updateFromX(change.position.x)
                                change.consume()
                            }
                            if (event.changes.all { it.changedToUpIgnoreConsumed() }) break
                        }
                        onValueChangeFinished()
                    }
                }
        ) {
            val centerY = size.height / 2f
            drawLine(
                color = YellowLight.copy(alpha = 0.5f),
                start = Offset(trackStartPx, centerY),
                end = Offset(trackEndPx, centerY),
                strokeWidth = trackStrokePx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = YellowLight,
                start = Offset(trackStartPx, centerY),
                end = Offset(thumbCenterX, centerY),
                strokeWidth = trackStrokePx,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = YellowLight,
                radius = thumbRadiusPx,
                center = Offset(thumbCenterX, centerY)
            )
        }
    }
}

@Composable
fun ItemLock(name: String, duration: String, onClick: (() -> Unit)? = null) {
    Box(Modifier.padding(bottom = 20.dp)) {
        Box(Modifier.shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Green).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp)) {
                Column(Modifier.weight(1f).alpha(0.4f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(name, style = MaterialTheme.typography.titleLarge, overflow = TextOverflow.Ellipsis, maxLines = 2); Text(duration, style = MaterialTheme.typography.bodyLarge, color = Green3) }
                Icon(Icons.Rounded.Lock, contentDescription = "Locked", modifier = Modifier.size(30.dp).alpha(0.4f), tint = YellowLight)
            }
        }
    }
}

@Composable
fun InlineUnlockPrompt(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 30.dp), horizontalArrangement = Arrangement.Center) {
        Box(Modifier.shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Green4).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp), tint = YellowLight)
                    Text("Unlock All Calls", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                }
                Text("80+ Species • 800+ Sounds", style = MaterialTheme.typography.bodyLarge, color = Green3)
            }
        }
    }
}

@Composable
fun Fab() {
    val playbackCoordinator = LocalPlaybackCoordinator.current
    if (playbackCoordinator.activeCallIdentifiers.isNotEmpty()) {
        Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.BottomEnd) { FloatingActionButton(onClick = { playbackCoordinator.stopAll() }, modifier = Modifier.size(64.dp), shape = CircleShape, containerColor = Green2) { Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(40.dp)) } }
    }
}

@Composable
fun HowToUseOverlay(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().zIndex(10f)) {
        Image(painterResource(R.drawable.blur), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Green.copy(alpha = 0.7f)))
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp).padding(top = 30.dp, bottom = 44.dp), verticalArrangement = Arrangement.spacedBy(25.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = YellowLight, modifier = Modifier.size(34.dp)); Text("How to Use", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = YellowLight, modifier = Modifier.padding(start = 10.dp)) }
            HowToUseSection("Getting Started", listOf(HowToUseItem(Icons.Rounded.Folder, "Browse by Category", "Animals are organized into hunting categories (Big Game, Predators, Waterfowl, etc.)"), HowToUseItem(Icons.Rounded.PlayCircle, "Tap to Play", "Simply tap any call to start playing the sound"), HowToUseItem(Icons.AutoMirrored.Rounded.VolumeUp, "Multiple Sounds", "Play several calls simultaneously to create realistic hunting scenarios")))
            HowToUseDivider()
            HowToUseSection("Advanced Features", listOf(HowToUseItem(Icons.Rounded.Repeat, "Loop Mode", "Hold and drag the slider to set custom intervals between calls"), HowToUseItem(Icons.Rounded.LockOpen, "Background Play", "Sounds continue playing even when your phone is locked"), HowToUseItem(Icons.AutoMirrored.Rounded.VolumeUp, "Volume Control", "Use your device volume"), HowToUseItem(Icons.Rounded.CheckCircle, "Works in Silent Mode", "Calls play even when your phone is on silent/mute")))
            HowToUseDivider()
            HowToUseSection("Pro Tips", listOf(HowToUseItem(Icons.AutoMirrored.Rounded.VolumeUp, "Layer Different Calls", "Combine multiple species sounds for more realistic hunting"), HowToUseItem(Icons.AutoMirrored.Rounded.VolumeUp, "Connect External Speakers", "Use Bluetooth or AirPlay speakers for better range and volume"), HowToUseItem(Icons.Rounded.CheckCircle, "Check Local Regulations", "Ensure electronic calls are legal in your hunting area"), HowToUseItem(Icons.Rounded.Timer, "Practice Before Hunt", "Familiarize yourself with different call types")))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 10.dp, end = 10.dp)) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = YellowLight, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
fun PaywallOverlay(purchaseHelper: PurchaseHelper, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val productName by purchaseHelper.productName.collectAsState("")
    val productPrices by purchaseHelper.productPrices.collectAsState(emptyMap())
    val buyEnabled by purchaseHelper.buyEnabled.collectAsState(false)
    val statusText by purchaseHelper.statusText.collectAsState("")
    val plans = purchaseHelper.plans
    var selectedPlanId by remember { mutableStateOf("com.huntingcalls.yearly") }
    LaunchedEffect(Unit) { purchaseHelper.billingSetup() }
    val hasProductLoadingError = statusText == "No Matching Products Found" ||
        statusText == "Billing Client Connection Failure" ||
        statusText == "Billing Client Connection Lost" ||
        statusText == "Product Not Loaded"
    val isProductLoading = !hasProductLoadingError && productName == "Searching..."
    val canContinue = buyEnabled && !hasProductLoadingError && !isProductLoading
    val continueText = when {
        hasProductLoadingError -> "Product loading error"
        isProductLoading -> "Loading..."
        else -> "Continue"
    }
    val fallbackPrice = productName.takeUnless { it == "Searching..." }.orEmpty()
    Box(Modifier.fillMaxSize().zIndex(12f)) {
        Image(painterResource(R.drawable.blur), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 50.dp, bottom = 30.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 15.dp)) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = YellowLight, modifier = Modifier.size(44.dp))
                Text("Get Full Access", style = MaterialTheme.typography.titleLarge.copy(fontSize = 34.sp), color = Color.White)
                Text("All 800+ hunting sounds unlocked", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                Text("80+ Species • No Ads • Offline Use", style = MaterialTheme.typography.bodyLarge, color = YellowLight)
            }

            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
                plans.forEach { plan ->
                    val price = when {
                        hasProductLoadingError -> "--"
                        else -> productPrices[plan.id] ?: fallbackPrice.ifBlank { "--" }
                    }
                    PlanButton(plan.title, price, plan.period, selectedPlanId == plan.id, badge = plan.badge) {
                        selectedPlanId = plan.id
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(YellowLight.copy(alpha = if (canContinue) 1f else 0.65f))
                    .clickable(enabled = canContinue) { purchaseHelper.makePurchase(selectedPlanId) }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(continueText, style = MaterialTheme.typography.titleLarge.copy(color = Color.Black))
            }

            Text(
                "Restore Purchases • Terms of Use • Privacy Policy",
                style = MaterialTheme.typography.labelMedium,
                color = YellowLight.copy(alpha = 0.7f)
            )
        }

        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 10.dp, end = 10.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = YellowLight, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PlanButton(title: String, price: String, period: String, isSelected: Boolean, badge: String? = null, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (isSelected) 14.dp else 0.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) Green2.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f))
                .border(if (isSelected) 2.5.dp else 1.5.dp, if (isSelected) YellowLight else Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(price, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(" · $period", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Color.White.copy(alpha = 0.78f))
                }
            }
            Icon(if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = if (isSelected) YellowLight else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(30.dp))
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium.copy(color = Color.Black),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp)
                    .offset(y = (-10).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(YellowLight)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private data class HowToUseItem(val icon: ImageVector, val title: String, val detail: String)

@Composable
private fun HowToUseSection(title: String, items: List<HowToUseItem>) { Column(verticalArrangement = Arrangement.spacedBy(15.dp), modifier = Modifier.fillMaxWidth()) { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = YellowLight); items.forEach { HowToUseBullet(it) } } }

@Composable
private fun HowToUseBullet(item: HowToUseItem) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { Icon(item.icon, contentDescription = null, tint = YellowLight, modifier = Modifier.width(24.dp).padding(top = 1.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = YellowLight); Text(item.detail, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, color = YellowLight.copy(alpha = 0.8f))) } } }

@Composable
private fun HowToUseDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(YellowLight.copy(alpha = 0.45f))) }

@Composable
private fun InfoOverlay(title: String, text: String, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().zIndex(11f)) {
        Image(painterResource(R.drawable.blur), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Green.copy(alpha = 0.7f)))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(30.dp)
                .padding(top = 30.dp)
        ) {
            Text(
                text.ifBlank { "No description available." },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = YellowLight
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 10.dp, end = 10.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = YellowLight, modifier = Modifier.size(28.dp))
        }
    }
}
