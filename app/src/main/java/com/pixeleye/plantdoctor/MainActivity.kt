package com.pixeleye.plantdoctor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pixeleye.plantdoctor.data.UserPreferencesRepository
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.ui.screens.CameraScreen
import com.pixeleye.plantdoctor.ui.screens.HomeScreen
import com.pixeleye.plantdoctor.ui.screens.LoginScreen
import com.pixeleye.plantdoctor.ui.screens.OnboardingScreen
import com.pixeleye.plantdoctor.ui.screens.PaywallScreen
import com.pixeleye.plantdoctor.ui.screens.ResultScreen
import com.pixeleye.plantdoctor.ui.screens.SettingsScreen
import com.pixeleye.plantdoctor.ui.screens.SplashScreen
import com.pixeleye.plantdoctor.ui.theme.PlantDoctorTheme
import com.pixeleye.plantdoctor.viewmodel.AuthState
import com.pixeleye.plantdoctor.viewmodel.AuthViewModel
import com.pixeleye.plantdoctor.viewmodel.DiagnosisState
import com.pixeleye.plantdoctor.viewmodel.HomeViewModel
import com.pixeleye.plantdoctor.viewmodel.PlantDiagnosisViewModel
import com.pixeleye.plantdoctor.viewmodel.UploadState
import com.pixeleye.plantdoctor.viewmodel.SettingsViewModel
import com.pixeleye.plantdoctor.utils.LocationHelper
import com.pixeleye.plantdoctor.utils.rememberNetworkState

