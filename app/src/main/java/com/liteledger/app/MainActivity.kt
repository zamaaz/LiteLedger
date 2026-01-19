package com.liteledger.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.liteledger.app.data.AppDatabase
import com.liteledger.app.data.AppTheme
import com.liteledger.app.data.LedgerRepository
import com.liteledger.app.data.UserPreferencesRepository
import com.liteledger.app.ui.*
import com.liteledger.app.ui.theme.LiteLedgerTheme
import com.liteledger.app.utils.BiometricPromptManager
import kotlinx.serialization.Serializable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.FiniteAnimationSpec

@Serializable object DashboardRoute
@Serializable data class DetailRoute(val id: Long, val name: String)
@Serializable object SettingsRoute

class MainActivity : FragmentActivity() {

    private val promptManager by lazy { BiometricPromptManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = LedgerRepository(db.dao(), db.tagDao(), db.settlementDao())
        val userPrefs = UserPreferencesRepository(applicationContext)

        setContent {
            val settingsViewModel = viewModel<SettingsViewModel>(factory = SettingsViewModelFactory(application, userPrefs, repository))
            val settingsState by settingsViewModel.state.collectAsState()

            var isLocked by remember { mutableStateOf(true) }
            var hasPrompted by remember { mutableStateOf(false) }

            val isDarkTheme = when (settingsState.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            var launchAction by remember { mutableStateOf<String?>(null) }

            // Security Logic
            LaunchedEffect(settingsState.isLoading, settingsState.biometricEnabled) {
                if (!settingsState.isLoading) {
                    if (settingsState.biometricEnabled) {
                        if (!hasPrompted) {
                            promptManager.showBiometricPrompt("Unlock LiteLedger", "Verify your identity")
                            hasPrompted = true
                        }
                        isLocked = true
                    } else {
                        isLocked = false
                    }
                }
            }

            LaunchedEffect(true) {
                promptManager.promptResults.collect { result ->
                    when (result) {
                        is BiometricPromptManager.BiometricResult.AuthenticationSuccess -> isLocked = false
                        is BiometricPromptManager.BiometricResult.AuthenticationError -> { /* Handle cancel */ }
                        else -> Unit
                    }
                }
            }

            LaunchedEffect(Unit) {
                val action = intent?.getStringExtra("action_type")
                if (action != null) {
                    launchAction = action
                    intent?.removeExtra("action_type")
                }
            }

            LiteLedgerTheme(darkTheme = isDarkTheme) {
                val view = LocalView.current
                val colorScheme = MaterialTheme.colorScheme
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as FragmentActivity).window
                        window.navigationBarColor = colorScheme.surfaceContainerLowest.toArgb()
                        window.statusBarColor = Color.Transparent.toArgb()
                        window.setBackgroundDrawableResource(
                            if (isDarkTheme) android.R.color.black else android.R.color.white
                        )
                    }
                }

                if (settingsState.isLoading) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { }
                } else if (isLocked) {
                    LockedScreen(
                        onUnlockClick = {
                            promptManager.showBiometricPrompt("Unlock LiteLedger", "Verify your identity")
                        }
                    )
                } else {
                    val navController = rememberNavController()
                    val expressiveEasing = remember { CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) }
                    val slideSpec: FiniteAnimationSpec<IntOffset> = remember {
                        tween(durationMillis = 600, easing = expressiveEasing)
                    }
                    val scaleSpec = remember {
                        tween<Float>(durationMillis = 600, easing = expressiveEasing)
                    }
                    val fadeSpec = remember { tween<Float>(durationMillis = 400) }

                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        NavHost(
                            navController = navController,
                            startDestination = DashboardRoute,
                            enterTransition = {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec) + fadeIn(fadeSpec)
                            },
                            exitTransition = {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec) { it / 4 } +
                                        scaleOut(targetScale = 0.92f, animationSpec = scaleSpec) + fadeOut(fadeSpec)
                            },
                            popEnterTransition = {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec) { it / 4 } +
                                        scaleIn(initialScale = 0.92f, animationSpec = scaleSpec) + fadeIn(fadeSpec)
                            },
                            popExitTransition = {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec) + fadeOut(fadeSpec)
                            }
                        ) {
                            composable<DashboardRoute> {
                            val viewModel = viewModel<DashboardViewModel>(factory = DashboardViewModelFactory(repository, userPrefs))
                            val state by viewModel.state.collectAsState()
                            val searchQuery by viewModel.searchQuery.collectAsState()
                            val sortOption by viewModel.sortOption.collectAsState()

                            DashboardScreen(
                                state = state,
                                searchQuery = searchQuery,
                                hapticsEnabled = settingsState.hapticsEnabled,
                                showLastActivity = settingsState.showLastActivity,
                                currentSortOption = sortOption,
                                onSortOptionChange = { viewModel.setSortOption(it) },
                                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                                onAddPerson = { name, isTemporary -> viewModel.addPerson(name, isTemporary) },
                                onRenamePerson = { id, name -> viewModel.renamePerson(id, name) },
                                onPersonClick = { id ->
                                    val name = state.people.find { it.person.id == id }?.person?.name ?: ""
                                    navController.navigate(DetailRoute(id, name))
                                },
                                onDeletePerson = { id -> viewModel.deletePerson(id) },
                                onSettingsClick = { navController.navigate(SettingsRoute) },
                                onValidateName = { name -> viewModel.validatePersonName(name) },
                                initialAction = launchAction,
                                onActionConsumed = { launchAction = null },
                                isPrivacyMode = settingsState.isPrivacyEnabled,
                            )
                        }
                            composable<DetailRoute> { backStackEntry ->
                                val route: DetailRoute = backStackEntry.toRoute()
                                val viewModel = viewModel<DetailViewModel>(factory = DetailViewModelFactory(repository, route.id))
                                val state by viewModel.state.collectAsState()
                                val allTags by viewModel.allTags.collectAsState()
                                val recentTags by viewModel.recentTags.collectAsState()
                                DetailScreen(
                                    personName = route.name,
                                    state = state,
                                    allTags = allTags,
                                    recentTags = recentTags,
                                    hapticsEnabled = settingsState.hapticsEnabled,
                                    onBack = { navController.popBackStack() },
                                    onAddTransaction = { amount, type, note, date, dueDate, tagIds ->
                                        viewModel.addTransaction(amount, type, note, date, dueDate, tagIds)
                                    },
                                    onDeleteTransaction = { txn -> viewModel.deleteTransaction(txn) },
                                    onEditTransaction = { txn, tagIds -> viewModel.updateTransaction(txn, tagIds) },
                                    onCreateTag = { name -> viewModel.createTag(name) },
                                    // Settlement callbacks
                                    getEligibleSettlementTargets = { type -> viewModel.getEligibleSettlementTargets(type) },
                                    onAddTransactionWithSettlements = { amount, type, note, date, dueDate, tagIds, settlements ->
                                        viewModel.addTransactionWithSettlements(amount, type, note, date, dueDate, tagIds, settlements)
                                    },
                                    onUpdateSettlements = { txnId, settlements -> viewModel.updateSettlements(txnId, settlements) },
                                    // Smart statement callback
                                    getSmartStatementData = { viewModel.getSmartStatementData() }
                                )
                            }
                            composable<SettingsRoute> {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val archivedPeople by settingsViewModel.archivedPeople.collectAsState()

                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { navController.popBackStack() },
                                    isPrivacyMode = settingsState.isPrivacyEnabled,
                                    onPrivacyToggle = { settingsViewModel.setPrivacy(it) },
                                    onExportClick = { settingsViewModel.exportData(context) },
                                    archivedCount = archivedPeople.size,
                                    archivedPeople = archivedPeople,
                                    onUnarchive = { settingsViewModel.unarchivePerson(it) },
                                    onDeletePerson = { settingsViewModel.deletePerson(it) },
                                    onPersonClick = { id, name -> 
                                        navController.navigate(DetailRoute(id, name)) 
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}