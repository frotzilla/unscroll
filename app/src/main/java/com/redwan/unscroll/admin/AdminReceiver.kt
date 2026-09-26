package com.redwan.unscroll.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.redwan.unscroll.R

/**
 * Device admin with no policies. While active, Android refuses to uninstall the app until admin is
 * turned off, which adds a step the accessibility guard can intercept.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

        fun isActive(ctx: Context) =
            (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(component(ctx))

        fun remove(ctx: Context) {
            (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).removeActiveAdmin(component(ctx))
        }

        fun requestIntent(ctx: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(ctx))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets Unscroll make itself harder to uninstall on impulse.")
    }
}