import com.pixeleye.plantdoctor.data.local.AppDatabase
import com.pixeleye.plantdoctor.data.api.PlantScanRepository
import com.pixeleye.plantdoctor.data.api.BillingManager
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import com.pixeleye.plantdoctor.data.api.UserQuotaRepository
import com.pixeleye.plantdoctor.viewmodel.PremiumViewModel
import com.pixeleye.plantdoctor.viewmodel.ReminderViewModel
import com.pixeleye.plantdoctor.ui.screens.RemindersScreen
import com.pixeleye.plantdoctor.utils.NavigationDebouncer
import com.pixeleye.plantdoctor.utils.LanguageHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.ads.MobileAds
import androidx.compose.material3.SnackbarResult
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.appupdate.AppUpdateOptions

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    private var isUpdateDownloaded by mutableStateOf(false)

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            isUpdateDownloaded = true
        }
    }

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.e("MainActivity", "In-app update flow failed or was cancelled by user. Result code: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this) {}

        // Initialize In-App Updates
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        checkForUpdates()

        // Initialize RevenueCat
        val billingManager = BillingManager()
        billingManager.initialize(this, BuildConfig.REVENUECAT_API_KEY)

        // Initialize Plant Care Notification Channel
        createNotificationChannel()

        val userPreferencesRepository = UserPreferencesRepository(applicationContext)

        // Read and apply saved app language locale on startup
        try {
            runBlocking {
                val prefs = userPreferencesRepository.userPreferences.first()
                LanguageHelper.setAppLocale(applicationContext, prefs.selectedAiLanguage)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to load locale preferences on startup", e)
        }

        setContent {
            PlantDoctorTheme {
                RequestNotificationPermission()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlantDoctorApp(
                        userPreferencesRepository = userPreferencesRepository,
                        billingManager = billingManager,
                        isUpdateDownloaded = isUpdateDownloaded,
                        onCompleteUpdate = {
                            if (::appUpdateManager.isInitialized) {
                                appUpdateManager.completeUpdate()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    isUpdateDownloaded = true
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
    }

    private fun checkForUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to start in-app update flow", e)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "plant_care_channel",
                "Plant Care Reminders",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications to remind you to water or fertilize your plants."
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun PlantDoctorApp(
    billingManager: BillingManager,
    userPreferencesRepository: UserPreferencesRepository,
    isUpdateDownloaded: Boolean,
    onCompleteUpdate: () -> Unit,
    authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application,
            billingManager
        )
    )
) {
    val isOnline by rememberNetworkState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(isUpdateDownloaded) {
        if (isUpdateDownloaded) {
            val message = context.getString(R.string.update_downloaded_message)
            val actionLabel = context.getString(R.string.update_restart_action)
            val result = snackbarHostState.showSnackbar(
                message = "SUCCESS|$message",
                actionLabel = actionLabel,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                onCompleteUpdate()
            }
        }
    }

    // ── Data Layer initialization (moved up for global VM access) ──
    val database = remember { AppDatabase.getDatabase(context) }
    val supabaseClient = remember {
        SupabaseClientProvider.getClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        )
    }
    val repository = remember { PlantScanRepository(supabaseClient, database.historyDao(), database.reminderDao(), context.applicationContext) }
    val userQuotaRepository = remember { UserQuotaRepository(supabaseClient) }

    // ── Shared ViewModels ──────────────────────────────────
    val premiumViewModel: PremiumViewModel = viewModel(
        factory = PremiumViewModel.Factory(billingManager)
    )
    val diagnosisViewModel: PlantDiagnosisViewModel = viewModel(
        factory = PlantDiagnosisViewModel.Factory(userPreferencesRepository, repository, userQuotaRepository)
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(repository, userPreferencesRepository)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(userPreferencesRepository)
    )
    val reminderViewModel: ReminderViewModel = viewModel(
        factory = ReminderViewModel.Factory(
            context.applicationContext as android.app.Application,
            database.reminderDao()
        )
    )

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            snackbarHostState.showSnackbar(
                message = "OFFLINE|No internet connection",
                duration = SnackbarDuration.Indefinite
            )
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // ── Global Premium State Management ────────────────────
    val isPremium by premiumViewModel.isPremium.collectAsStateWithLifecycle()

    // Listen for real-time premium status updates from RevenueCat
    LaunchedEffect(Unit) {
        billingManager.setUpdatedCustomerInfoListener { customerInfo ->
            val isPro = billingManager.isProActive(customerInfo)
            Log.d("MainActivity", "Real-time update: isPremium=$isPro")
            premiumViewModel.setPremium(isPro)
        }
    }

    LaunchedEffect(isPremium) {
        homeViewModel.setPremiumStatus(isPremium)
    }

    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val premiumSnackbar by premiumViewModel.snackbarEvent.collectAsStateWithLifecycle()
    val diagnosisSnackbar by diagnosisViewModel.snackbarEvent.collectAsStateWithLifecycle()
    val homeSnackbar by homeViewModel.snackbarEvent.collectAsStateWithLifecycle()
    val settingsSnackbar by settingsViewModel.snackbarEvent.collectAsStateWithLifecycle()

    // ── Global Snackbar Management ─────────────────────────
    LaunchedEffect(premiumSnackbar) {
        premiumSnackbar?.let {
            snackbarHostState.showSnackbar("${it.type.name}|${it.message}")
            premiumViewModel.consumeSnackbarEvent()
        }
    }

    LaunchedEffect(diagnosisSnackbar) {
        diagnosisSnackbar?.let {
            snackbarHostState.showSnackbar("${it.type.name}|${it.message}")
            diagnosisViewModel.consumeSnackbarEvent()
        }
    }

    LaunchedEffect(homeSnackbar) {
        homeSnackbar?.let {
            snackbarHostState.showSnackbar("${it.type.name}|${it.message}")
            homeViewModel.consumeSnackbarEvent()
        }
    }

    LaunchedEffect(settingsSnackbar) {
        settingsSnackbar?.let {
            snackbarHostState.showSnackbar("${it.type.name}|${it.message}")
            settingsViewModel.consumeSnackbarEvent()
        }
    }

    // ── Settings ViewModel logic (inside NavHost requires local viewmodel call or global collector) ──
    // Since SettingsViewModel is created inside NavHost, we'll collect its events here if we have a reference.
    // However, it's easier to collect the auth snackbars here too.
    val authSnackbar by authViewModel.snackbarEvent.collectAsStateWithLifecycle()
    LaunchedEffect(authSnackbar) {
        authSnackbar?.let {
            snackbarHostState.showSnackbar("${it.type.name}|${it.message}")
            authViewModel.consumeSnackbarEvent()
        }
    }

    // Prefetch weather forecast on startup/authentication if location is available
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            val coords = LocationHelper.getCoordinates(context)
            if (coords != null) {
                com.pixeleye.plantdoctor.data.api.WeatherRepository.fetchAndCacheForecast(coords.latitude, coords.longitude)
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val parts = data.visuals.message.split("|", limit = 2)
                val type = if (parts.size == 2) {
                    try {
                        com.pixeleye.plantdoctor.ui.components.SnackbarType.valueOf(parts[0])
                    } catch (e: Exception) {
                        com.pixeleye.plantdoctor.ui.components.SnackbarType.INFO
                    }
                } else {
                    com.pixeleye.plantdoctor.ui.components.SnackbarType.INFO
                }
                val rawMessage = if (parts.size == 2) parts[1] else data.visuals.message

                com.pixeleye.plantdoctor.ui.components.CustomSnackbar(
                    snackbarData = object : androidx.compose.material3.SnackbarData by data {
                        override val visuals: androidx.compose.material3.SnackbarVisuals = object : androidx.compose.material3.SnackbarVisuals by data.visuals {
                            override val message: String = rawMessage
                        }
                    },
                    type = type
                )
            }
        }
    ) { paddingValues ->
        when (authState) {
            is AuthState.Loading -> {
                LoadingSplash()
            }

            is AuthState.Authenticated,
            is AuthState.Unauthenticated,
            is AuthState.Error -> {
                PlantDoctorNavHost(
                    authViewModel = authViewModel,
                    homeViewModel = homeViewModel,
                    diagnosisViewModel = diagnosisViewModel,
                    authState = authState,
                    userPreferencesRepository = userPreferencesRepository,
                    billingManager = billingManager,
                    premiumViewModel = premiumViewModel,
                    settingsViewModel = settingsViewModel,
                    database = database,
                    isOnline = isOnline,
                    reminderViewModel = reminderViewModel,
                    onSignOut = { authViewModel.signOut() }
                )
            }
        }
    }
}

@Composable
private fun LoadingSplash() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    }
}

