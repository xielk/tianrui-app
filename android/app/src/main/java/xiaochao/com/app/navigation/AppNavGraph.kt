package xiaochao.com.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xiaochao.com.data.model.DeviceType
import xiaochao.com.data.session.AppSessionStore
import xiaochao.com.feature.auth.presentation.AuthViewModel
import xiaochao.com.feature.auth.ui.LoginScreen
import xiaochao.com.feature.auth.ui.RegisterScreen
import xiaochao.com.feature.f2.ui.AddVehicleScreen
import xiaochao.com.feature.f2.ui.F2ControlScreen
import xiaochao.com.feature.f2.ui.F2CurrentLocationMapScreen
import xiaochao.com.feature.f2.ui.F2SettingsScreen
import xiaochao.com.feature.f2.ui.F2ShareUsersScreen
import xiaochao.com.feature.f2.ui.F2TrackMapScreen
import xiaochao.com.feature.f2.ui.M1BControlScreen
import xiaochao.com.feature.f2.ui.MoveDeviceScreen
import xiaochao.com.feature.user.ui.BluetoothCalibrationScreen
import xiaochao.com.feature.user.ui.OtaUpdateScreen
import xiaochao.com.feature.user.ui.ProductDetailsScreen
import xiaochao.com.feature.user.ui.UserCenterScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val savedUuid = AppSessionStore.getUuid()
    val savedUserInfo = AppSessionStore.getUserInfoJson()
    val savedModelType = AppSessionStore.getModelType().uppercase()

    fun routeForModel(modelType: String): String {
        val value = modelType.uppercase()
        return when {
            value.contains("M1") -> "m1b"
            value.contains("F1") -> "f1"
            else -> "f2"
        }
    }

    val startDest = if (savedUuid.isNotEmpty() && savedUserInfo.isNotEmpty()) {
        xiaochao.com.core.AppConfig.deviceUuid = savedUuid
        routeForModel(savedModelType)
    } else "login"

    NavHost(navController = navController, startDestination = startDest) {
        composable("login") {
            val authViewModel: AuthViewModel = viewModel()
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { 
                    navController.navigate(routeForModel(AppSessionStore.getModelType())) {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        composable("register") {
            val authViewModel: AuthViewModel = viewModel()
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate(routeForModel(AppSessionStore.getModelType())) {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        composable("f2") {
            F2ControlScreen(
                onNavigateAddVehicle = { navController.navigate("addVehicle") },
                onNavigateSettings = { deviceKey -> navController.navigate("f2Settings/$deviceKey") },
                onNavigateShareUsers = { deviceKey -> navController.navigate("f2Share/$deviceKey") },
                onNavigateCurrentLocation = { deviceKey -> navController.navigate("f2MapCurrent/$deviceKey") },
                onNavigateTrack = { deviceKey -> navController.navigate("f2MapTrack/$deviceKey") },
                onNavigateUserCenter = { navController.navigate("userCenter") },
                onNavigateF1 = {
                    navController.navigate("f1") {
                        popUpTo("f2") { inclusive = true }
                    }
                },
                onNavigateF2 = {},
                onNavigateM1B = {
                    navController.navigate("m1b") {
                        popUpTo("f2") { inclusive = true }
                    }
                },
                onNavigateBleCalibration = { navController.navigate("bleCalibration") },
                onNavigateMoveDevice = { navController.navigate("moveDevice") },
            )
        }

        composable("f1") {
            F2ControlScreen(
                isF1Layout = true,
                onNavigateAddVehicle = { navController.navigate("addVehicle") },
                onNavigateSettings = { deviceKey -> navController.navigate("f1Settings/$deviceKey") },
                onNavigateShareUsers = { deviceKey -> navController.navigate("f2Share/$deviceKey") },
                onNavigateCurrentLocation = { deviceKey -> navController.navigate("f2MapCurrent/$deviceKey") },
                onNavigateTrack = { deviceKey -> navController.navigate("f2MapTrack/$deviceKey") },
                onNavigateUserCenter = { navController.navigate("userCenter") },
                onNavigateF1 = {},
                onNavigateF2 = {
                    navController.navigate("f2") {
                        popUpTo("f1") { inclusive = true }
                    }
                },
                onNavigateM1B = {
                    navController.navigate("m1b") {
                        popUpTo("f1") { inclusive = true }
                    }
                },
                onNavigateBleCalibration = { navController.navigate("bleCalibration") },
                onNavigateMoveDevice = { navController.navigate("moveDevice") },
            )
        }

        composable("moveDevice") {
            MoveDeviceScreen(
                onBack = { navController.popBackStack() },
                onNavigateBleCalibration = { navController.navigate("bleCalibration") },
            )
        }

        composable("m1b") {
            M1BControlScreen(
                onNavigateUserCenter = { navController.navigate("userCenter") },
                onNavigateCurrentLocation = { deviceKey -> navController.navigate("f2MapCurrent/$deviceKey") },
                onNavigateTrack = { deviceKey -> navController.navigate("f2MapTrack/$deviceKey") },
                onNavigateF1 = {
                    navController.navigate("f1") {
                        popUpTo("m1b") { inclusive = true }
                    }
                },
                onNavigateF2 = {
                    navController.navigate("f2") {
                        popUpTo("m1b") { inclusive = true }
                    }
                },
            )
        }

        composable("userCenter") {
            UserCenterScreen(
                onGoHome = {
                    val route = routeForModel(AppSessionStore.getModelType())
                    navController.navigate(route) {
                        popUpTo("userCenter") { inclusive = true }
                    }
                },
                onGoAddVehicle = { navController.navigate("addVehicle") },
                onGoProductDetails = { navController.navigate("productDetails") },
                onGoBleCalibration = { navController.navigate("bleCalibration") },
                onGoOtaUpdate = { navController.navigate("otaUpdate") },
                onLoggedOut = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("productDetails") {
            ProductDetailsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("bleCalibration") {
            BluetoothCalibrationScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("otaUpdate") {
            OtaUpdateScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("addVehicle") {
            AddVehicleScreen(
                onDone = {
                    navController.navigate(routeForModel(AppSessionStore.getModelType())) {
                        popUpTo("addVehicle") { inclusive = true }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "f2Settings/{deviceKey}",
            arguments = listOf(navArgument("deviceKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceKey = backStackEntry.arguments?.getString("deviceKey").orEmpty()
            F2SettingsScreen(
                deviceKey = deviceKey,
                onBack = { navController.popBackStack() },
                deviceType = DeviceType.F2,
            )
        }

        composable(
            route = "f1Settings/{deviceKey}",
            arguments = listOf(navArgument("deviceKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceKey = backStackEntry.arguments?.getString("deviceKey").orEmpty()
            F2SettingsScreen(
                deviceKey = deviceKey,
                onBack = { navController.popBackStack() },
                deviceType = DeviceType.F1,
                onNavigateBleCalibration = { navController.navigate("bleCalibration") },
            )
        }

        composable(
            route = "f2Share/{deviceKey}",
            arguments = listOf(navArgument("deviceKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceKey = backStackEntry.arguments?.getString("deviceKey").orEmpty()
            F2ShareUsersScreen(
                deviceKey = deviceKey,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "f2MapCurrent/{deviceKey}",
            arguments = listOf(navArgument("deviceKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceKey = backStackEntry.arguments?.getString("deviceKey").orEmpty()
            F2CurrentLocationMapScreen(
                deviceKey = deviceKey,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "f2MapTrack/{deviceKey}",
            arguments = listOf(navArgument("deviceKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceKey = backStackEntry.arguments?.getString("deviceKey").orEmpty()
            F2TrackMapScreen(
                deviceKey = deviceKey,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
