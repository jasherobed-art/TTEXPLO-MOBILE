package com.ttexplo.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.license.LicenseManager
import com.ttexplo.mobile.license.LicenseStatus
import com.ttexplo.mobile.ui.navigation.RouteParamCodec
import com.ttexplo.mobile.ui.navigation.Screen
import com.ttexplo.mobile.ui.screens.accesorios.AccesoriosHeaderScreen
import com.ttexplo.mobile.ui.screens.accesorios.AccesoriosScanScreen
import com.ttexplo.mobile.ui.screens.accesorios.AccesoriosViewModel
import com.ttexplo.mobile.ui.screens.catalog.CatalogManagementScreen
import com.ttexplo.mobile.ui.screens.despacho.DespachoHeaderScreen
import com.ttexplo.mobile.ui.screens.despacho.DespachoScanScreen
import com.ttexplo.mobile.ui.screens.despacho.DespachoViewModel
import com.ttexplo.mobile.ui.screens.devolucion.DevolucionScreen
import com.ttexplo.mobile.ui.screens.entradas.EntradasHeaderScreen
import com.ttexplo.mobile.ui.screens.entradas.EntradasScanScreen
import com.ttexplo.mobile.ui.screens.entradas.EntradasViewModel
import com.ttexplo.mobile.ui.screens.history.ValesHistoryScreen
import com.ttexplo.mobile.ui.screens.home.HomeScreen
import com.ttexplo.mobile.ui.screens.inventory.InventoryScreen
import com.ttexplo.mobile.ui.screens.inventory.InventorySyncScreen
import com.ttexplo.mobile.ui.screens.license.LicenseScreen
import com.ttexplo.mobile.ui.screens.settings.SettingsScreen
import com.ttexplo.mobile.ui.theme.MineDarkBackground
import com.ttexplo.mobile.ui.theme.TTExploTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.parseColor("#121212")),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.parseColor("#121212"))
        )

        // Request Bluetooth permissions on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                bluetoothPermissionLauncher.launch(needed.toTypedArray())
            }
        }

        // Seed initial bundled catalog if database is empty
        lifecycleScope.launch(Dispatchers.IO) {
            val db = (application as TTExploApp).database
            val repo = CatalogRepositoryImpl(db.productoDao())
            if (repo.countProducts() == 0) {
                try {
                    val csv = assets.open("catalogo_productos.csv").bufferedReader().use { it.readText() }
                    repo.importCsv(csv)
                } catch (e: Exception) {
                    // Ignore asset loading error
                }
            }
        }

        // Provision a one-time 1-day HMAC-SHA256 temporary license on clean install
        LicenseManager.ensureOneDayTemporaryLicenseIfNeeded(this)

        setContent {
            TTExploTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MineDarkBackground
                ) {
                    TTExploAppNavigation()
                }
            }
        }
    }
}

