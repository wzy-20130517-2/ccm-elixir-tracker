package com.ccm.elixir.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ccm.elixir.data.BattlePoller
import com.ccm.elixir.data.ElixirTracker
import com.ccm.elixir.data.EnemyTracker
import com.ccm.elixir.ui.ControlPad
import com.ccm.elixir.ui.OverlayRoot

/**
 * 两个窗口：
 *  1. 显示层：全屏透明，NOT_TOUCHABLE，不挡游戏
 *  2. 控制窗：小尺寸可触摸，可拖动
 */
class OverlayHost(private val ctx: Context) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = ctx.resources.displayMetrics.density

    private var displayView: ComposeView? = null
    private var controlView: ComposeView? = null
    private var controlParams: WindowManager.LayoutParams? = null

    private val stateHolder = mutableStateOf<BattlePoller.State?>(null)
    private var tracker: EnemyTracker? = null
    private var elixir: ElixirTracker? = null

    private var dragX = 0
    private var dragY = 0

    fun bindTrackers(t: EnemyTracker, e: ElixirTracker) {
        tracker = t
        elixir = e
    }

    fun onState(s: BattlePoller.State) {
        stateHolder.value = s
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    fun show() {
        if (displayView != null) return

        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        /* ---------- 1. 显示层 ---------- */
        val dv = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeViewModelStoreOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setContent {
                val st by stateHolder
                OverlayRoot(state = st, tracker = tracker, elixir = elixir, context = ctx)
            }
        }
        val dp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        /* ---------- 2. 控制窗 ---------- */
        val cv = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeViewModelStoreOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setContent {
                ControlPad(elixir = elixir) { dx, dy -> moveControl(dx, dy) }
            }
        }
        val cp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * density).toInt()
            y = (150 * density).toInt()
        }

        runCatching { wm.addView(cv, cp) }.onSuccess {
            controlView = cv
            controlParams = cp
        }
        runCatching { wm.addView(dv, dp) }.onSuccess {
            displayView = dv
        }
    }

    private fun moveControl(dx: Int, dy: Int) {
        val p = controlParams ?: return
        if (dx == dragX && dy == dragY) return
        dragX = dx
        dragY = dy
        val base = controlBase
        p.x = base[0] + dx
        p.y = base[1] + dy
        controlView?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    private var controlBase = intArrayOf(0, 0)

    fun hide() {
        displayView?.let { runCatching { wm.removeView(it) } }
        controlView?.let { runCatching { wm.removeView(it) } }
        displayView = null
        controlView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
