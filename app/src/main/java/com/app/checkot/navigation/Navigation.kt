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
    object Bookings : Screen("bookings")
    object BookingDetails : Screen("booking_details/{bookingId}")
    object Cars : Screen("cars")
    object AddCar : Screen("add_car")
    object MyBookings : Screen("my_bookings")
    object EditProfile : Screen("edit_profile")
    object OwnerDashboard : Screen("owner_dashboard")
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
            if (currentUser?.role == "owner") Screen.OwnerDashboard.route
            else Screen.Home.route
        } else {
            Screen.Login.route
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
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
        composable(Screen.OwnerDashboard.route) {
            OwnerDashboard(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        composable(Screen.Signup.route) {
            SignupScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onSignupSuccess = {
                    navController.popBackStack()
                    navController.navigate(Screen.Home.route)
                },
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
                    navController.popBackStack()
                    navController.navigate(Screen.Login.route)
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
        composable(Screen.Bookings.route) {
            BookingsScreen(
                navController = navController,
                bookingViewModel = bookingViewModel
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
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                navController = navController,
                authViewModel = authViewModel,
                profileViewModel = profileViewModel
            )
        }
    }
}
