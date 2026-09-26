package com.redwan.unscroll.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

object C {
    val bg = Color(0xFF000000)
    val card = Color(0xFF141414)
    val card2 = Color(0xFF1E1E1E)
    val line = Color(0xFF2C2C2C)
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFF9A9A9A)
    val pink = Color(0xFFFF69B4)
    val warn = Color(0xFFFFB74D)
}

private val scheme: ColorScheme = darkColorScheme(
    primary = C.pink, onPrimary = Color.Black, secondary = C.pink, background = C.bg, surface = C.card,
    onSurface = C.text, onBackground = C.text, surfaceVariant = C.card2, outline = C.line,
)

val Round = RoundedCornerShape(14.dp)
val Pill = RoundedCornerShape(50)
private val shapes = Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), Round, RoundedCornerShape(18.dp), RoundedCornerShape(24.dp))

@Composable
fun UnscrollTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.text)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.text, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }
}

@Composable
fun Section(title: String) {
    Text(
        title.uppercase(), color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Round)
            .background(C.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Body(text: String, color: Color = C.muted, size: Int = 14, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = size.sp, lineHeight = (size + 6).sp, modifier = modifier)

@Composable
fun Title(text: String, size: Int = 17, color: Color = C.text, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)

/** A compact pill toggle. */
@Composable
fun SquareSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val x = animateDpAsState(if (checked) 22.dp else 2.dp, label = "thumb")
    Box(
        Modifier
            .size(46.dp, 26.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(Pill)
            .background(if (checked) C.pink else C.card2)
            .border(1.dp, if (checked) C.pink else C.line, Pill)
            .clickable(enabled = enabled) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = x.value, y = 3.dp).size(20.dp).clip(CircleShape).background(if (checked) Color.White else C.muted))
    }
}

@Composable
fun SquareCheck(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).border(2.dp, if (checked) C.pink else C.muted, RoundedCornerShape(6.dp)).background(if (checked) C.pink else Color.Transparent)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ToggleRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, badge: String? = null, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 10.dp).alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = C.text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(title, 16)
                if (badge != null) Badge(badge)
            }
            if (sub != null) Body(sub, size = 13)
        }
        Spacer(Modifier.width(12.dp))
        SquareSwitch(checked, onChange, enabled)
    }
}

@Composable
fun Badge(text: String) {
    Text(
        text, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp).clip(Pill).background(C.pink).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
fun NavRow(title: String, sub: String? = null, trailing: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = C.text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Title(title, 16)
            if (sub != null) Body(sub, size = 13)
        }
        if (trailing != null) Text(trailing, color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.muted)
    }
}

@Composable
fun PinkButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).alpha(if (enabled) 1f else 0.35f).clip(Round).background(C.pink).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).alpha(if (enabled) 1f else 0.35f).clip(Round).border(BorderStroke(1.dp, C.line), Round).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.text, fontSize = 16.sp) }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text, color = if (selected) Color.Black else C.text, fontSize = 14.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(Pill)
            .background(if (selected) C.pink else C.card2)
            .border(1.dp, if (selected) C.pink else C.line, Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun Stepper(value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, C.line, CircleShape).clickable(onClick = onDec), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, "Decrease", tint = C.text, modifier = Modifier.size(18.dp))
        }
        Text(value, color = C.text, fontSize = 15.sp, modifier = Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, C.line, CircleShape).clickable(onClick = onInc), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, "Increase", tint = C.text, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    singleLine: Boolean = false, minLines: Int = 1, number: Boolean = false, secret: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = C.muted) }, singleLine = singleLine, minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.NumberPassword) else KeyboardOptions.Default,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.pink, unfocusedBorderColor = C.line, cursorColor = C.pink,
            focusedTextColor = C.text, unfocusedTextColor = C.text,
            focusedContainerColor = C.card, unfocusedContainerColor = C.card,
        ),
    )
}

@Composable
fun SquareDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.card).padding(22.dp), content = content)
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))

/** A bar filled to [fraction]. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = C.pink) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(Pill).background(C.card2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(Pill).background(color))
    }
}

@Composable
fun RowGap(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)

// App icons and labels

private val iconCache = HashMap<String, ImageBitmap?>()
private val labelCache = HashMap<String, String>()

fun appLabel(ctx: Context, pkg: String): String = labelCache.getOrPut(pkg) {
    runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}

fun appIcon(ctx: Context, pkg: String): ImageBitmap? = iconCache.getOrPut(pkg) {
    runCatching {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 96, 96)
        d.draw(Canvas(bmp))
        bmp.asImageBitmap()
    }.getOrNull()
}

@Composable
fun AppIcon(pkg: String, size: Int = 36) {
    val ctx = LocalContext.current
    val img = remember(pkg) { appIcon(ctx, pkg) }
    if (img != null) Image(img, null, Modifier.size(size.dp))
    else Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(C.card2))
}
