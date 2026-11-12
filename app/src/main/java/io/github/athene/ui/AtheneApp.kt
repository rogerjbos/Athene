package io.github.athene.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.athene.athene
import io.github.athene.ui.home.HomeScreen
import io.github.athene.ui.lesson.LessonScreen
import io.github.athene.ui.onboarding.OnboardingScreen
import io.github.athene.ui.settings.SettingsScreen
import io.github.athene.ui.words.WordsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LESSON = "lesson/{lessonId}"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val WORDS = "words"

    fun lesson(id: String) = "lesson/$id"
}

@Composable
fun AtheneApp() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val start = remember { if (context.athene.settings.language == null) Routes.ONBOARDING else Routes.HOME }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onLesson = { nav.navigate(Routes.lesson(it)) },
                onReview = { nav.navigate(Routes.REVIEW) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onWords = { nav.navigate(Routes.WORDS) },
            )
        }
        composable(Routes.LESSON, arguments = listOf(navArgument("lessonId") { type = NavType.StringType })) {
            LessonScreen(onClose = { nav.popBackStack() })
        }
        composable(Routes.REVIEW) { LessonScreen(onClose = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.WORDS) { WordsScreen(onBack = { nav.popBackStack() }) }
    }
}
