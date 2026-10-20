package com.supakeyboard.neuralkey.ime.lifecycle

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.Window
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Robust base class bridging Android's InputMethodService to the modern AndroidX architecture components.
 * 
 * Implements:
 * - [LifecycleOwner] for coroutine lifecycle scopes and Compose lifecycle awareness.
 * - [ViewModelStoreOwner] for scoped ViewModels inside the keyboard view hierarchy.
 * - [SavedStateRegistryOwner] for state preservation across configuration changes and rememberSaveable.
 * 
 * Automatically attaches ViewTree owners to the IME window's root decorView and input views,
 * preventing memory leaks and crashes with Jetpack Compose.
 */
abstract class LifecycleInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry: LifecycleRegistry by lazy { LifecycleRegistry(this) }
    private val store: ViewModelStore by lazy { ViewModelStore() }
    private val savedStateRegistryController: SavedStateRegistryController by lazy {
        SavedStateRegistryController.create(this)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        attachViewTreeOwners(win.decorView)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        window?.window?.decorView?.let { attachViewTreeOwners(it) }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    /**
     * Binds this service as the Lifecycle, ViewModelStore, and SavedStateRegistry owner
     * to the given view hierarchy.
     */
    fun attachViewTreeOwners(view: View) {
        if (view.findViewTreeLifecycleOwner() == null) {
            view.setViewTreeLifecycleOwner(this)
        }
        if (view.findViewTreeViewModelStoreOwner() == null) {
            view.setViewTreeViewModelStoreOwner(this)
        }
        if (view.findViewTreeSavedStateRegistryOwner() == null) {
            view.setViewTreeSavedStateRegistryOwner(this)
        }
    }
}
