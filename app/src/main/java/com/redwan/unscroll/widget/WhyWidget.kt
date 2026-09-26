package com.redwan.unscroll.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.redwan.unscroll.R
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.ui.MainActivity

/** Home screen widget that shows the user's "why" and their rule. */
class WhyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        private fun views(ctx: Context) = RemoteViews(ctx.packageName, R.layout.widget_why).apply {
            val why = Store.why.ifBlank { ctx.getString(R.string.widget_empty) }
            setTextViewText(R.id.widget_why, why)
            setTextViewText(R.id.widget_rule, Store.commitmentText()?.let { "My rule: $it" } ?: "")
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).putExtra("route", "tips"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setOnClickPendingIntent(R.id.widget_root, open)
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WhyWidget::class.java))
            ids.forEach { mgr.updateAppWidget(it, views(ctx)) }
        }
    }
}
