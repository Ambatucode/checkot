package com.app.misproject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

// Import all your screen composables
import com.app.misproject.LoginScreen
import com.app.misproject.SignupScreen
import com.app.misproject.HomeScreen
import com.app.misproject.ProfileScreen
import com.app.misproject.BookServiceScreen
import com.app.misproject.BookingsScreen
import com.app.misproject.BookingDetailsScreen
import com.app.misproject.MyCarsScreen
import com.app.misproject.AddCarScreen
import com.app.misproject.MyBookingsScreen  // Add this import

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
    object MyBookings : Screen("my_bookings")  // ADD THIS LINE

    object EditProfile : Screen("edit_profile")

    object OwnerDashboard : Screen("owner_dashboard")

}



@Composable
fun NavigationGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = when (authState) {
            is AuthState.Authenticated -> Screen.Home.route
            else -> Screen.Login.route
        }
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
            OwnerDashboard(navController = navController)
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
                authViewModel = authViewModel
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

        composable(Screen.BookService.route) {
            BookServiceScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        composable(Screen.BookServiceWithType.route) { backStackEntry ->
            val serviceType = backStackEntry.arguments?.getString("serviceType")
            BookServiceScreen(
                navController = navController,
                authViewModel = authViewModel,
                preselectedService = serviceType?.let { ServiceType.valueOf(it) }
            )
        }

        composable(Screen.Bookings.route) {
            BookingsScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        composable(Screen.BookingDetails.route) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId")
            BookingDetailsScreen(
                bookingId = bookingId,
                navController = navController,
                authViewModel = authViewModel
            )
        }

        composable(Screen.Cars.route) {
            MyCarsScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        composable(Screen.AddCar.route) {
            AddCarScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        // ADD THIS NEW COMPOSABLE - put it after AddCar or before the closing brace
        composable(Screen.MyBookings.route) {
            MyBookingsScreen(
                navController = navController,
                authViewModel = authViewModel
            )


        }

        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }
    }
}