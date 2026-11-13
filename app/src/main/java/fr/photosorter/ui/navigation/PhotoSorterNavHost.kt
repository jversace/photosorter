package fr.photosorter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.photosorter.ui.detail.DetailScreen
import fr.photosorter.ui.gallery.GalleryScreen
import fr.photosorter.ui.labels.LabelScreen
import fr.photosorter.ui.scan.ScanScreen

@Composable
fun PhotoSorterNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.GALLERY,
    ) {
        composable(Destinations.GALLERY) {
            GalleryScreen(
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
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.SCAN) {
            ScanScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.LABELS) {
            LabelScreen(onBack = { navController.popBackStack() })
        }
    }
}
