package com.fintrackai.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.ui.theme.LocalExtendedColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fintrackai.notification.NotificationManagerHelper
import com.fintrackai.ui.auth.AuthScreen
import com.fintrackai.ui.auth.AuthViewModel
import com.fintrackai.ui.auth.PostLoginImportScreen
import com.fintrackai.ui.onboarding.OnboardingScreen
import com.fintrackai.ui.undetected.SenderMessagesScreen
import com.fintrackai.ui.undetected.UndetectedSmsScreen
import com.fintrackai.ui.undetected.UndetectedSmsViewModel
import com.fintrackai.ui.budget.BudgetScreen
import com.fintrackai.ui.accounts.AccountsScreen
import com.fintrackai.ui.accounts.AccountsViewModel
import com.fintrackai.ui.accounts.AllAccountsScreen
import com.fintrackai.ui.home.AllRemindersScreen
import com.fintrackai.ui.home.HomeScreen
import com.fintrackai.ui.home.HomeViewModel
import com.fintrackai.ui.home.SetReminderScreen
import com.fintrackai.ui.insights.InsightsScreen
import com.fintrackai.ui.settings.SettingsScreen
import com.fintrackai.ui.settings.StartupSmsViewModel
import com.fintrackai.ui.transactions.AddTransactionScreen
import com.fintrackai.ui.transactions.CategoryDetailScreen
import com.fintrackai.ui.transactions.MerchantDetailScreen
import com.fintrackai.ui.transactions.TransactionDetailScreen
import com.fintrackai.ui.transactions.TransactionsScreen
import com.fintrackai.ui.weeklysummary.WeeklySummaryScreen
import com.fintrackai.ui.wrapped.WrappedScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Auth : Screen("auth")
    data object PostLoginImport : Screen("post_login_import")
    data object Home : Screen("home")
    data object Budget : Screen("budget")
    data object Insights : Screen("insights")
    data object Accounts : Screen("accounts")
    data object Settings : Screen("settings")
    data object Transactions :
        Screen("transactions?accountKey={accountKey}&accountTitle={accountTitle}&typeFilter={typeFilter}&dateStart={dateStart}&dateEnd={dateEnd}&linkAnchorId={linkAnchorId}&isCard={isCard}&categoryFilter={categoryFilter}&fromViewAll={fromViewAll}") {
        fun createRoute(
            accountKey: String = "",
            accountTitle: String = "",
            typeFilter: String = "",
            dateStart: String = "",
            dateEnd: String = "",
            linkAnchorId: String = "",
            isCard: Boolean = false,
            categoryFilter: String = "",
            fromViewAll: Boolean = false
        ): String {
            val ak = Uri.encode(accountKey)
            val at = Uri.encode(accountTitle)
            val tf = Uri.encode(typeFilter)
            val ds = Uri.encode(dateStart)
            val de = Uri.encode(dateEnd)
            val la = Uri.encode(linkAnchorId)
            val cf = Uri.encode(categoryFilter)
            return "transactions?accountKey=$ak&accountTitle=$at&typeFilter=$tf&dateStart=$ds&dateEnd=$de&linkAnchorId=$la&isCard=$isCard&categoryFilter=$cf&fromViewAll=$fromViewAll"
        }
    }
    data object AddTransaction : Screen("add_transaction")
    data object MerchantDetail : Screen("merchant/{name}?monthKey={monthKey}&filterType={filterType}") {
        fun createRoute(name: String, monthKey: String = "", filterType: String = "") =
            "merchant/${android.net.Uri.encode(name)}?monthKey=${android.net.Uri.encode(monthKey)}&filterType=${android.net.Uri.encode(filterType)}"
    }
    data object CategoryDetail : Screen("category/{id}?monthKey={monthKey}&filterType={filterType}") {
        fun createRoute(id: String, monthKey: String = "", filterType: String = "") =
            "category/${android.net.Uri.encode(id)}?monthKey=${android.net.Uri.encode(monthKey)}&filterType=${android.net.Uri.encode(filterType)}"
    }
    data object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) =
            "transaction_detail/${android.net.Uri.encode(transactionId)}"
    }
    data object ExpenseWrapped : Screen("expense_wrapped")
    data object SetReminder : Screen("set_reminder?merchant={merchant}&amount={amount}&type={type}&reminderId={reminderId}&defaultDate={defaultDate}") {
        fun createRoute(merchant: String, amount: Double, type: String, reminderId: String = "", defaultDate: String = ""): String {
            val m = Uri.encode(merchant)
            val t = Uri.encode(type)
            val r = Uri.encode(reminderId)
            val d = Uri.encode(defaultDate)
            return "set_reminder?merchant=$m&amount=$amount&type=$t&reminderId=$r&defaultDate=$d"
        }
    }
    data object AllReminders : Screen("all_reminders?source={source}") {
        fun createRoute(source: String = "home") = "all_reminders?source=$source"
    }
    data object AllAccounts : Screen("all_accounts/{section}") {
        fun createRoute(section: String = "accounts") = "all_accounts/$section"
    }
    data object WeeklySummary : Screen("weekly_summary?weekStart={weekStart}") {
        fun createRoute(weekStart: String = "") =
            "weekly_summary?weekStart=${android.net.Uri.encode(weekStart)}"
    }
    data object UndetectedSms : Screen("undetected_sms")
    data object SenderMessages : Screen("sender_messages/{senderId}") {
        fun createRoute(senderId: String) =
            "sender_messages/${android.net.Uri.encode(senderId)}"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Budget, "Budget", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    BottomNavItem(Screen.Insights, "Insights", Icons.Filled.Insights, Icons.Outlined.Insights),
    BottomNavItem(Screen.Accounts, "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun AppNavHost(notifDestination: String? = null) {
    val activity = LocalContext.current as ComponentActivity
    val authViewModel: AuthViewModel = hiltViewModel(activity)
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navStateViewModel: AppNavStateViewModel = hiltViewModel()
    val bootstrap by navStateViewModel.bootstrap.collectAsState()
    val session = bootstrap
    if (session == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Compute startDestination only once from the first non-null session snapshot.
    // Later prefs changes (e.g. setWrappedLastShownMonth) must not re-navigate.
    val startDestination = remember(Unit) {
        when {
            !session.isLoggedIn && !session.onboardingCompleted -> Screen.Onboarding.route
            !session.isLoggedIn -> Screen.Auth.route
            !session.loginSmsImportCompleted -> Screen.PostLoginImport.route
            session.shouldShowWrapped -> Screen.ExpenseWrapped.route
            else -> Screen.Home.route
        }
    }

    // Run SMS rescan when fully logged in (Home or Wrapped start — not Auth/PostLoginImport/Onboarding)
    if (startDestination == Screen.Home.route || startDestination == Screen.ExpenseWrapped.route) {
        val startupSmsViewModel: StartupSmsViewModel = hiltViewModel()
        LaunchedEffect(Unit) {
            startupSmsViewModel.runLaunchRescanIfNeeded(context)
        }
    }


    // Deep-link from notification tap — only when the user is already logged in
    if (notifDestination != null && session.isLoggedIn && session.loginSmsImportCompleted) {
        LaunchedEffect(notifDestination) {
            navStateViewModel.logNotificationTapped(notifDestination)
            when {
                notifDestination.startsWith(NotificationManagerHelper.DEST_TRANSACTIONS_DATE_PREFIX) -> {
                    val date = notifDestination.removePrefix(NotificationManagerHelper.DEST_TRANSACTIONS_DATE_PREFIX)
                    navController.navigate(
                        Screen.Transactions.createRoute(dateStart = date, dateEnd = date)
                    )
                }
                notifDestination.startsWith(NotificationManagerHelper.DEST_TRANSACTIONS_RANGE_PREFIX) -> {
                    val parts = notifDestination
                        .removePrefix(NotificationManagerHelper.DEST_TRANSACTIONS_RANGE_PREFIX)
                        .split("|")
                    if (parts.size == 2) {
                        navController.navigate(
                            Screen.Transactions.createRoute(dateStart = parts[0], dateEnd = parts[1])
                        )
                    }
                }
                notifDestination.startsWith(NotificationManagerHelper.DEST_WEEKLY_SUMMARY_PREFIX) -> {
                    val parts = notifDestination
                        .removePrefix(NotificationManagerHelper.DEST_WEEKLY_SUMMARY_PREFIX)
                        .split("|")
                    val weekStart = if (parts.isNotEmpty()) parts[0] else ""
                    navController.navigate(Screen.WeeklySummary.createRoute(weekStart))
                }
                notifDestination == NotificationManagerHelper.DEST_BUDGET -> {
                    navController.navigate(Screen.Budget.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                notifDestination == NotificationManagerHelper.DEST_REMINDERS -> {
                    navController.navigate(Screen.AllReminders.createRoute("home")) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // Track which bottom-nav tabs the user has visited, then show feedback prompt once all are seen
    val bottomNavRoutes = remember { bottomNavItems.map { it.screen.route }.toSet() }
    var visitedTabs by remember { mutableStateOf(setOf<String>()) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route ?: return@LaunchedEffect
        if (route in bottomNavRoutes) {
            visitedTabs = visitedTabs + route
            if (!session.feedbackPromptShown && visitedTabs.size == bottomNavRoutes.size) {
                showFeedbackDialog = true
            }
        }
    }

    if (showFeedbackDialog) {
        FeedbackPromptDialog(
            onDismiss = {
                showFeedbackDialog = false
                navStateViewModel.markFeedbackPromptShown()
            },
            onRate = {
                showFeedbackDialog = false
                navStateViewModel.markFeedbackPromptShown()
                val openPlayStore = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${context.packageName}"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY or android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    )
                }
                val manager = com.google.android.play.core.review.ReviewManagerFactory.create(context)
                manager.requestReviewFlow().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        manager.launchReviewFlow(activity, task.result)
                            .addOnCompleteListener { openPlayStore() }
                    } else {
                        openPlayStore()
                    }
                }
            },
            onFeedback = {
                showFeedbackDialog = false
                navStateViewModel.markFeedbackPromptShown()
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:")
                        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("support@expensly.co.in"))
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "FinTrackAI Feedback")
                    }
                )
            }
        )
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navStateViewModel.completeOnboarding()
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Auth.route) {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.PostLoginImport.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PostLoginImport.route) {
            PostLoginImportScreen(
                onContinueToHome = {
                    // Re-read the latest bootstrap snapshot; by the time the user completes
                    // import, DataStore has emitted importDone=true and shouldShowWrapped is accurate.
                    val latestBootstrap = navStateViewModel.bootstrap.value
                    val next = if (latestBootstrap?.shouldShowWrapped == true) {
                        Screen.ExpenseWrapped.route
                    } else {
                        Screen.Home.route
                    }
                    navController.navigate(next) {
                        popUpTo(Screen.PostLoginImport.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ExpenseWrapped.route) {
            WrappedScreen(
                onClose = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.ExpenseWrapped.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) { backStackEntry ->
            val homeViewModel: HomeViewModel = hiltViewModel()
            DisposableEffect(backStackEntry.id) {
                homeViewModel.refreshDailySummary()
                onDispose { }
            }
            MainScaffold(navController = navController, currentRoute = Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToTransactions = { accountKey, accountTitle, typeFilter, dateStart, dateEnd, linkAnchorId, isCard, fromViewAll ->
                        navController.navigate(
                            Screen.Transactions.createRoute(accountKey, accountTitle, typeFilter, dateStart, dateEnd, linkAnchorId, isCard, fromViewAll = fromViewAll)
                        )
                    },
                    onNavigateToCategory = { id, monthKey ->
                        navController.navigate(Screen.CategoryDetail.createRoute(id, monthKey))
                    },
                    onNavigateToMerchant = { name, monthKey ->
                        navController.navigate(Screen.MerchantDetail.createRoute(name, monthKey))
                    },
                    onNavigateToAddTransaction = { navController.navigate(Screen.AddTransaction.route) },
                    onNavigateToWeeklySummary = { navController.navigate(Screen.WeeklySummary.createRoute()) },
                    showTutorial = session.smsPermissionGranted && !session.homeTutorialSeen,
                    onTutorialDone = { navStateViewModel.markHomeTutorialSeen() },
                    showCategoryTip = session.smsPermissionGranted && !session.categoryTipSeen,
                    onCategoryTipDismissed = { navStateViewModel.markCategoryTipSeen() },
                    showTxDetailTip = session.smsPermissionGranted && !session.txDetailTipSeen,
                    onTxDetailTipDismissed = { navStateViewModel.markTxDetailTipSeen() }
                )
            }
        }

        composable(Screen.Budget.route) { backStackEntry ->
            val budgetViewModel: com.fintrackai.ui.budget.BudgetViewModel = hiltViewModel()
            DisposableEffect(backStackEntry.id) {
                budgetViewModel.loadData()
                onDispose { }
            }
            MainScaffold(navController = navController, currentRoute = Screen.Budget.route) {
                BudgetScreen(viewModel = budgetViewModel)
            }
        }

        composable(Screen.Insights.route) { backStackEntry ->
            val insightsViewModel: com.fintrackai.ui.insights.InsightsViewModel = hiltViewModel()
            DisposableEffect(backStackEntry.id) {
                insightsViewModel.refreshAll()
                onDispose { }
            }
            MainScaffold(navController = navController, currentRoute = Screen.Insights.route) {
                InsightsScreen(viewModel = insightsViewModel)
            }
        }

        composable(Screen.Accounts.route) { backStackEntry ->
            val accountsViewModel: com.fintrackai.ui.accounts.AccountsViewModel = hiltViewModel()
            MainScaffold(navController = navController, currentRoute = Screen.Accounts.route) {
                AccountsScreen(
                    viewModel = accountsViewModel,
                    onNavigateToTransactions = { accountKey, accountTitle, typeFilter, dateStart, dateEnd, linkAnchorId, isCard ->
                        navController.navigate(
                            Screen.Transactions.createRoute(accountKey, accountTitle, typeFilter, dateStart, dateEnd, linkAnchorId, isCard)
                        )
                    },
                    onNavigateToAllAccounts = { section ->
                        navController.navigate(Screen.AllAccounts.createRoute(section))
                    },
                    onNavigateToSetReminder = { merchant, amount, type, reminderId, defaultDate ->
                        navController.navigate(Screen.SetReminder.createRoute(merchant, amount, type, reminderId, defaultDate))
                    },
                    onNavigateToAllReminders = {
                        navController.navigate(Screen.AllReminders.createRoute("accounts"))
                    },
                    onNavigateToMerchant = { name, monthKey ->
                        navController.navigate(Screen.MerchantDetail.createRoute(name, monthKey))
                    },
                    showTutorial = session.smsPermissionGranted && !session.walletTutorialSeen,
                    onTutorialDone = { navStateViewModel.markWalletTutorialSeen() }
                )
            }
        }

        composable(
            route = Screen.AllAccounts.route,
            arguments = listOf(
                navArgument("section") { type = NavType.StringType; defaultValue = "accounts" }
            )
        ) { backStackEntry ->
            val section = backStackEntry.arguments?.getString("section").orEmpty()
            val accountsViewModel: com.fintrackai.ui.accounts.AccountsViewModel = hiltViewModel()
            AllAccountsScreen(
                initialSection = section,
                viewModel = accountsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToTransactions = { accountKey, accountTitle, typeFilter, dateStart, dateEnd, linkAnchorId, isCard ->
                    navController.navigate(
                        Screen.Transactions.createRoute(accountKey, accountTitle, typeFilter, dateStart, dateEnd, linkAnchorId, isCard)
                    )
                }
            )
        }

        composable(Screen.Settings.route) {
            MainScaffold(navController = navController, currentRoute = Screen.Settings.route) {
                SettingsScreen(
                    onLogout = {
                        scope.launch {
                            authViewModel.logout()
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    onNavigateToUndetectedSms = {
                        navController.navigate(Screen.UndetectedSms.route)
                    },
                    onNavigateToWeeklySummary = {
                        navController.navigate(Screen.WeeklySummary.createRoute())
                    },
                    onNavigateToWrapped = {
                        navController.navigate(Screen.ExpenseWrapped.route)
                    }
                )
            }
        }

        composable(Screen.UndetectedSms.route) {
            val undetectedVm: UndetectedSmsViewModel = hiltViewModel()
            UndetectedSmsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSender = { senderId ->
                    navController.navigate(Screen.SenderMessages.createRoute(senderId))
                },
                viewModel = undetectedVm
            )
        }

        composable(
            route = Screen.SenderMessages.route,
            arguments = listOf(
                navArgument("senderId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val senderId = backStackEntry.arguments?.getString("senderId").orEmpty()
            val undetectedBackStackEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.UndetectedSms.route) }
            val undetectedVm: UndetectedSmsViewModel = hiltViewModel(undetectedBackStackEntry)
            SenderMessagesScreen(
                senderId = senderId,
                onBack = { navController.popBackStack() },
                viewModel = undetectedVm
            )
        }

        composable(
            route = Screen.Transactions.route,
            arguments = listOf(
                navArgument("accountKey") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("accountTitle") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("typeFilter") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("dateStart") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("dateEnd") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("linkAnchorId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isCard") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("categoryFilter") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("fromViewAll") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val fromViewAll = backStackEntry.arguments?.getBoolean("fromViewAll") ?: false
            TransactionsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMerchant = { name, monthKey, filterType ->
                    navController.navigate(Screen.MerchantDetail.createRoute(name, monthKey, filterType))
                },
                onNavigateToCategory = { id, monthKey, filterType ->
                    navController.navigate(Screen.CategoryDetail.createRoute(id, monthKey, filterType))
                },
                showTutorial = session.smsPermissionGranted && fromViewAll && !session.txTutorialSeen,
                onTutorialDone = { navStateViewModel.markTxTutorialSeen() },
                showCategoryTip = session.smsPermissionGranted && !session.categoryTipSeen,
                onCategoryTipDismissed = { navStateViewModel.markCategoryTipSeen() },
                showTxDetailTip = session.smsPermissionGranted && !session.txDetailTipSeen,
                onTxDetailTipDismissed = { navStateViewModel.markTxDetailTipSeen() }
            )
        }

        composable(Screen.AddTransaction.route) {
            AddTransactionScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.SetReminder.route,
            arguments = listOf(
                navArgument("merchant") { type = NavType.StringType; defaultValue = "" },
                navArgument("amount") { type = NavType.StringType; defaultValue = "0" },
                navArgument("type") { type = NavType.StringType; defaultValue = "debit" },
                navArgument("reminderId") { type = NavType.StringType; defaultValue = "" },
                navArgument("defaultDate") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val merchant = backStackEntry.arguments?.getString("merchant").orEmpty()
            val amount = backStackEntry.arguments?.getString("amount")?.toDoubleOrNull() ?: 0.0
            val type = backStackEntry.arguments?.getString("type").orEmpty()
            val reminderId = backStackEntry.arguments?.getString("reminderId").orEmpty()
            val defaultDate = backStackEntry.arguments?.getString("defaultDate").orEmpty()
            SetReminderScreen(
                merchant = merchant,
                amount = amount,
                transactionType = type,
                reminderId = reminderId,
                defaultDate = defaultDate,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AllReminders.route,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType; defaultValue = "home" }
            )
        ) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source") ?: "home"
            if (source == "accounts") {
                val accountsViewModel: AccountsViewModel = hiltViewModel()
                val accountsState by accountsViewModel.uiState.collectAsState()
                AllRemindersScreen(
                    reminders = accountsState.reminders,
                    recurringTransactions = accountsState.recurringTransactions,
                    onDeleteReminder = { id -> accountsViewModel.deleteReminder(id) },
                    onMarkReminderPaid = { id -> accountsViewModel.markReminderPaid(id) },
                    onDismissRecurring = { merchant, amount -> accountsViewModel.dismissRecurring(merchant, amount) },
                    onBack = { navController.popBackStack() },
                    onNavigateToSetReminder = { merchant, amount, type, reminderId, defaultDate ->
                        navController.navigate(Screen.SetReminder.createRoute(merchant, amount, type, reminderId, defaultDate))
                    },
                    onNavigateToAddReminder = {
                        navController.navigate(Screen.SetReminder.createRoute("", 0.0, "debit"))
                    },
                    onNavigateToMerchant = { name, monthKey ->
                        navController.navigate(Screen.MerchantDetail.createRoute(name, monthKey))
                    }
                )
            } else {
                val homeBackStackEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.Home.route) }
                val homeViewModel: HomeViewModel = hiltViewModel(homeBackStackEntry)
                val homeState by homeViewModel.uiState.collectAsState()
                AllRemindersScreen(
                    reminders = homeState.reminders,
                    recurringTransactions = homeState.recurringTransactions,
                    onDeleteReminder = { id -> homeViewModel.deleteReminder(id) },
                    onMarkReminderPaid = { id -> homeViewModel.markReminderPaid(id) },
                    onDismissRecurring = { merchant, amount -> homeViewModel.dismissRecurring(merchant, amount) },
                    onBack = { navController.popBackStack() },
                    onNavigateToSetReminder = { merchant, amount, type, reminderId, defaultDate ->
                        navController.navigate(Screen.SetReminder.createRoute(merchant, amount, type, reminderId, defaultDate))
                    },
                    onNavigateToAddReminder = {
                        navController.navigate(Screen.SetReminder.createRoute("", 0.0, "debit"))
                    },
                    onNavigateToMerchant = { name, monthKey ->
                        navController.navigate(Screen.MerchantDetail.createRoute(name, monthKey))
                    }
                )
            }
        }

        composable(
            route = Screen.WeeklySummary.route,
            arguments = listOf(
                navArgument("weekStart") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val weekStart = backStackEntry.arguments?.getString("weekStart").orEmpty().ifEmpty { null }
            WeeklySummaryScreen(
                weekStart = weekStart,
                onBack = { navController.popBackStack() },
                onNavigateToTransactions = { ds, de ->
                    navController.navigate(Screen.Transactions.createRoute(dateStart = ds, dateEnd = de))
                },
                onNavigateToTransactionsFiltered = { ds, de, category ->
                    navController.navigate(
                        Screen.Transactions.createRoute(dateStart = ds, dateEnd = de, categoryFilter = category, typeFilter = "debit")
                    )
                },
                onNavigateToMerchant = { name, monthKey ->
                    navController.navigate(Screen.MerchantDetail.createRoute(name, monthKey))
                },
                showCategoryTip = session.smsPermissionGranted && !session.categoryTipSeen,
                onCategoryTipDismissed = { navStateViewModel.markCategoryTipSeen() },
                showTxDetailTip = session.smsPermissionGranted && !session.txDetailTipSeen,
                onTxDetailTipDismissed = { navStateViewModel.markTxDetailTipSeen() }
            )
        }

        composable(
            route = Screen.MerchantDetail.route,
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("monthKey") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("filterType") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            val monthKey = backStackEntry.arguments?.getString("monthKey").orEmpty()
            val filterType = backStackEntry.arguments?.getString("filterType").orEmpty()
            MerchantDetailScreen(
                merchantName = name,
                monthKey = monthKey,
                filterType = filterType,
                onBack = { navController.popBackStack() },
                onOpenTransactionsToCombine = { anchorId ->
                    navController.navigate(Screen.Transactions.createRoute(linkAnchorId = anchorId))
                },
                showTxDetailTip = session.smsPermissionGranted && !session.txDetailTipSeen,
                onTxDetailTipDismissed = { navStateViewModel.markTxDetailTipSeen() }
            )
        }

        composable(
            route = Screen.CategoryDetail.route,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("monthKey") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("filterType") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            val monthKey = backStackEntry.arguments?.getString("monthKey").orEmpty()
            val filterType = backStackEntry.arguments?.getString("filterType").orEmpty()
            CategoryDetailScreen(
                categoryId = id,
                monthKey = monthKey,
                filterType = filterType,
                onBack = { navController.popBackStack() },
                onOpenTransactionsToCombine = { anchorId ->
                    navController.navigate(Screen.Transactions.createRoute(linkAnchorId = anchorId))
                },
                showTxDetailTip = session.smsPermissionGranted && !session.txDetailTipSeen,
                onTxDetailTipDismissed = { navStateViewModel.markTxDetailTipSeen() }
            )
        }

        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(
                navArgument("transactionId") { type = NavType.StringType }
            )
        ) {
            TransactionDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun MainScaffold(
    navController: androidx.navigation.NavController,
    currentRoute: String,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val ext = LocalExtendedColors.current

    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(color = ext.border, thickness = 0.5.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}

@Composable
private fun FeedbackPromptDialog(
    onDismiss: () -> Unit,
    onRate: () -> Unit,
    onFeedback: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enjoying Expensly?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "We'd love to hear what you think!",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onRate, modifier = Modifier.fillMaxWidth()) {
                    Text("⭐  Rate on Play Store")
                }
                OutlinedButton(onClick = onFeedback, modifier = Modifier.fillMaxWidth()) {
                    Text("Send Feedback")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Maybe later (Never)")
                }
            }
        },
        confirmButton = {},
        dismissButton = null
    )
}
