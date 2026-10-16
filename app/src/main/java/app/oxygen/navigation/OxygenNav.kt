package app.oxygen.navigation
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.oxygen.features.chat.ChatScreen
import app.oxygen.features.conversations.ConversationsScreen
import app.oxygen.features.presets.PresetsScreen
import app.oxygen.features.settings.SettingsScreen
import app.oxygen.features.usage.UsageScreen
object Routes {
    const val Chats = "chats"
    const val Chat = "chat/{conversationId}"
    const val Settings = "settings"
    const val Usage = "usage"
    const val Presets = "presets"
    fun chat(conversationId: String): String = "chat/$conversationId"
}
@Composable
fun OxygenNav(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.Chats) {
        composable(Routes.Chats) {
            ConversationsScreen(
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenUsage = { navController.navigate(Routes.Usage) },
            )
        }
        composable(
            route = Routes.Chat,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { entry ->
            val conversationId = entry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenUsage = { navController.navigate(Routes.Usage) },
                onOpenPresets = { navController.navigate(Routes.Presets) },
            )
        }
        composable(Routes.Usage) {
            UsageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Presets) {
            PresetsScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
            )
        }
    }
}
