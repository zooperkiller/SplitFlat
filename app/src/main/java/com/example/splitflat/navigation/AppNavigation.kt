package com.example.splitflat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.splitflat.ui.auth.LoginScreen
import com.example.splitflat.ui.auth.SignUpScreen
import com.example.splitflat.ui.group.AddExpenseScreen
import com.example.splitflat.ui.home.CreateGroupScreen
import com.example.splitflat.ui.home.HomeScreen
import com.google.firebase.auth.FirebaseAuth

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object Home : Screen("home")
    object CreateGroup : Screen("create_group")
    object GroupDetail : Screen("group_detail/{groupId}") {
        fun createRoute(groupId: String) = "group_detail/$groupId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    
    // Check if user is already logged in
    val startDestination = if (auth.currentUser != null) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignUp.route)
                }
            )
        }
        
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Screen.CreateGroup.route)
                },
                onNavigateToGroupDetail = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                }
            )
        }
        
        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onGroupCreated = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.GroupDetail.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            if (groupId != null) {
                com.example.splitflat.ui.group.GroupDetailScreen(
                    groupId = groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddExpense = { id ->
                        navController.navigate("add_expense/$id")
                    },
                    onEditExpense = { gId, eId ->
                        navController.navigate("edit_expense/$gId/$eId")
                    }
                )
            }
        }
        
        composable(
            route = "add_expense/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            AddExpenseScreen(
                groupId = groupId,
                expenseId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "edit_expense/{groupId}/{expenseId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            val expenseId = backStackEntry.arguments?.getString("expenseId") ?: return@composable
            AddExpenseScreen(
                groupId = groupId,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
