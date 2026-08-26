package com.app.checkot.navigation
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.app.checkot.ui.screens.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Home : Screen("home")
    object Profile : Screen("profile")
    object BookService : Screen("book_service")
    object BookServiceWithType : Screen("book_service/{serviceType}")
    object BookingDetails : Screen("booking_details/{bookingId}")
    object Cars : Screen("cars")
    object AddCar : Screen("add_car")
    object MyBookings : Screen("my_bookings")
    object CheckCar : Screen("check_car")
    object OwnerDashboard : Screen("owner_dashboard")
    object OwnerSignup : Screen("owner_signup")
    object AdminDashboard : Screen("admin_dashboard")
    object SetShopLocation : Screen("set_shop_location")
}
@Composable
fun NavigationGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel(),
    bookingViewModel: BookingViewModel = viewModel(),
    carViewModel: CarViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser by authViewModel.currentUserData.collectAsState()

    val startDest = remember(authState, currentUser) {
        if (authState is AuthState.Authenticated) {
            val user = currentUser
            when {
                user == null -> "auth_landing"
                user.role == "admin" -> Screen.AdminDashboard.route
                user.role == "owner" -> Screen.OwnerDashboard.route
                // Customers who registered via phone must complete profile first
                user.fullName == "New User" -> "complete_profile"
                else -> Screen.Home.route
            }
        } else {
            "auth_landing"
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable("auth_landing") {
            AuthLandingScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                navController = navController,
                onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                onLoginSuccess = {
                    navController.popBackStack()
                    navController.navigate(Screen.Home.route)
                },
                authViewModel = authViewModel
            )
        }
        composable(Screen.AdminDashboard.route) {
            AdminDashboard(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        composable(Screen.OwnerDashboard.route) {
            OwnerDashboard(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        composable(Screen.Signup.route) {
            SignupScreen(
                onNavigateToLogin = { navController.popBackStack() },
                // New accounts must verify their phone before entering the app.
                onSignupSuccess = {
                    navController.navigate("phone_verification/signup") {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }
        composable(Screen.OwnerSignup.route) {
            OwnerSignupScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                // Verify phone first; PhoneVerificationScreen then routes owners to
                // their dashboard by role on success.
                onSignupSuccess = {
                    navController.navigate("phone_verification/signup") {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }
        composable("phone_verification/{mode}") { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "signup"
            PhoneVerificationScreen(
                navController = navController,
                mode = mode,
                authViewModel = authViewModel
            )
        }
        composable("complete_profile") {
            CompleteProfileScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                authViewModel = authViewModel,
                bookingViewModel = bookingViewModel
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                authViewModel = authViewModel,
                onLogout = {
                    // Clear the whole back stack so Back can't return to an
                    // authenticated screen (dashboard/home) after logout.
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                navController = navController
            )
        }
        composable("book_service/{shopId}") { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            BookServiceScreen(
                navController = navController,
                authViewModel = authViewModel,
                bookingViewModel = bookingViewModel,
                carViewModel = carViewModel,
                shopId = shopId
            )
        }
        composable("book_service/{shopId}/{serviceType}") { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            val serviceType = backStackEntry.arguments?.getString("serviceType")
            BookServiceScreen(
                navController = navController,
                authViewModel = authViewModel,
                bookingViewModel = bookingViewModel,
                carViewModel = carViewModel,
                shopId = shopId,
                preselectedService = serviceType?.let { ServiceType.valueOf(it) }
            )
        }
        composable(Screen.BookingDetails.route) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId")
            BookingDetailsScreen(
                bookingId = bookingId,
                navController = navController,
                bookingViewModel = bookingViewModel
            )
        }
        composable(Screen.Cars.route) {
            MyCarsScreen(
                navController = navController,
                carViewModel = carViewModel,
                bookingViewModel = bookingViewModel
            )
        }
        composable(Screen.AddCar.route) {
            AddCarScreen(
                navController = navController,
                carViewModel = carViewModel
            )
        }
        composable(Screen.MyBookings.route) {
            MyBookingsScreen(
                navController = navController,
                bookingViewModel = bookingViewModel
            )
        }
        composable(Screen.CheckCar.route) {
            CheckCarScreen(navController = navController)
        }
        composable("shops_for_service/{serviceType}") { backStackEntry ->
            val serviceType = backStackEntry.arguments?.getString("serviceType") ?: ""
            ShopsForServiceScreen(
                navController = navController,
                serviceTypeName = serviceType
            )
        }
        composable(Screen.SetShopLocation.route) {
            SetShopLocationScreen(navController = navController)
        }
    }
}
