package com.fhdfh.launcher.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nb.api.DPMKLauncherHostActivity as LauncherHostActivity
import com.example.nb.api.DPMKLauncherHostPlugin as LauncherHostPlugin
import com.example.nb.api.DPMKLauncherSdk as LauncherSdk
import com.fhdfh.launcher.ObserveAppHomeRoute
import com.fhdfh.launcher.sample.databinding.ActivitySampleAppBinding

/**
 * 最小 launcher 宿主。正式 App 也是继承 [LauncherHostActivity]，只管 App 页和业务跳转。
 *
 * 接入清单：
 * - Application 继承 LauncherHostApp（见 [SampleApp]）
 * - Manifest 给本 Activity 声明 MAIN+LAUNCHER 和 HOME+DEFAULT
 * - 桌面业务入口在 SampleApp 中动态配置，无需 activity-alias
 * - [createAppPage] 返回 App 页，Compose 或 View 均可
 * - [hostPlugins] 挂桌面槽广告、应用列表广告、ROLE_HOME 后的桌面引导
 * - 申请默认桌面调 [requestHomeRole]，AAR 会记下引导 pending
 * - 应用列表点自身走 [onOpenHostAppFromDrawer]，不要再 startActivity
 * - 重复宿主由 AAR [com.fhdfh.launcher.HostActivityTask] 在挂桌面前自动清掉
 */
class SampleHostActivity : LauncherHostActivity() {

    private lateinit var appPageHost: FrameLayout
    private var viewBinding: ActivitySampleAppBinding? = null
    private var useCompose = true
    private var lastShortcut by mutableStateOf<String?>(null)
    private var homeHeld by mutableStateOf(false)
    private var inAppSecondPage by mutableStateOf(false)
    private var composeNav: NavController? = null

    /** 要留引用：首页「Replay desktop guide」直接调 [DesktopGuidePlugin.replay]。 */
    private val desktopGuidePlugin = DesktopGuidePlugin()

