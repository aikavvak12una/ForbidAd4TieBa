package androidx.coordinatorlayout.widget

import android.view.View

/** No Android View is constructed by this metadata/notification fixture. */
class CoordinatorLayout {
    var dependencyNotifications = 0
    fun dispatchDependentViewsChanged(view: View?) {
        dependencyNotifications++
    }

    open class Behavior

    class LayoutParams {
        private var behavior: Behavior? = null
        fun getBehavior(): Behavior? = behavior
        fun setBehavior(value: Behavior?) { behavior = value }
    }
}
