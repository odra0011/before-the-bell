package com.example.beforethebell

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.beforethebell.data.StorageManager
import com.example.beforethebell.network.OpenRouterService
import com.example.beforethebell.ui.screens.*

sealed interface AppScreen {
    data object Home : AppScreen
    data object NewGame : AppScreen
    data class Gameplay(val campaignId: String) : AppScreen
    data class Journal(val campaignId: String) : AppScreen
    data class Facts(val campaignId: String) : AppScreen
    data class Prompts(val campaignId: String? = null) : AppScreen
    data object Sessions : AppScreen
    data object Settings : AppScreen
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val storage = remember { StorageManager(context) }
    val openRouterService = remember { OpenRouterService() }

    var screenStack by remember { mutableStateOf(listOf<AppScreen>(AppScreen.Home)) }
    val currentScreen = screenStack.lastOrNull() ?: AppScreen.Home

    fun navigateTo(screen: AppScreen) {
        screenStack = screenStack + screen
    }

    fun popBack() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        } else {
            screenStack = listOf(AppScreen.Home)
        }
    }

    when (val screen = currentScreen) {
        AppScreen.Home -> {
            val latestCampaign = storage.getLatestCampaign()
            HomeScreen(
                latestCampaign = latestCampaign,
                onContinueGame = {
                    latestCampaign?.let { navigateTo(AppScreen.Gameplay(it.campaignId)) }
                },
                onNewGame = {
                    navigateTo(AppScreen.NewGame)
                },
                onOpenSessions = {
                    navigateTo(AppScreen.Sessions)
                },
                onOpenPrompts = {
                    navigateTo(AppScreen.Prompts(latestCampaign?.campaignId))
                },
                onOpenSettings = {
                    navigateTo(AppScreen.Settings)
                }
            )
        }

        AppScreen.NewGame -> {
            NewGameScreen(
                storage = storage,
                onBack = { popBack() },
                onStartCampaign = { campaign ->
                    // Replace stack with Home + Gameplay
                    screenStack = listOf(AppScreen.Home, AppScreen.Gameplay(campaign.campaignId))
                }
            )
        }

        is AppScreen.Gameplay -> {
            GameplayScreen(
                campaignId = screen.campaignId,
                storage = storage,
                openRouterService = openRouterService,
                onBackToHome = {
                    screenStack = listOf(AppScreen.Home)
                },
                onOpenJournal = {
                    navigateTo(AppScreen.Journal(screen.campaignId))
                },
                onOpenFacts = {
                    navigateTo(AppScreen.Facts(screen.campaignId))
                }
            )
        }

        is AppScreen.Journal -> {
            val camp = storage.getCampaign(screen.campaignId)
            if (camp != null) {
                JournalPanel(
                    campaign = camp,
                    onBackToStory = { popBack() },
                    onToggleShowPrivate = { showPrivate ->
                        val updatedCamp = camp.copy(
                            journal = camp.journal.copy(showPrivateImpressions = showPrivate)
                        )
                        storage.saveCampaign(updatedCamp)
                        // Trigger recomposition
                        screenStack = screenStack.toList()
                    }
                )
            } else {
                popBack()
            }
        }

        is AppScreen.Facts -> {
            val camp = storage.getCampaign(screen.campaignId)
            if (camp != null) {
                FactsPanel(
                    campaign = camp,
                    onBackToStory = { popBack() }
                )
            } else {
                popBack()
            }
        }

        is AppScreen.Prompts -> {
            PromptsScreen(
                storage = storage,
                currentCampaignId = screen.campaignId,
                onBack = { popBack() }
            )
        }

        AppScreen.Sessions -> {
            SessionsScreen(
                storage = storage,
                onResumeCampaign = { campaignId ->
                    screenStack = listOf(AppScreen.Home, AppScreen.Gameplay(campaignId))
                },
                onBack = { popBack() }
            )
        }

        AppScreen.Settings -> {
            SettingsScreen(
                storage = storage,
                openRouterService = openRouterService,
                onBack = { popBack() }
            )
        }
    }
}