    /**
     * 部分系统授权后会重建 Activity，此回调不会执行，SDK 会在新 Activity 中自动恢复。
     * 未重建且能收到结果时，这里作为兜底；必须确认已持有 ROLE_HOME 后再通知 SDK。
     */
    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshHomeHeld()
        if (!homeHeld) return@registerForActivityResult
        notifyHomeRoleGranted()
        Toast.makeText(this, R.string.toast_home_granted, Toast.LENGTH_SHORT).show()
    }

    override fun hostPlugins(): List<LauncherHostPlugin> = listOf(
        DesktopSlotAdPlugin(),
        AllAppsSlotAdPlugin(),
        desktopGuidePlugin,
    )

    /**
     * App 页根 View。AAR 把它放进 ViewPager 第 0 页，第 1 页是 Lawnchair 桌面。
     * 在 super.onCreate 里被调用，早于本类 onCreate 的后续代码，
     * 所以 ViewBinding 要在这里 inflate，不能放到 onCreate 里。
     */
    override fun createAppPage(context: Context): View {
        useCompose = SampleUiMode.isCompose(this)
        appPageHost = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        renderAppPage()
        return appPageHost
    }

    /**
     * 通知 / 小组件等带了自己的 extras 时返回 true。
     * AAR 先排除桌面槽位，再问这里；为 true 不当成系统 Home 键。
     */
    override fun hasHostLaunchExtras(intent: Intent): Boolean {
        return intent.getBooleanExtra(EXTRA_HOST_JUMP, false)
                || LauncherSdk.desktopEntryId(intent) != null
    }

    override fun onDesktopShortcut(slot: String): Boolean {
        lastShortcut = DESKTOP_SHORTCUTS[slot] ?: slot
        bindViewPage()
        Toast.makeText(this, getString(R.string.toast_shortcut, lastShortcut), Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onLeaveSecondaryRouteForDesktop() {
        composeNav?.popBackStack(ROUTE_HOME, false)
        leaveInAppSecondPage()
    }

    /** 应用列表点了本应用图标：AAR 已拦截 startActivity，这里只热启动到 App 页。 */
    override fun onOpenHostAppFromDrawer() {
        lastShortcut = null
        composeNav?.popBackStack(ROUTE_HOME, false)
        leaveInAppSecondPage()
        bindViewPage()
        Toast.makeText(this, R.string.toast_open_self, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshHomeHeld()
        consumeHostIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeHostIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshHomeHeld()
    }

    private fun renderAppPage() {
        if (!::appPageHost.isInitialized) return
        appPageHost.removeAllViews()
        viewBinding = null
        if (useCompose) {
            appPageHost.addView(createComposeAppPage())
        } else {
            val binding = createViewAppPage()
            viewBinding = binding
            appPageHost.addView(binding.root)
            bindViewPage()
        }
    }

    private fun switchUi() {
        useCompose = !useCompose
        if (!useCompose) composeNav = null
        SampleUiMode.setCompose(this, useCompose)
        renderAppPage()
    }

    private fun createComposeAppPage(): View {
        return ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    val navController = rememberNavController()
                    composeNav = navController
                    ObserveAppHomeRoute(navController) { dest -> dest.route == ROUTE_HOME }
                    NavHost(
                        navController,
                        startDestination = ROUTE_HOME,
                        enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None },
                        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                        popExitTransition = { androidx.compose.animation.ExitTransition.None },
                    ) {
                        composable(ROUTE_HOME) {
                            SampleAppPage(
                                lastShortcut = lastShortcut,
                                homeHeld = homeHeld,
                                onRequestHome = ::onSetOrOpenHome,
                                onReplayGuide = ::replayDesktopGuide,
                                onSimulateNotify = ::simulateNotification,
                                onOpenDetail = ::openDetailActivity,
                                onOpenInPage = { navController.navigate(ROUTE_SECOND) },
                                onSwitchUi = ::switchUi,
                                onToggleDrawerBackground = ::toggleDrawerBackground,
                            )
                        }
                        composable(ROUTE_SECOND) {
                            SampleSecondPage(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    private fun createViewAppPage(): ActivitySampleAppBinding {
        val binding = ActivitySampleAppBinding.inflate(LayoutInflater.from(this), appPageHost, false)
        binding.sampleHomeAction.setOnClickListener { onSetOrOpenHome() }
        binding.sampleReplayGuide.setOnClickListener { replayDesktopGuide() }
        binding.sampleSimulateNotify.setOnClickListener { simulateNotification() }
        binding.sampleOpenDetail.setOnClickListener { openDetailActivity() }
        binding.sampleOpenInPage.setOnClickListener { enterInAppSecondPage() }
        binding.sampleSwitchUi.setOnClickListener { switchUi() }
        binding.sampleDrawerBackground.setOnClickListener { toggleDrawerBackground() }
        return binding
    }

    private fun bindViewPage() {
        val binding = viewBinding ?: return
        val second = inAppSecondPage
        binding.sampleTitle.setText(
            if (second) R.string.in_page_title else R.string.sample_title_view,
        )
        binding.sampleHomeStatus.setText(
            when {
                second -> R.string.in_page_body
                homeHeld -> R.string.home_held
                else -> R.string.home_not_held
            },
        )
        binding.sampleHomeAction.apply {
            visibility = if (second) View.GONE else View.VISIBLE
            setText(if (homeHeld) R.string.open_desktop else R.string.set_as_home)
        }
        binding.sampleReplayGuide.visibility =
            if (!second && homeHeld) View.VISIBLE else View.GONE
        binding.sampleSimulateNotify.visibility =
            if (second) View.GONE else View.VISIBLE
        binding.sampleOpenDetail.visibility =
            if (second) View.GONE else View.VISIBLE
        binding.sampleOpenInPage.apply {
            visibility = View.VISIBLE
            setText(if (second) R.string.back_home else R.string.open_in_page)
            setOnClickListener { if (second) leaveInAppSecondPage() else enterInAppSecondPage() }
        }
        binding.sampleSwitchUi.visibility =
            if (second) View.GONE else View.VISIBLE
        binding.sampleDrawerBackground.visibility = if (second) View.GONE else View.VISIBLE
        if (second || lastShortcut == null) {
            binding.sampleLastShortcut.visibility = View.GONE
        } else {
            binding.sampleLastShortcut.visibility = View.VISIBLE
            binding.sampleLastShortcut.text = getString(R.string.last_shortcut, lastShortcut)
        }
    }

    private fun refreshHomeHeld() {
        homeHeld = isHomeRoleHeld()
        bindViewPage()
    }

    private fun toggleDrawerBackground() {
        val reset = LauncherSdk.getAllAppsBackgroundColor() != null
        val night = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (reset) null else if (night) 0xFF164B73.toInt() else 0xFFB3D9FF.toInt()
        LauncherSdk.setAllAppsBackgroundColor(color)
        showHostDesktopPage(animate = false)
//        showAllApps()
        Toast.makeText(this, if (reset) R.string.drawer_background_default
            else R.string.drawer_background_custom, Toast.LENGTH_SHORT).show()
    }

    private fun consumeHostIntent(intent: Intent?) {
        LauncherSdk.desktopEntryId(intent)?.let { id ->
            lastShortcut = id
            showHostAppPage(animate = false)
            bindViewPage()
            Toast.makeText(this, getString(R.string.toast_shortcut, id), Toast.LENGTH_SHORT).show()
            return
        }
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_HOST_JUMP, false)) {
            lastShortcut = getString(R.string.last_jump, getString(R.string.simulate_notify))
            showHostAppPage(animate = false)
            bindViewPage()
            Toast.makeText(this, R.string.toast_notify, Toast.LENGTH_SHORT).show()
        }
    }

    /** singleTask 下会走 onNewIntent，带 EXTRA_HOST_JUMP，验证 hasHostLaunchExtras。 */
    private fun simulateNotification() {
        startActivity(Intent(this, SampleHostActivity::class.java).putExtra(EXTRA_HOST_JUMP, true))
    }

    private fun openDetailActivity() {
        startActivity(Intent(this, SampleDetailActivity::class.java))
    }

    /**
     * View 模式同 Activity 换页，SDK 感知不到，必须手调 [setOnAppHome]：
     * 不在首页时关左滑进桌面，从桌面返回也按二级页处理。
     * Compose 模式由 [ObserveAppHomeRoute] 处理，不走这里。
     */
    private fun enterInAppSecondPage() {
        inAppSecondPage = true
        setOnAppHome(false)
        bindViewPage()
    }

    /** 回到 View 首页，同 [enterInAppSecondPage]，需手调 [setOnAppHome] 恢复首页状态。 */
    private fun leaveInAppSecondPage() {
        inAppSecondPage = false
        setOnAppHome(true)
        bindViewPage()
    }

    /** 未持有 HOME：拉系统授权页。已持有：直接切桌面。 */
    private fun onSetOrOpenHome() {
        if (homeHeld) {
            showHostDesktopPage(animate = true)
            return
        }
        if (!requestHomeRole(homeRoleLauncher)) {
            Toast.makeText(this, R.string.toast_home_already, Toast.LENGTH_SHORT).show()
        }
    }

    private fun replayDesktopGuide() {
        if (!isHomeRoleHeld()) {
            Toast.makeText(this, R.string.toast_guide_need_home, Toast.LENGTH_SHORT).show()
            return
        }
        desktopGuidePlugin.replay(this)
    }

    companion object {
        const val EXTRA_HOST_JUMP = "host_jump"
        const val ROUTE_HOME = "home"
        const val ROUTE_SECOND = "second"

        private val DESKTOP_SHORTCUTS = mapOf(
            LauncherSdk.DesktopShortcut.SLOT_1 to "Shortcut 1",
            LauncherSdk.DesktopShortcut.SLOT_2 to "Shortcut 2",
            LauncherSdk.DesktopShortcut.SLOT_3 to "Shortcut 3",
            LauncherSdk.DesktopShortcut.SLOT_4 to "Shortcut 4",
            LauncherSdk.DesktopShortcut.SLOT_5 to "Promotion 1",
            LauncherSdk.DesktopShortcut.SLOT_6 to "Promotion 2",
            LauncherSdk.DesktopShortcut.SLOT_7 to "Promotion 3",
            LauncherSdk.DesktopShortcut.SLOT_8 to "Promotion 4",
        )
    }
}

private object SampleUiMode {
    private const val PREFS = "sample_ui"
    private const val KEY_COMPOSE = "use_compose"

    fun isCompose(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_COMPOSE, true)

    fun setCompose(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPOSE, value)
            .apply()
    }
}

@Composable
private fun SampleSecondPage(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.in_page_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.in_page_body), modifier = Modifier.fillMaxWidth())
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_home))
        }
    }
}

@Composable
private fun SampleAppPage(
    lastShortcut: String?,
    homeHeld: Boolean,
    onRequestHome: () -> Unit,
    onReplayGuide: () -> Unit,
    onSimulateNotify: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenInPage: () -> Unit,
    onSwitchUi: () -> Unit,
    onToggleDrawerBackground: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.sample_title_compose), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(if (homeHeld) R.string.home_held else R.string.home_not_held),
            modifier = Modifier.fillMaxWidth(),
        )
        if (lastShortcut != null) {
            Text(stringResource(R.string.last_shortcut, lastShortcut), modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = onRequestHome, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (homeHeld) R.string.open_desktop else R.string.set_as_home))
        }
        if (homeHeld) {
            OutlinedButton(onClick = onReplayGuide, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.replay_desktop_guide))
            }
        }
        OutlinedButton(onClick = onSimulateNotify, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.simulate_notify))
        }
        OutlinedButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_detail_activity))
        }
        OutlinedButton(onClick = onOpenInPage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_in_page))
        }
        OutlinedButton(onClick = onSwitchUi, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.switch_to_view))
        }
        OutlinedButton(onClick = onToggleDrawerBackground, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.toggle_drawer_background))
        }
    }
}
