package com.lambrk.splitease.ui.screens.profile

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.auth.PhoneVerificationException
import com.lambrk.splitease.auth.PhoneVerifier
import com.lambrk.splitease.ui.SplitEaseViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.lambrk.splitease.ui.components.Panel
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.i18n.Text
import com.lambrk.splitease.ui.i18n.tr
import com.lambrk.splitease.ui.util.COUNTRIES
import com.lambrk.splitease.ui.util.checkUpiId
import com.lambrk.splitease.ui.util.defaultCountry
import com.lambrk.splitease.ui.util.openUpiVerification
import com.lambrk.splitease.ui.util.optionalText
import com.lambrk.splitease.ui.util.withCountryCode
import com.lambrk.splitease.ui.util.exampleNumber
import com.lambrk.splitease.ui.util.formatAsYouType
import com.lambrk.splitease.ui.util.prettyPhone
import com.lambrk.splitease.ui.util.splitE164
import org.json.JSONObject

/** Your picture and name, the details friends use to reach you, and the app's settings. */
@Composable
internal fun ProfileScreen(
    model: SplitEaseViewModel,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
) {
    val user = model.user ?: return
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    androidx.compose.runtime.LaunchedEffect(Unit) { model.refreshPhonePolicy() }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    val phone = user.optionalText("phone")
    val upi = user.optionalText("upiId")
    val upiChecked = upi.isNotBlank() && user.optBoolean("upiVerified")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ---- Who you are
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileAvatar(user.optionalText("photoUrl"), size = 88.dp)
                Spacer(Modifier.height(6.dp))
                Text(user.getString("displayName"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                SelectionContainer {
                    Text(user.getString("email"), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }

        if (model.needsReconnect) {
            item {
                Panel {
                    Text("Reconnect your account", style = MaterialTheme.typography.titleMedium)
                    Text("Sign in with Google again to get updates and save your splits online.")
                    Button(enabled = !model.busy, onClick = { (context as? Activity)?.let(model::login) }) {
                        Text("Reconnect Google account")
                    }
                }
            }
        }

        // ---- How friends reach and pay you
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("How friends reach you")
                Group {
                    ProfileRow(
                        icon = Icons.Rounded.Phone,
                        title = "Phone number",
                        value = if (phone.isBlank()) "Add" else prettyPhone(phone),
                        highlight = phone.isBlank(),
                        badge = when {
                            phone.isBlank() -> null
                            user.optBoolean("phoneVerified") -> "Verified"
                            model.phoneVerificationRequired -> "Not verified"
                            else -> null
                        },
                        badgeGood = user.optBoolean("phoneVerified"),
                        onClick = { editing = "phone" },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = colors.outlineVariant)
                    ProfileRow(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        title = "UPI ID",
                        value = if (upi.isBlank()) "Add" else upi,
                        highlight = upi.isBlank(),
                        badge = when {
                            upi.isBlank() -> null
                            upiChecked -> "Checked"
                            else -> "Not checked"
                        },
                        badgeGood = upiChecked,
                        onClick = { editing = "upi" },
                    )
                }
            }
        }

        // ---- App
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("App")
                Group {
                    ProfileRow(Icons.Rounded.Settings, "Settings", onClick = onSettings)
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = colors.outlineVariant)
                    val plan = model.entitlement?.optionalText("planCode")?.let(::planName)
                    ProfileRow(Icons.Rounded.CreditCard, "Plan", value = plan ?: "", onClick = onPlan)
                }
            }
        }
    }

    when (editing) {
        "phone" -> PhoneSheet(model, phone, user.optBoolean("phoneVerified")) { editing = null }
        "upi" -> UpiSheet(model, user) { editing = null }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

/** One soft card holding a few rows, instead of a box around every item. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) { Column { content() } }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    value: String = "",
    highlight: Boolean = false,
    badge: String? = null,
    badgeGood: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = badge?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (badgeGood) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = if (badgeGood) colors.primary else colors.tertiary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (value.isNotBlank()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (highlight) colors.primary else colors.onSurfaceVariant,
                        fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

// ------------------------------------------------------------------ Phone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSheet(model: SplitEaseViewModel, saved: String, verified: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val savedParts = if (saved.isNotBlank()) splitE164(saved) else null
    var country by rememberSaveable { mutableStateOf(savedParts?.first ?: defaultCountry(context).iso) }
    val current = COUNTRIES.firstOrNull { it.iso == country } ?: COUNTRIES.first()
    // Only the digits are kept; the box shows them grouped the way people in that country write numbers.
    var digits by rememberSaveable { mutableStateOf(savedParts?.second.orEmpty()) }
    var menu by rememberSaveable { mutableStateOf(false) }
    val full = if (digits.isBlank()) null else withCountryCode(digits, current.iso)
    // When codes are required, a saved number that was never confirmed can be confirmed now; a confirmed one needs no new code.
    val needsCode = model.phoneVerificationRequired
    val ready = full != null && (if (needsCode) !(full == saved && verified) else full != saved)
    androidx.compose.runtime.LaunchedEffect(Unit) { model.refreshPhonePolicy() }
    val example = exampleNumber(current.iso)
    val shown = formatAsYouType(current.iso, digits)

    // Step 1: the number. Step 2: the code Firebase texts to it.
    val verifier = remember { PhoneVerifier() }
    val scope = rememberCoroutineScope()
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var working by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    var cooldown by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(cooldown) {
        if (cooldown > 0) { kotlinx.coroutines.delay(1000); cooldown -= 1 }
    }

    fun finish(task: suspend () -> String) {
        working = true
        problem = null
        scope.launch {
            try {
                val token = task()
                model.verifyPhone(token) { onDismiss() }
            } catch (error: PhoneVerificationException) {
                problem = error.message
            } catch (error: Exception) {
                problem = "Couldn't verify the number. Check it and try again."
            } finally {
                working = false
            }
        }
    }

    fun sendCode() {
        val target = full ?: return
        val activity = context as? Activity ?: return
        working = true
        problem = null
        verifier.sendCode(
            activity = activity,
            e164 = target,
            onCodeSent = { working = false; codeSent = true; code = ""; cooldown = 30 },
            onAutoVerified = { credential -> working = false; finish { verifier.link(credential) } },
            onError = { message -> working = false; problem = message },
        )
    }

    if (menu) CountryPicker(onPick = { country = it.iso; menu = false }, onDismiss = { menu = false })

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Phone number", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!codeSent) {
                Text("Friends who have your number can find you.", color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { menu = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Flag(current.iso)
                            Text("+${current.dial}")
                        }
                    }
                    OutlinedTextField(
                        value = androidx.compose.ui.text.input.TextFieldValue(shown, androidx.compose.ui.text.TextRange(shown.length)),
                        onValueChange = { digits = it.text.filter { ch -> ch.isDigit() }.take(15) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(example ?: "Your number") },
                        isError = digits.isNotBlank() && full == null,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
                if (digits.isNotBlank() && full == null) {
                    Text(
                        "Enter a valid number for ${current.name}" + (example?.let { ", like $it" } ?: "") + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
                problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                Button(
                    onClick = { if (needsCode) sendCode() else { model.savePhone(full); onDismiss() } },
                    enabled = !model.busy && !working && ready,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (working) "Sending…" else if (needsCode) "Send code" else "Save") }
                if (needsCode) {
                    Text("We'll text you a 6-digit code to make sure the number is yours.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            } else {
                Text("We sent a code to ${full ?: ""}", color = colors.onSurfaceVariant)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("6-digit code") },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    isError = problem != null,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                Button(
                    onClick = { finish { verifier.confirm(code) } },
                    enabled = !model.busy && !working && code.length == 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (working) "Checking…" else "Verify") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { codeSent = false; problem = null }, enabled = !working) { Text("Change number") }
                    TextButton(onClick = ::sendCode, enabled = !working && cooldown == 0) {
                        Text(if (cooldown > 0) "Resend code in ${cooldown}s" else "Resend code")
                    }
                }
            }
            if (saved.isNotBlank() && !codeSent) {
                TextButton(onClick = { model.savePhone(null); onDismiss() }, enabled = !model.busy && !working, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove my number", color = colors.error)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ UPI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpiSheet(model: SplitEaseViewModel, user: JSONObject, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val saved = user.optionalText("upiId")
    var upi by rememberSaveable { mutableStateOf(saved) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val check = checkUpiId(upi)
    val checked = saved.isNotBlank() && user.optBoolean("upiVerified")
    val changed = upi.trim().lowercase() != saved
    val showError = touched && upi.isNotBlank() && !check.valid

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("UPI ID", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Friends use this to pay you back.", color = colors.onSurfaceVariant)
            OutlinedTextField(
                value = upi,
                onValueChange = { upi = it.trim(); touched = true },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("name@bank") },
                isError = showError,
                supportingText = {
                    when {
                        showError -> Text(check.reason.orEmpty())
                        upi.isNotBlank() && check.valid && check.unknownHandle -> Text("We don't recognise that bank handle. Double-check it.")
                        upi.isBlank() -> Text("For example name@oksbi or 9876543210@ybl")
                    }
                },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Email),
            )
            Button(
                enabled = !model.busy && changed && (upi.isBlank() || check.valid),
                onClick = { model.saveUpiId(upi); if (upi.isBlank()) onDismiss() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (upi.isBlank()) "Remove UPI ID" else "Save") }

            // One short step: see your own name in your UPI app, then say yes.
            if (saved.isNotBlank() && !changed) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (checked) colors.primaryContainer else colors.surfaceContainerHigh,
                    border = if (checked) null else BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (checked) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary)
                                Text("This UPI ID is yours", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text("Make sure this ID is yours", fontWeight = FontWeight.SemiBold)
                            Text(
                                "We'll open your UPI app. If you see your own name, come back and tap Yes. No money is sent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    val opened = openUpiVerification(context, saved, user.optionalText("displayName"))
                                    if (opened) confirming = true else model.message = tr("No UPI app found on this phone.")
                                },
                                enabled = !model.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Check my name") }
                        }
                    }
                }
            }
        }
    }
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Did you see your own name?") },
            text = { Text("If you saw someone else's name, or an error, fix the UPI ID.") },
            confirmButton = {
                TextButton(onClick = { confirming = false; model.confirmUpiId() }, enabled = !model.busy) { Text("Yes, that's me") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("No, not yet") } },
        )
    }
}

/** A searchable list of countries with their flags and calling codes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountryPicker(onPick: (com.lambrk.splitease.ui.util.Country) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().removePrefix("+").lowercase()
    val shown = COUNTRIES.filter {
        needle.isEmpty() || it.name.lowercase().contains(needle) || it.dial.startsWith(needle) || tr(it.name).lowercase().contains(needle)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose country", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search country or code") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false).navigationBarsPadding()) {
                items(shown.size) { index ->
                    val c = shown[index]
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        leadingContent = { Flag(c.iso, width = 32.dp) },
                        headlineContent = { Text(c.name) },
                        trailingContent = { Text("+${c.dial}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { onPick(c) },
                    )
                }
                if (shown.isEmpty()) item { Text("No country found.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A country flag picture (not an emoji); the country code in a small badge shows while it loads or offline. */
@Composable
internal fun Flag(iso: String, width: androidx.compose.ui.unit.Dp = 28.dp) {
    val height = width * 0.75f
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.width(width).height(height).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(iso, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        coil.compose.AsyncImage(
            model = "https://flagcdn.com/w80/${iso.lowercase()}.png",
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}
