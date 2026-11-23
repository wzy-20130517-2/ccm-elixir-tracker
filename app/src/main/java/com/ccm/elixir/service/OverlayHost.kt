package com.ccm.elixir.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
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
import com.ccm.elixir.ui.OverlayRoot

/**
 * 悬浮窗宿主：用 Compose 渲染，挂到 WindowManager。
 *
 * 一个覆盖全屏的透明窗口，内部自己定位各个元素。
 */
class OverlayHost(private val ctx: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    private val stateHolder = mutableStateOf<BattlePoller.State?>(null)

    fun onState(s: BattlePoller.State) {
        stateHolder.value = s
    }

    fun show() {
        if (view != null) return

        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED

        val cv = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeViewModelStoreOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setContent {
                val st by stateHolder
                OverlayRoot(state = st, context = ctx)
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        runCatching { wm.addView(cv, p) }.onSuccess {
            view = cv
            params = p
        }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
    }
}