@Composable
fun TTExploAppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val initialLicense = remember { LicenseManager.checkLicense(context) }
    val isLicensed = initialLicense.status == LicenseStatus.VALID || initialLicense.status == LicenseStatus.EXPIRING_SOON
    val startDest = if (isLicensed) Screen.Home.route else Screen.License.createRoute(canBack = false)

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        // HOME SCREEN (Welcome Window)
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToEntradas = { navController.navigate(Screen.EntradasHeader.route) },
                onNavigateToDespacho = { navController.navigate(Screen.DespachoHeader.route) },
                onNavigateToAccesorios = { navController.navigate(Screen.AccesoriosHeader.route) },
                onNavigateToDevolucionAccesorios = { navController.navigate(Screen.DevolucionAccesoriosHeader.route) },
                onNavigateToDevoluciones = { navController.navigate(Screen.Devoluciones.createRoute()) },
                onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToHistory = { navController.navigate(Screen.ValesHistory.route) },
                onNavigateToLicense = { navController.navigate(Screen.License.createRoute(canBack = true)) }
            )
        }

        // 1. ENTRADAS MODULE
        composable(Screen.EntradasHeader.route) {
            EntradasHeaderScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinueToScan = { prov, polv, fact ->
                    navController.navigate(Screen.EntradasScan.createRoute(prov, polv, fact))
                }
            )
        }

        composable(
            route = Screen.EntradasScan.route,
            arguments = listOf(
                navArgument("prov") { type = NavType.StringType },
                navArgument("polv") { type = NavType.StringType },
                navArgument("fact") { type = NavType.StringType },
                navArgument("id") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val prov = RouteParamCodec.decode(backStackEntry.arguments?.getString("prov"))
            val polv = RouteParamCodec.decode(backStackEntry.arguments?.getString("polv"))
            val fact = RouteParamCodec.decode(backStackEntry.arguments?.getString("fact"))
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            val entradasVm: EntradasViewModel = viewModel()

            EntradasScanScreen(
                proveedor = prov,
                polvorin = polv,
                factura = fact,
                entradaId = id,
                viewModel = entradasVm,
                onNavigateBack = { navController.popBackStack() },
                onFinished = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        // 2. DESPACHO MODULE
        composable(Screen.DespachoHeader.route) {
            DespachoHeaderScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinueToScan = { vale, empresa, lugar, transportista, turno, voladura ->
                    navController.navigate(Screen.DespachoScan.createRoute(vale, empresa, lugar, transportista, turno, voladura))
                }
            )
        }

        composable(
            route = Screen.DespachoScan.route,
            arguments = listOf(
                navArgument("vale") { type = NavType.StringType },
                navArgument("empresa") { type = NavType.StringType },
                navArgument("lugar") { type = NavType.StringType },
                navArgument("transportista") { type = NavType.StringType },
                navArgument("turno") { type = NavType.StringType },
                navArgument("voladura") { type = NavType.StringType },
                navArgument("id") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val vale = RouteParamCodec.decode(backStackEntry.arguments?.getString("vale"))
            val empresa = RouteParamCodec.decode(backStackEntry.arguments?.getString("empresa"))
            val lugar = RouteParamCodec.decode(backStackEntry.arguments?.getString("lugar"))
            val transportista = RouteParamCodec.decode(backStackEntry.arguments?.getString("transportista"))
            val turno = RouteParamCodec.decode(backStackEntry.arguments?.getString("turno"))
            val voladura = RouteParamCodec.decode(backStackEntry.arguments?.getString("voladura"))
            val id = backStackEntry.arguments?.getLong("id") ?: 0L

            val despachoVm: DespachoViewModel = viewModel()

            DespachoScanScreen(
                vale = vale,
                empresa = empresa,
                lugar = lugar,
                transportista = transportista,
                turno = turno,
                voladura = voladura,
                despachoId = id,
                viewModel = despachoVm,
                onNavigateBack = { navController.popBackStack() },
                onFinished = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        // 3. ACCESORIOS MODULE
        composable(Screen.AccesoriosHeader.route) {
            AccesoriosHeaderScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinueToScan = { vale, empresa, lugar, transportista, turno, recibio, entrego, voladura ->
                    navController.navigate(Screen.AccesoriosScan.createRoute(vale, empresa, lugar, transportista, turno, recibio, entrego, voladura))
                }
            )
        }

        composable(
            route = Screen.AccesoriosScan.route,
            arguments = listOf(
                navArgument("vale") { type = NavType.StringType },
                navArgument("empresa") { type = NavType.StringType },
                navArgument("lugar") { type = NavType.StringType },
                navArgument("transportista") { type = NavType.StringType },
                navArgument("turno") { type = NavType.StringType },
                navArgument("recibio") { type = NavType.StringType },
                navArgument("entrego") { type = NavType.StringType },
                navArgument("voladura") { type = NavType.StringType },
                navArgument("id") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val vale = RouteParamCodec.decode(backStackEntry.arguments?.getString("vale"))
            val empresa = RouteParamCodec.decode(backStackEntry.arguments?.getString("empresa"))
            val lugar = RouteParamCodec.decode(backStackEntry.arguments?.getString("lugar"))
            val transportista = RouteParamCodec.decode(backStackEntry.arguments?.getString("transportista"))
            val turno = RouteParamCodec.decode(backStackEntry.arguments?.getString("turno"))
            val recibio = RouteParamCodec.decode(backStackEntry.arguments?.getString("recibio"))
            val entrego = RouteParamCodec.decode(backStackEntry.arguments?.getString("entrego"))
            val voladura = RouteParamCodec.decode(backStackEntry.arguments?.getString("voladura"))
            val id = backStackEntry.arguments?.getLong("id") ?: 0L

            val accesoriosVm: AccesoriosViewModel = viewModel()

            AccesoriosScanScreen(
                vale = vale,
                empresa = empresa,
                lugar = lugar,
                transportista = transportista,
                turno = turno,
                recibio = recibio,
                entrego = entrego,
                voladura = voladura,
                valeId = id,
                viewModel = accesoriosVm,
                onNavigateBack = { navController.popBackStack() },
                onFinished = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        // 4. DEVOLUCIONES MODULE
        composable(
            route = Screen.Devoluciones.route,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            DevolucionScreen(
                onNavigateBack = { navController.popBackStack() },
                devolucionId = id
            )
        }

        // 5. DEVOLUCION DE ACCESORIOS MODULE
        composable(Screen.DevolucionAccesoriosHeader.route) {
            AccesoriosHeaderScreen(
                isDevolucion = true,
                onNavigateBack = { navController.popBackStack() },
                onContinueToScan = { vale, empresa, lugar, transportista, turno, recibio, entrego, voladura ->
                    navController.navigate(
                        Screen.DevolucionAccesoriosScan.createRoute(
                            vale,
                            empresa,
                            lugar,
                            transportista,
                            turno,
                            recibio,
                            entrego,
                            voladura
                        )
                    )
                }
            )
        }

        composable(
            route = Screen.DevolucionAccesoriosScan.route,
            arguments = listOf(
                navArgument("vale") { type = NavType.StringType },
                navArgument("empresa") { type = NavType.StringType },
                navArgument("lugar") { type = NavType.StringType },
                navArgument("transportista") { type = NavType.StringType },
                navArgument("turno") { type = NavType.StringType },
                navArgument("recibio") { type = NavType.StringType },
                navArgument("entrego") { type = NavType.StringType },
                navArgument("voladura") { type = NavType.StringType },
                navArgument("id") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val vale = RouteParamCodec.decode(backStackEntry.arguments?.getString("vale"))
            val empresa = RouteParamCodec.decode(backStackEntry.arguments?.getString("empresa"))
            val lugar = RouteParamCodec.decode(backStackEntry.arguments?.getString("lugar"))
            val transportista = RouteParamCodec.decode(backStackEntry.arguments?.getString("transportista"))
            val turno = RouteParamCodec.decode(backStackEntry.arguments?.getString("turno"))
            val recibio = RouteParamCodec.decode(backStackEntry.arguments?.getString("recibio"))
            val entrego = RouteParamCodec.decode(backStackEntry.arguments?.getString("entrego"))
            val voladura = RouteParamCodec.decode(backStackEntry.arguments?.getString("voladura"))
            val id = backStackEntry.arguments?.getLong("id") ?: 0L

            val accesoriosVm: AccesoriosViewModel = viewModel()

            AccesoriosScanScreen(
                vale = vale,
                empresa = empresa,
                lugar = lugar,
                transportista = transportista,
                turno = turno,
                recibio = recibio,
                entrego = entrego,
                voladura = voladura,
                valeId = id,
                isDevolucion = true,
                viewModel = accesoriosVm,
                onNavigateBack = { navController.popBackStack() },
                onFinished = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        // 6. SETTINGS & EXPORT MODULE
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateCatalog = { navController.navigate(Screen.CatalogManagement.route) },
                onNavigateHistory = { navController.navigate(Screen.ValesHistory.route) },
                onNavigateToLicense = { navController.navigate(Screen.License.createRoute(canBack = true)) }
            )
        }

        // 7. CATALOG CRUD MODULE
        composable(Screen.CatalogManagement.route) {
            CatalogManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 8. VALES HISTORY MODULE
        composable(Screen.ValesHistory.route) {
            ValesHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditDespacho = { id ->
                    navController.navigate(Screen.DespachoScan.createRoute("-", "-", "-", "-", "1", "-", id))
                },
                onEditEntrada = { id ->
                    navController.navigate(Screen.EntradasScan.createRoute("-", "-", "-", id))
                },
                onEditAccesorios = { id ->
                    navController.navigate(Screen.AccesoriosScan.createRoute("-", "-", "-", "-", "1", "-", "-", "-", id))
                },
                onEditDevolucionAccesorios = { id ->
                    navController.navigate(Screen.DevolucionAccesoriosScan.createRoute("-", "-", "-", "-", "1", "-", "-", "-", id))
                },
                onEditDevolucion = { id ->
                    navController.navigate(Screen.Devoluciones.createRoute(id))
                }
            )
        }

        // 8.5 INVENTARIO EN POLVORÍN EN VIVO
        composable(Screen.Inventory.route) { backStackEntry ->
            val inspectProdId = backStackEntry.savedStateHandle.get<String>("inspect_product_id")
            InventoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSync = { tab ->
                    navController.navigate(Screen.InventorySync.createRoute(tab))
                },
                initialInspectProductId = inspectProdId,
                onConsumeInspectProductId = {
                    backStackEntry.savedStateHandle.remove<String>("inspect_product_id")
                }
            )
        }

        // 8.6 CENTRO DE SINCRONIZACIÓN MULTI-ESCÁNER (QR ZEBRA + JSON)
        composable(
            route = Screen.InventorySync.route,
            arguments = listOf(
                navArgument("tab") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            InventorySyncScreen(
                initialTab = tab,
                onNavigateBack = { navController.popBackStack() },
                onInspectProduct = { prodId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("inspect_product_id", prodId)
                    navController.popBackStack()
                }
            )
        }

        // 9. LICENCIA & DISPOSITIVO MODULE
        composable(
            route = Screen.License.route,
            arguments = listOf(
                navArgument("canBack") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val canBack = backStackEntry.arguments?.getBoolean("canBack") ?: false
            LicenseScreen(
                canNavigateBack = canBack,
                onNavigateBack = { navController.popBackStack() },
                onLicenseActivated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
