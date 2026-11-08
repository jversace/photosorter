package fr.photosorter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.photosorter.ui.screens.GalleryPlaceholderScreen
import fr.photosorter.ui.screens.LabelPlaceholderScreen
import fr.photosorter.ui.screens.ScanPlaceholderScreen
import fr.photosorter.ui.screens.ImageDetailPlaceholderScreen

@Composable
fun PhotoSorterNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.GALLERY,
    ) {
        composable(Destinations.GALLERY) {
            GalleryPlaceholderScreen(
                onOpenDetail = { imageId ->
                    navController.navigate(Destinations.detail(imageId))
                },
                onOpenScan = { navController.navigate(Destinations.SCAN) },
                onOpenLabels = { navController.navigate(Destinations.LABELS) },
            )
        }

        composable(
            route = Destinations.DETAIL_ROUTE,
            arguments = listOf(navArgument(Destinations.DETAIL_ARG_IMAGE_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments?.getLong(Destinations.DETAIL_ARG_IMAGE_ID) ?: -1L
            ImageDetailPlaceholderScreen(
                imageId = imageId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destinations.SCAN) {
            ScanPlaceholderScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.LABELS) {
            LabelPlaceholderScreen(onBack = { navController.popBackStack() })
        }
    }
}