@Composable
fun PlantDoctorNavHost(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    diagnosisViewModel: PlantDiagnosisViewModel,
    authState: AuthState,
    userPreferencesRepository: UserPreferencesRepository,
    billingManager: BillingManager,
    premiumViewModel: PremiumViewModel,
    settingsViewModel: SettingsViewModel,
    database: com.pixeleye.plantdoctor.data.local.AppDatabase,
    isOnline: Boolean,
    reminderViewModel: ReminderViewModel,
    onSignOut: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val activity = context.findActivity()
                val intent = activity?.intent
                val navigateTo = intent?.getStringExtra("NAVIGATE_TO")
                val parentId = intent?.getStringExtra("PARENT_ID")
                if (navigateTo == "camera" && !parentId.isNullOrBlank()) {
                    intent.removeExtra("NAVIGATE_TO")
                    intent.removeExtra("PARENT_ID")
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("camera?parentId=$parentId")
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val reminders by reminderViewModel.reminders.collectAsStateWithLifecycle()
    val diagnosisState by diagnosisViewModel.diagnosisState.collectAsStateWithLifecycle()
    val isPremium by premiumViewModel.isPremium.collectAsStateWithLifecycle()
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(initialValue = com.pixeleye.plantdoctor.data.UserPreferences())
    val hasSeenCameraShowcase by homeViewModel.hasSeenCameraShowcase.collectAsStateWithLifecycle()
    val hasSeenLongPressShowcase by homeViewModel.hasSeenLongPressShowcase.collectAsStateWithLifecycle()

    // ── Setup OneSignal IAM Click Listener for Discount IAM ──
    LaunchedEffect(Unit) {
        com.onesignal.OneSignal.InAppMessages.addClickListener(object : com.onesignal.inAppMessages.IInAppMessageClickListener {
            override fun onClick(event: com.onesignal.inAppMessages.IInAppMessageClickEvent) {
                if (event.result.actionId == "open_pro_paywall") {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("paywall")
                    }
                }
            }
        })
    }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                authState = authState,
                userPreferencesRepository = userPreferencesRepository,
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("login") {
            var isSaving by remember { mutableStateOf(false) }

            LoginScreen(
                isLoading = isSaving,
                errorMessage = when (authState) {
                    is AuthState.Error -> (authState as AuthState.Error).message
                    else -> null
                },
                onGoogleSignIn = {
                    if (authState is AuthState.Error) {
                        authViewModel.clearError()
                    }
                    authViewModel.signInWithGoogle(onPostAuth = {
                        premiumViewModel.syncPremiumStatus()
                    })
                }
            )

            // After successful sign-in, navigate (state update is now delayed until sync is complete)
            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    navController.navigate("splash") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        }

        composable("onboarding") {
            var isSaving by remember { mutableStateOf(false) }

            OnboardingScreen(
                initialLanguage = prefs.selectedAiLanguage,
                onLanguageChanged = { newLanguage ->
                    scope.launch {
                        userPreferencesRepository.saveUserPreferences(
                            country = prefs.country,
                            language = prefs.language,
                            selectedAiLanguage = newLanguage,
                            onboardingCompleted = false
                        )
                        LanguageHelper.setAppLocale(context, newLanguage)
                    }
                },
                onSaveAndContinue = { country, language, aiLanguage ->
                    isSaving = true
                    scope.launch {
                        userPreferencesRepository.saveUserPreferences(
                            country = country,
                            language = language,
                            selectedAiLanguage = aiLanguage,
                            onboardingCompleted = true
                        )
                        LanguageHelper.setAppLocale(context, aiLanguage)
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                },
                isSaving = isSaving
            )
        }

        composable("home") {
            HomeScreen(
                uiState = homeUiState,
                selectedAiLanguage = prefs.selectedAiLanguage,
                isPremium = isPremium,
                onTriggerSnackbar = { msg, type ->
                    homeViewModel.showSnackbar(msg, type)
                },
                onScanPlantClick = {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("camera")
                    }
                },
                onViewResult = { scan: PlantScanDto ->
                    if (NavigationDebouncer.canNavigate()) {
                        val encodedUrl = Uri.encode(scan.imageUrl)
                        val encodedTitle = Uri.encode(scan.diseaseTitle)
                        val encodedPlan = Uri.encode(scan.treatmentPlan)
                        val scanId = scan.id
                        val parentId = scan.parentId
                        navController.navigate(
                            "result?imageUrl=$encodedUrl&title=$encodedTitle&plan=$encodedPlan&id=$scanId&parentId=$parentId"
                        )
                    }
                },
                onDeleteScan = { scan ->
                    homeViewModel.deleteScan(scan)
                },
                onDeleteSelectedScans = { ids ->
                    homeViewModel.deleteSelectedScans(ids)
                },
                onRetry = {
                    homeViewModel.fetchHistory()
                },
                onOpenSettings = {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("settings")
                    }
                },
                onOpenReminders = {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("reminders")
                    }
                },
                onOpenPaywall = {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("paywall")
                    }
                },
                onResume = {
                    homeViewModel.fetchHistory()
                },
                hasSeenCameraShowcase = hasSeenCameraShowcase,
                hasSeenLongPressShowcase = hasSeenLongPressShowcase,
                onCameraShowcaseDismissed = { homeViewModel.markCameraShowcaseSeen() },
                onLongPressShowcaseDismissed = { homeViewModel.markLongPressShowcaseSeen() }
            )
        }

        composable(
            route = "camera?parentId={parentId}",
            arguments = listOf(
                navArgument("parentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val parentId = backStackEntry.arguments?.getString("parentId")
            CameraScreen(
                diagnosisViewModel = diagnosisViewModel,
                isPremium = isPremium,
                onImageCaptured = { uri ->
                    if (NavigationDebouncer.canNavigate()) {
                        Log.d("PlantDoctor", "Image captured: $uri, parentId: $parentId")
                        diagnosisViewModel.resetState()
                        val encodedUri = Uri.encode(uri.toString())
                        val dest = if (parentId != null) {
                            "result?imageUri=$encodedUri&showAd=true&parentId=$parentId"
                        } else {
                            "result?imageUri=$encodedUri&showAd=true"
                        }
                        navController.navigate(dest) {
                            popUpTo("camera") { inclusive = true }
                        }
                    }
                },
                onError = { errorMessage ->
                    Log.e("PlantDoctor", "Camera error: $errorMessage")
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on camera error: ${e.message}")
                    }
                },
                onCancel = {
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on cancel: ${e.message}")
                    }
                },
                onOpenPaywall = {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("paywall")
                    }
                }
            )
        }

        // Fresh capture result (image from camera → Gemini analysis)
        composable(
            route = "result?imageUri={imageUri}&showAd={showAd}&parentId={parentId}",
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("showAd") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("parentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri")
            val showAd = backStackEntry.arguments?.getBoolean("showAd") ?: false
            val parentId = backStackEntry.arguments?.getString("parentId")
            val imageUri = imageUriString?.let {
                try { Uri.parse(it) } catch (e: Exception) {
                    Log.e("PlantDoctor", "Failed to parse URI: $it", e)
                    null
                }
            }

            LaunchedEffect(parentId) {
                if (parentId != null) {
                    homeViewModel.loadThreadScans(parentId)
                } else {
                    homeViewModel.clearThreadScans()
                }
            }

            var analysisTriggered by remember { mutableStateOf(false) }

            LaunchedEffect(imageUriString) {
                if (imageUri != null && diagnosisState is DiagnosisState.Idle && !analysisTriggered) {
                    analysisTriggered = true
                    try {
                        Log.d("PlantDoctor", "Starting image decode for URI: $imageUri")
                        val bitmap = withContext(Dispatchers.IO) {
                            val inputStream = context.contentResolver.openInputStream(imageUri)
                            val decoded = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            decoded
                        }
                        if (bitmap != null) {
                            Log.d("PlantDoctor", "Bitmap decoded: ${bitmap.width}x${bitmap.height}")
                            val locationStr = LocationHelper.getRobustLocationString(context)
                            diagnosisViewModel.analyzePlant(bitmap, imageUri = imageUri, locationStr = locationStr, context = context, isPremium = premiumViewModel.isPremium.value, parentId = parentId)
                        } else {
                            Log.e("PlantDoctor", "Failed to decode bitmap from URI: $imageUri")
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Error decoding image: ${e.message}", e)
                    }
                }
            }

            val uploadState by diagnosisViewModel.uploadState.collectAsStateWithLifecycle()

            val currentScanId = (uploadState as? UploadState.Success)?.scanId
            val currentParentId = (uploadState as? UploadState.Success)?.parentId 
                ?: (uploadState as? UploadState.Success)?.scanId 
                ?: parentId

            LaunchedEffect(uploadState) {
                val state = uploadState
                if (state is UploadState.Success) {
                    val targetParent = state.parentId ?: state.scanId
                    homeViewModel.loadThreadScans(targetParent)
                }
            }

            val threadScans by homeViewModel.threadScans.collectAsStateWithLifecycle()

            val isLoading = diagnosisState is DiagnosisState.Loading
            val diagnosisData = when (val state = diagnosisState) {
                is DiagnosisState.Success -> state.result
                is DiagnosisState.Error -> com.pixeleye.plantdoctor.data.api.DiagnosisResponse("Analysis failed: ${state.message}", emptyList())
                else -> null
            }

            LaunchedEffect(currentScanId, diagnosisData) {
                val scanId = currentScanId
                if (parentId.isNullOrBlank() && scanId != null && diagnosisData != null) {
                    val name = diagnosisData.plantName
                    if (name != null && name.isNotBlank()) {
                        homeViewModel.savePlantNameLocal(scanId, name)
                    }
                }
            }

            ResultScreen(
                imageUri = imageUri,
                diagnosisTitle = if (parentId != null) "Plant Progress Update" else "Plant Analysis",
                diagnosisData = diagnosisData,
                isLoading = isLoading,
                isSaving = uploadState is UploadState.Uploading,
                showAd = showAd,
                isPremium = isPremium,
                id = currentScanId,
                parentId = currentParentId,
                threadScans = threadScans,
                trackProgressEnabled = isOnline && (authState is AuthState.Authenticated),
                onAddReminders = { plantName, wateringEnabled, wateringHour, wateringMinute, fertilizingEnabled, fertilizingHour, fertilizingMinute ->
                    val targetParent = currentParentId ?: currentScanId ?: ""
                    reminderViewModel.addReminders(
                        scanId = targetParent,
                        plantName = plantName,
                        wateringEnabled = wateringEnabled,
                        wateringHour = wateringHour,
                        wateringMinute = wateringMinute,
                        fertilizingEnabled = fertilizingEnabled,
                        fertilizingHour = fertilizingHour,
                        fertilizingMinute = fertilizingMinute
                    )
                    if (targetParent.isNotBlank()) {
                        homeViewModel.savePlantNameLocal(targetParent, plantName)
                    }
                },
                existingReminders = reminders,
                onGoToReminders = {
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.navigate("reminders")
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation to reminders failed: ${e.message}")
                    }
                },
                onUpdateReminderTime = { reminder, hour, minute ->
                    reminderViewModel.updateReminderTime(reminder, hour, minute)
                },
                onBack = {
                    diagnosisViewModel.resetState()
                    homeViewModel.fetchHistory()
                    homeViewModel.clearThreadScans()
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on back: ${e.message}")
                    }
                },
                onNewScan = {
                    diagnosisViewModel.resetState()
                    homeViewModel.clearThreadScans()
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.navigate("camera") {
                                popUpTo("home")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on new scan: ${e.message}")
                    }
                },
                onOpenPaywall = {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("paywall")
                    }
                },
                onTrackProgress = {
                    val state = diagnosisViewModel.uploadState.value
                    val targetParent = (state as? UploadState.Success)?.parentId 
                        ?: (state as? UploadState.Success)?.scanId 
                        ?: parentId
                    Log.d("PlantDoctor", "Track Progress clicked (fresh capture): state=$state, targetParent=$targetParent, parentId=$parentId")
                    if (targetParent != null) {
                        try {
                            if (NavigationDebouncer.canNavigate()) {
                                navController.navigate("camera?parentId=$targetParent") {
                                    popUpTo("home")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PlantDoctor", "Navigation failed in track progress: ${e.message}", e)
                        }
                    } else {
                        Log.w("PlantDoctor", "Cannot track progress: targetParent is null")
                    }
                },
                onViewResult = { scan ->
                    if (NavigationDebouncer.canNavigate()) {
                        val encodedUrl = Uri.encode(scan.imageUrl)
                        val encodedTitle = Uri.encode(scan.diseaseTitle)
                        val encodedPlan = Uri.encode(scan.treatmentPlan)
                        val scanId = scan.id
                        val scanParentId = scan.parentId
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        navController.navigate(
                            "result?imageUrl=$encodedUrl&title=$encodedTitle&plan=$encodedPlan&id=$scanId&parentId=$scanParentId"
                        ) {
                            if (currentRoute != null) {
                                popUpTo(currentRoute) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        // History item result (pre-saved data from Supabase)
        composable(
            route = "result?imageUrl={imageUrl}&title={title}&plan={plan}&id={id}&parentId={parentId}",
            arguments = listOf(
                navArgument("imageUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("plan") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("parentId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val imageUrl = backStackEntry.arguments?.getString("imageUrl")
            val title = backStackEntry.arguments?.getString("title") ?: "Plant Analysis"
            val plan = backStackEntry.arguments?.getString("plan") ?: ""
            val id = backStackEntry.arguments?.getString("id")
            val parentId = backStackEntry.arguments?.getString("parentId")

            LaunchedEffect(id, parentId) {
                val targetParent = parentId ?: id
                if (targetParent != null) {
                    homeViewModel.loadThreadScans(targetParent)
                } else {
                    homeViewModel.clearThreadScans()
                }
            }

            val threadScans by homeViewModel.threadScans.collectAsStateWithLifecycle()

            // Reconstruct DiagnosisResponse from history plain text
            // Support new format (Organic/Chemical/Plant info sections) and legacy format (Action Plan section)
            val diagnosisData = if (plan.contains("Organic Treatments:") || plan.contains("Chemical Treatments:")) {
                val summaryEnd = listOfNotNull(
                    plan.indexOf("\n\nOrganic Treatments:\n").takeIf { it >= 0 },
                    plan.indexOf("\n\nChemical Treatments:\n").takeIf { it >= 0 },
                    plan.indexOf("\n\nPlant Name:").takeIf { it >= 0 }
                ).minOrNull() ?: plan.length
                val summary = plan.substring(0, summaryEnd).trim()

                val organicBlock = """Organic Treatments:\n(.*?)(?=\n*Chemical Treatments:|\n*Plant Name:|\n*Watering Time:|\n*Fertilizing Time:|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    .find(plan)?.groupValues?.getOrNull(1) ?: ""
                val organicList = organicBlock.lines().map { it.removePrefix("- ").trim() }.filter { it.isNotBlank() }

                val chemicalBlock = """Chemical Treatments:\n(.*?)(?=\n*Plant Name:|\n*Watering Time:|\n*Fertilizing Time:|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    .find(plan)?.groupValues?.getOrNull(1) ?: ""
                val chemicalList = chemicalBlock.lines().map { it.removePrefix("- ").trim() }.filter { it.isNotBlank() }

                val plantName = """Plant Name:\s*(.*)""".toRegex().find(plan)?.groupValues?.getOrNull(1)?.substringBefore("\n")?.trim()
                val wateringTime = """Watering Time:\s*(.*)""".toRegex().find(plan)?.groupValues?.getOrNull(1)?.substringBefore("\n")?.trim()
                val fertilizingTime = """Fertilizing Time:\s*(.*)""".toRegex().find(plan)?.groupValues?.getOrNull(1)?.trim()

                com.pixeleye.plantdoctor.data.api.DiagnosisResponse(
                    summary = summary,
                    organicTreatments = organicList,
                    chemicalTreatments = chemicalList,
                    plantName = plantName,
                    wateringTime = wateringTime,
                    fertilizingTime = fertilizingTime
                )
            } else {
                val legacyPlanParts = plan.split("\n\nAction Plan:\n")
                val legacySummary = legacyPlanParts.getOrNull(0) ?: plan
                val legacyList = legacyPlanParts.getOrNull(1)?.lines()?.map { it.removePrefix("- ").trim() }?.filter { it.isNotBlank() } ?: emptyList()
                com.pixeleye.plantdoctor.data.api.DiagnosisResponse(summary = legacySummary, organicTreatments = legacyList)
            }

            LaunchedEffect(id, diagnosisData) {
                if (parentId.isNullOrBlank() && id != null && diagnosisData != null) {
                    val name = diagnosisData.plantName
                    if (name != null && name.isNotBlank()) {
                        homeViewModel.savePlantNameLocal(id, name)
                    }
                }
            }

            val imageUri = imageUrl?.let {
                try { Uri.parse(it) } catch (_: Exception) { null }
            }

            ResultScreen(
                imageUri = imageUri,
                diagnosisTitle = title,
                diagnosisData = diagnosisData,
                isLoading = false,
                isSaving = false,
                isPremium = isPremium,
                id = id,
                parentId = parentId,
                threadScans = threadScans,
                trackProgressEnabled = isOnline && (authState is AuthState.Authenticated),
                onAddReminders = { plantName, wateringEnabled, wateringHour, wateringMinute, fertilizingEnabled, fertilizingHour, fertilizingMinute ->
                    val targetParent = parentId ?: id ?: ""
                    reminderViewModel.addReminders(
                        scanId = targetParent,
                        plantName = plantName,
                        wateringEnabled = wateringEnabled,
                        wateringHour = wateringHour,
                        wateringMinute = wateringMinute,
                        fertilizingEnabled = fertilizingEnabled,
                        fertilizingHour = fertilizingHour,
                        fertilizingMinute = fertilizingMinute
                    )
                    if (targetParent.isNotBlank()) {
                        homeViewModel.savePlantNameLocal(targetParent, plantName)
                    }
                },
                existingReminders = reminders,
                onGoToReminders = {
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.navigate("reminders")
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation to reminders failed: ${e.message}")
                    }
                },
                onUpdateReminderTime = { reminder, hour, minute ->
                    reminderViewModel.updateReminderTime(reminder, hour, minute)
                },
                onBack = {
                    homeViewModel.clearThreadScans()
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on back: ${e.message}")
                    }
                },
                onNewScan = {
                    homeViewModel.clearThreadScans()
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.navigate("camera") {
                                popUpTo("home")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on new scan: ${e.message}")
                    }
                },
                onOpenPaywall = {
                    if (NavigationDebouncer.canNavigate()) {
                        navController.navigate("paywall")
                    }
                },
                onTrackProgress = {
                    val targetParent = parentId ?: id
                    Log.d("PlantDoctor", "Track Progress clicked (history): targetParent=$targetParent, parentId=$parentId, id=$id")
                    if (targetParent != null) {
                        try {
                            if (NavigationDebouncer.canNavigate()) {
                                navController.navigate("camera?parentId=$targetParent") {
                                    popUpTo("home")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PlantDoctor", "Navigation failed in track progress: ${e.message}", e)
                        }
                    }
                },
                onViewResult = { scan ->
                    if (NavigationDebouncer.canNavigate()) {
                        val encodedUrl = Uri.encode(scan.imageUrl)
                        val encodedTitle = Uri.encode(scan.diseaseTitle)
                        val encodedPlan = Uri.encode(scan.treatmentPlan)
                        val scanId = scan.id
                        val scanParentId = scan.parentId
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        navController.navigate(
                            "result?imageUrl=$encodedUrl&title=$encodedTitle&plan=$encodedPlan&id=$scanId&parentId=$scanParentId"
                        ) {
                            if (currentRoute != null) {
                                popUpTo(currentRoute) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        composable("settings") {
            val currentPrefs by settingsViewModel.currentPrefs.collectAsStateWithLifecycle()
            val isSaving by settingsViewModel.isSaving.collectAsStateWithLifecycle()

            SettingsScreen(
                currentPrefs = currentPrefs,
                isSaving = isSaving,
                onSave = { country, language, aiLanguage, areFollowUpRemindersEnabled, customMessage ->
                    settingsViewModel.savePreferences(country, language, aiLanguage, areFollowUpRemindersEnabled, customMessage) {
                        LanguageHelper.setAppLocale(context, aiLanguage)
                    }
                },
                onTriggerSnackbar = { message, type ->
                    settingsViewModel.showSnackbar(message, type)
                },
                onLogout = {
                    premiumViewModel.setPremium(false)
                    scope.launch {
                        userPreferencesRepository.clearPreferences()
                        database.historyDao().clearAll()
                        authViewModel.signOut().await()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onBack = {
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on back: ${e.message}")
                    }
                }
            )
        }

        composable("paywall") {
            val isProcessing by premiumViewModel.isLoading.collectAsStateWithLifecycle()
            val monthlyPrice by premiumViewModel.monthlyPrice.collectAsStateWithLifecycle()
            val yearlyPrice by premiumViewModel.yearlyPrice.collectAsStateWithLifecycle()
            val monthlyPackage by premiumViewModel.monthlyPackage.collectAsStateWithLifecycle()
            val yearlyPackage by premiumViewModel.yearlyPackage.collectAsStateWithLifecycle()

            PaywallScreen(
                monthlyPrice = monthlyPrice,
                yearlyPrice = yearlyPrice,
                monthlyPackage = monthlyPackage,
                yearlyPackage = yearlyPackage,
                isProcessing = isProcessing,
                onTriggerSnackbar = { message, type ->
                    premiumViewModel.showSnackbar(message, type)
                },
                onClose = {
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on paywall close: ${e.message}")
                    }
                },
                onSubscribe = { plan ->
                    val activity = context.findActivity()
                    if (activity != null) {
                        premiumViewModel.startPurchase(
                            activity = activity,
                            billingManager = billingManager,
                            planId = plan,
                            onSuccess = {
                                premiumViewModel.upgradeToPremium()
                                premiumViewModel.showSnackbar("Welcome to PRO!", com.pixeleye.plantdoctor.ui.components.SnackbarType.SUCCESS)
                                try {
                                    if (NavigationDebouncer.canNavigate()) {
                                        navController.popBackStack()
                                    }
                                } catch (e: Exception) {
                                    Log.e("PlantDoctor", "Navigation error after purchase: ${e.message}")
                                }
                            },
                            onError = { message ->
                                Log.e("PlantDoctor", "Purchase error: $message")
                                premiumViewModel.showSnackbar(message, com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR)
                            }
                        )
                    } else {
                        Log.e("PlantDoctor", "Cannot start purchase: Activity is null")
                        premiumViewModel.showSnackbar("Unable to initiate purchase process.", com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR)
                    }
                },
                onRestorePurchases = {
                    try {
                        premiumViewModel.restorePurchases(
                            billingManager = billingManager,
                            onSuccess = { isPro ->
                                if (isPro) {
                                    premiumViewModel.upgradeToPremium()
                                    premiumViewModel.showSnackbar("Purchases restored! Welcome back to PRO!", com.pixeleye.plantdoctor.ui.components.SnackbarType.SUCCESS)
                                    try {
                                        if (NavigationDebouncer.canNavigate()) {
                                            navController.popBackStack()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PlantDoctor", "Navigation error after restore: ${e.message}")
                                    }
                                } else {
                                    premiumViewModel.showSnackbar("No active PRO subscription found.", com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR)
                                }
                            },
                            onError = { message ->
                                Log.e("PlantDoctor", "Restore error: $message")
                                premiumViewModel.showSnackbar(message, com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR)
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Restore exception: ${e.message}", e)
                        premiumViewModel.showSnackbar("Failed to restore purchases. Please try again.", com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR)
                    }
                },
                onTermsClick = {
                    uriHandler.openUri("https://ishara-madu.github.io/Plant-Doctor-AI-Disease-Identifier-Android/terms-and-conditions.html")
                },
                onPrivacyClick = {
                    uriHandler.openUri("https://ishara-madu.github.io/Plant-Doctor-AI-Disease-Identifier-Android/privacy-policy.html")
                }
            )
        }

        composable("reminders") {
            RemindersScreen(
                viewModel = reminderViewModel,
                onBack = {
                    try {
                        if (NavigationDebouncer.canNavigate()) {
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on reminders back: ${e.message}")
                    }
                }
            )
        }
    }
}

@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle result if needed
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!isPermissionGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

fun android.content.Context.findActivity(): ComponentActivity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    return null
}
