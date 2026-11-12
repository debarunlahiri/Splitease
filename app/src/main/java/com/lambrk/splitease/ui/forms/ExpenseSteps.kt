@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lambrk.splitease.ui.forms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lambrk.splitease.data.DeviceContact
import com.lambrk.splitease.data.loadDeviceContacts
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.currencySymbol
import com.lambrk.splitease.ui.util.friendlyDate
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.memberPhotoUrl
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.optionalText
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.json.JSONObject

// ------------------------------------------------------------------ Who's in

@Composable
internal fun SelectCircle(selected: Boolean) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                modifier = Modifier.padding(4.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
internal fun InitialsAvatar(name: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Surface(Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * One searchable list: your group and your phone contacts together, with the people already picked
 * shown as chips on top and your recent groups of people one tap away. Tapping a contact who isn't
 * in the group adds them to it (and to this split); they can be invited to SplitEase afterwards.
 */
@Composable
internal fun ColumnScope.WhoIsInStep(
    model: SplitEaseViewModel,
    members: List<JSONObject>,
    participants: Set<String>,
    onParticipants: (Set<String>) -> Unit,
    enabled: Boolean,
    personLabel: (String) -> String,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<DeviceContact>?>(null) }
    var invited by remember { mutableStateOf(setOf<String>()) }
    // Contacts added to the group during this visit, so they can be invited from their row.
    var added by remember { mutableStateOf(mapOf<String, DeviceContact>()) }
    var contactsAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }
    LaunchedEffect(contactsAllowed) {
        if (contactsAllowed && contacts == null) {
            contacts = try { loadDeviceContacts(context) } catch (_: Exception) { emptyList() }
        } else if (!contactsAllowed) {
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    val enabledNow = enabled && !model.busy
    fun short(id: String) = personLabel(id).substringBefore(" (").substringBefore(" ")
    fun toggle(id: String) = onParticipants(if (id in participants) participants - id else participants + id)

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        placeholder = { Text("Search contacts or type a name") },
        shape = RoundedCornerShape(18.dp),
    )

    // ---- picked people
    if (participants.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.map { it.getString("userId") }.filter { it in participants }.forEach { id ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.clickable(enabled = enabledNow) { toggle(id) },
                ) {
                    Text(
                        short(id),
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    // ---- recent gangs: the groups of people you split with most recently
    val memberIds = members.map { it.getString("userId") }.toSet()
    val gangs = model.expenses
        .map { e -> e.optJSONArray("shares")?.objects().orEmpty().map { it.optString("userId") }.toSet() }
        .filter { it.isNotEmpty() && memberIds.containsAll(it) && it != participants }
        .distinct()
        .take(3)
    if (gangs.isNotEmpty() && query.isBlank()) {
        Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recent gangs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                gangs.forEach { gang ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(enabled = enabledNow) { onParticipants(gang) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.People, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(gang.joinToString(", ") { short(it) }, maxLines = 1)
                    }
                }
            }
        }
    }

    // ---- one list: group members, then contacts who aren't in the group
    val needle = query.trim().lowercase()
    fun matches(vararg text: String?) = needle.isEmpty() || text.any { it?.lowercase()?.contains(needle) == true }
    val shownMembers = members.filter { matches(personLabel(it.getString("userId")), it.optionalText("email"), it.optionalText("upiId")) }
    shownMembers.forEach { member ->
        val id = member.getString("userId")
        val pending = member.optBoolean("pending")
        val contact = added[id]
        PersonRow(
            name = personLabel(id),
            detail = when {
                pending -> contact?.phone ?: contact?.email ?: "Not on SplitEase yet"
                else -> member.optionalText("upiId").takeIf { it.isNotBlank() }?.let { "UPI $it" } ?: member.optionalText("email")
            },
            photo = memberPhotoUrl(id, model),
            selected = id in participants,
            enabled = enabledNow,
            action = if (pending && contact != null && contact.id + "!" !in invited) (if (contact.email != null) "Invite" else "Send link") else null,
            onAction = {
                if (contact?.email != null) model.invite(contact.email) { invited = invited + (contact.id + "!") }
                else if (contact?.phone != null) model.shareInvite { text ->
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.phone)}")).putExtra("sms_body", text)
                    context.startActivity(Intent.createChooser(intent, "Invite ${contact.name}"))
                    invited = invited + (contact.id + "!")
                }
            },
        ) { toggle(id) }
    }

    if (!contactsAllowed) {
        OutlinedButton(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Show my contacts") }
    } else {
        val memberEmails = members.map { it.optionalText("email").lowercase() }.filter { it.isNotBlank() }.toSet()
        val memberNames = members.map { personLabel(it.getString("userId")).substringBefore(" (").lowercase() }.toSet()
        val addedKeys = added.values.map { it.id }.toSet()
        val shownContacts = (contacts ?: emptyList()).filter {
            it.id !in addedKeys && it.email !in memberEmails && it.name.lowercase() !in memberNames && matches(it.name, it.email, it.phone)
        }.take(80)
        val typedEmail = query.trim().lowercase().takeIf { android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() }
        if (typedEmail != null && typedEmail !in memberEmails && shownContacts.none { it.email == typedEmail }) {
            PersonRow(typedEmail, "Add and invite by email", null, false, enabledNow, null, {}) {
                val contact = DeviceContact("typed:$typedEmail", typedEmail.substringBefore('@'), null, typedEmail)
                model.addContactMember(contact.name, contact.email, null) { id ->
                    added = added + (id to contact)
                    onParticipants(participants + id)
                    model.invite(typedEmail) {}
                }
            }
        }
        shownContacts.forEach { contact ->
            PersonRow(
                name = contact.name,
                detail = contact.phone ?: contact.email.orEmpty(),
                photo = null,
                selected = false,
                enabled = enabledNow,
                action = null,
                onAction = {},
            ) {
                model.addContactMember(contact.name, contact.email, contact.phone) { id ->
                    added = added + (id to contact)
                    onParticipants(participants + id)
                }
            }
        }
        if (contacts != null && shownContacts.isEmpty() && shownMembers.isEmpty() && typedEmail == null) {
            Text("No one found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        "Picking a contact adds them to this group. Invite them to SplitEase from their row so they can see and pay the split.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun PersonRow(
    name: String,
    detail: String,
    photo: String?,
    selected: Boolean,
    enabled: Boolean,
    action: String?,
    onAction: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photo.isNullOrBlank()) InitialsAvatar(name) else ProfileAvatar(photo, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (detail.isNotBlank() && detail != name) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (action != null) TextButton(onClick = onAction, enabled = enabled) { Text(action) }
        SelectCircle(selected)
    }
}

// ------------------------------------------------------------------ Split it

internal val SPLIT_MODES = listOf("EQUAL" to "Equal", "EXACT" to "Exact", "PERCENTAGE" to "%", "SHARES" to "Shares")

/** Equal / Exact / % / Shares tabs and one card per person with their share. */
@Composable
internal fun ColumnScope.SplitItStep(
    model: SplitEaseViewModel,
    total: String,
    currency: String,
    split: String,
    onSplit: (String) -> Unit,
    people: List<String>,
    shares: Map<String, String>,
    onShare: (String, String) -> Unit,
    amounts: Map<String, BigDecimal>,
    enabled: Boolean,
    highlight: Boolean,
    personLabel: (String) -> String,
    payerLabel: String,
    paymentStatus: (String) -> Pair<String, String>?,
    onPaymentClick: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Paid by $payerLabel", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            money(total.ifBlank { "0" }, currency),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SPLIT_MODES.forEach { (type, label) ->
                val selected = split == type
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                        .clickable(enabled = enabled && !model.busy) { onSplit(type) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
    val unit = when (split) {
        "PERCENTAGE" -> "%"
        "SHARES" -> "shares"
        else -> currencySymbol(currency)
    }
    people.forEach { id ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(memberPhotoUrl(id, model), size = 36.dp)
                    Text(personLabel(id), Modifier.weight(1f), maxLines = 1)
                    if (split == "SHARES") {
                        ShareStepper(
                            value = shares[id].orEmpty(),
                            onChange = { onShare(id, it) },
                            enabled = enabled,
                        )
                    } else if (split != "EQUAL") {
                        CompactNumberField(
                            value = shares[id].orEmpty(),
                            onChange = { onShare(id, com.lambrk.splitease.ui.components.sanitizeAmount(it)) },
                            suffix = if (split == "PERCENTAGE") "%" else null,
                            prefix = if (split == "EXACT") currencySymbol(currency) else null,
                            enabled = enabled,
                            isError = highlight,
                        )
                    }
                    if (split != "EXACT") {
                        Text(
                            amounts[id]?.let { money(it.toPlainString(), currency) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                paymentStatus(id)?.let { (label, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable { onPaymentClick(id) },
                        ) {
                            Text(
                                "$label · Update",
                                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    when (split) {
        "EQUAL" -> Text("Shared equally between ${people.size} ${if (people.size == 1) "person" else "people"}.")
        "SHARES" -> Text(
            "Enter how many shares each person has. Someone with 2 shares pays twice as much as someone with 1.",
            style = MaterialTheme.typography.bodySmall,
        )
        else -> {
            val target = if (split == "PERCENTAGE") BigDecimal(100) else total.toBigDecimalOrNull()
            if (target != null) {
                val left = target - people.sumOf { shares[it]?.toBigDecimalOrNull() ?: BigDecimal.ZERO }
                Text(
                    when {
                        left.signum() == 0 -> "Fully split"
                        split == "PERCENTAGE" -> "${left.stripTrailingZeros().toPlainString()}% left to assign"
                        else -> "${money(left.toPlainString(), currency)} left to assign"
                    },
                    color = if (left.signum() == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Whether the split can be saved as entered (the same rules the server enforces). */
internal fun splitIsComplete(split: String, total: String, people: List<String>, shares: Map<String, String>): Boolean {
    if (people.isEmpty()) return false
    val values = people.map { shares[it]?.toBigDecimalOrNull() }
    return when (split) {
        "EQUAL" -> true
        "SHARES" -> values.all { it != null && it.signum() >= 0 } && values.any { it != null && it.signum() > 0 }
        else -> {
            if (values.any { it == null || it.signum() < 0 }) return false
            val target = if (split == "PERCENTAGE") BigDecimal(100) else total.toBigDecimalOrNull() ?: return false
            values.sumOf { it!! }.compareTo(target) == 0
        }
    }
}

// -------------------------------------------------------------------- Review

/** "Looks right?": a summary, a note, and the optional extras. */
@Composable
internal fun ColumnScope.ReviewStep(
    description: String,
    total: String,
    currency: String,
    people: List<String>,
    amounts: Map<String, BigDecimal>,
    personLabel: (String) -> String,
    photo: (String) -> String?,
    notes: String,
    onNotes: (String) -> Unit,
    remindAfterDays: Int?,
    onRemind: (Int?) -> Unit,
    payByDate: String?,
    onPayBy: (String?) -> Unit,
    saveTemplate: Boolean,
    onSaveTemplate: (Boolean) -> Unit,
    templateName: String,
    onTemplateName: (String) -> Unit,
    enabled: Boolean,
    showTemplate: Boolean,
    dueFor: List<String> = emptyList(),
    personDue: Map<String, String> = emptyMap(),
    onPersonDue: (String, String?) -> Unit = { _, _ -> },
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(description, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(money(total.ifBlank { "0" }, currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            people.forEach { id ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(photo(id), size = 32.dp)
                    Text(personLabel(id), Modifier.weight(1f))
                    Text(amounts[id]?.let { money(it.toPlainString(), currency) } ?: "—", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Field(notes, onNotes, "Note (optional)", enabled = enabled)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Notifications, null, Modifier.size(20.dp))
        Text("Auto-remind if unpaid", style = MaterialTheme.typography.titleSmall)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(2, 3, 7).forEach { days ->
            FilterChip(
                selected = remindAfterDays == days,
                onClick = { onRemind(if (remindAfterDays == days) null else days) },
                enabled = enabled,
                label = { Text("After $days days") },
            )
        }
    }
    Text(
        "You get a nudge with the message ready. The app never texts your friends itself.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Pay-by date", style = MaterialTheme.typography.titleSmall)
    val today = LocalDate.now()
    val options = listOf(
        "In 3 days" to today.plusDays(3),
        "In a week" to today.plusWeeks(1),
        "End of month" to today.with(TemporalAdjusters.lastDayOfMonth()),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, date) ->
            FilterChip(
                selected = payByDate == date.toString(),
                onClick = { onPayBy(if (payByDate == date.toString()) null else date.toString()) },
                enabled = enabled,
                label = { Text(label) },
            )
        }
    }
    Text(
        if (payByDate != null) "Pay by ${friendlyDate(payByDate)}. Friends see it on their settle page, and nudges say when it is late."
        else "Friends see the date on their settle page, and nudges say when it is late.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (dueFor.isNotEmpty()) {
        Text("Due date per person (optional)", style = MaterialTheme.typography.titleSmall)
        dueFor.forEach { id ->
            DueDateRow(
                name = personLabel(id).substringBefore(" ("),
                date = personDue[id],
                fallback = payByDate,
                enabled = enabled,
                onChange = { onPersonDue(id, it) },
            )
        }
    }

    if (showTemplate) {
        FilterChip(
            selected = saveTemplate,
            onClick = { onSaveTemplate(!saveTemplate) },
            enabled = enabled,
            leadingIcon = { Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp)) },
            label = { Text("Save as template") },
        )
        if (saveTemplate) Field(templateName, onTemplateName, "Template name", enabled = enabled)
    }
}

// ------------------------------------------------------------ Templates bar

/** Saved templates as chips; tapping one fills the form. */
@Composable
internal fun ColumnScope.TemplateChips(templates: List<JSONObject>, onPick: (JSONObject) -> Unit) {
    if (templates.isEmpty()) return
    Text("Start from a template", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        templates.forEach { template ->
            AssistChip(
                onClick = { onPick(template) },
                leadingIcon = { Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp)) },
                label = { Text(template.optString("name")) },
            )
        }
    }
}

// ------------------------------------------------------------------ Payments

/** The payer records that [name] paid them back, in full or in part, or nudges them. */
@Composable
internal fun SharePaymentDialog(
    name: String,
    amount: BigDecimal,
    paid: BigDecimal,
    currency: String,
    description: String,
    busy: Boolean,
    onSettle: () -> Unit,
    onPartial: (String) -> Unit,
    onNudge: (push: Boolean, email: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var partial by remember { mutableStateOf("") }
    val left = (amount - paid).max(BigDecimal.ZERO)
    val status = when {
        left.signum() == 0 -> "Paid"
        paid.signum() > 0 -> "Partly paid"
        else -> "Unpaid"
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(money(amount.toPlainString(), currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(status, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (paid.signum() > 0) Text("${money(paid.toPlainString(), currency)} received so far")
                if (left.signum() > 0) {
                    Button(onClick = onSettle, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Check, null)
                        Text("Mark settled (money arrived)", Modifier.padding(start = 8.dp))
                    }
                    Text("Partial amount received", style = MaterialTheme.typography.labelLarge)
                    Field(partial, { partial = com.lambrk.splitease.ui.components.sanitizeAmount(it) }, "e.g. ${left.divide(BigDecimal(2), 0, java.math.RoundingMode.DOWN).toPlainString()}", decimal = true)
                    OutlinedButton(
                        onClick = { onPartial(partial) },
                        enabled = !busy && partial.toBigDecimalOrNull()?.let { it.signum() > 0 && it <= left } == true,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Record partial") }
                    OutlinedButton(
                        onClick = {
                            val text = "Hi $name, a friendly reminder to settle up ${money(left.toPlainString(), currency)} for \"$description\" on SplitEase."
                            val whatsapp = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.whatsapp")
                                .putExtra(Intent.EXTRA_TEXT, text)
                            try {
                                context.startActivity(whatsapp)
                            } catch (_: Exception) {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                        "Send a reminder",
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Notifications, null)
                        Text("Nudge on WhatsApp", Modifier.padding(start = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onNudge(true, false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text("Notify in app")
                        }
                        OutlinedButton(onClick = { onNudge(false, true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text("Send email")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } },
    )
}

// ------------------------------------------------------------------ Split is live

/** Shown after a new expense is saved: send it where the group already chats. */
@Composable
internal fun SplitLiveScreen(
    summary: String,
    people: List<Pair<String, String>>,
    personalMessage: (name: String, amount: String) -> String,
    onShowQr: (() -> Unit)?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(0) }
    fun send(text: String, title: String) {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title)
        )
    }
    androidx.activity.compose.BackHandler(onBack = onDone)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Check, "Done", Modifier.padding(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("Split is live", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Now send it where the gang already is.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { send(summary, "Send to the group chat") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Send to the group chat")
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        people.forEachIndexed { i, (name, amount) ->
                            FilterChip(
                                selected = i == selected,
                                onClick = { selected = i },
                                label = { Column { Text(name); Text(amount, style = MaterialTheme.typography.labelSmall) } },
                            )
                        }
                    }
                    if (people.isNotEmpty()) {
                        val (name, amount) = people[selected.coerceIn(0, people.lastIndex)]
                        OutlinedButton(
                            onClick = {
                                send(personalMessage(name, amount), "Message $name")
                                if (selected < people.lastIndex) selected++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Message $name personally (${selected + 1}/${people.size})") }
                    }
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Split", summary))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy message") }
                }
            }
            Text(
                "Messages go from your own phone, through WhatsApp or any chat app you pick.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onShowQr != null) {
                OutlinedButton(onClick = onShowQr, modifier = Modifier.fillMaxWidth()) { Text("Show QR to collect") }
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

/** A small right-aligned number box, sized to its content instead of a full-height form field. */
@Composable
private fun CompactNumberField(
    value: String,
    onChange: (String) -> Unit,
    prefix: String?,
    suffix: String?,
    enabled: Boolean,
    isError: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        color = colors.onSurface,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(112.dp),
        decorationBox = { inner ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceContainerHigh,
                border = BorderStroke(1.dp, if (isError) colors.error else colors.outlineVariant),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    prefix?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty()) Text("0", color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                        inner()
                    }
                    suffix?.let { Text(" $it", color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    )
}

/** − 1 + control for the number of shares. */
@Composable
private fun ShareStepper(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val current = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
    fun set(next: BigDecimal) = onChange(next.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString())
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(
                onClick = { set(current - BigDecimal.ONE) },
                enabled = enabled && current.signum() > 0,
                modifier = Modifier.size(36.dp),
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                current.stripTrailingZeros().toPlainString(),
                Modifier.width(32.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.material3.IconButton(
                onClick = { set(current + BigDecimal.ONE) },
                enabled = enabled,
                modifier = Modifier.size(36.dp),
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/** Asks the payer to add a UPI ID (once) so friends can pay them back on INR expenses. */
@Composable
internal fun ColumnScope.UpiPrompt(model: SplitEaseViewModel) {
    val saved = model.user?.optionalText("upiId").orEmpty()
    if (saved.isNotBlank()) {
        Text(
            "Friends can pay you on UPI: $saved",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    var value by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Get paid on UPI", style = MaterialTheme.typography.titleSmall)
            Text(
                "Add your UPI ID and your friends can pay this expense with one tap, even without the app.",
                style = MaterialTheme.typography.bodySmall,
            )
            Field(value, { value = it.trim() }, "name@bank")
            OutlinedButton(onClick = { model.saveUpiId(value) }, enabled = !model.busy && value.isNotBlank()) {
                Text("Save UPI ID")
            }
        }
    }
}

/** One person's own due date: tap to pick, or clear to follow the expense's pay-by date. */
@Composable
private fun DueDateRow(name: String, date: String?, fallback: String?, enabled: Boolean, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, Modifier.weight(1f), maxLines = 1)
        FilterChip(
            selected = date != null,
            onClick = { open = true },
            enabled = enabled,
            label = { Text(date?.let { friendlyDate(it) } ?: fallback?.let { "Same as pay-by" } ?: "Set date") },
        )
        if (date != null) TextButton(onClick = { onChange(null) }, enabled = enabled) { Text("Clear") }
    }
    if (open) {
        val initial = runCatching {
            LocalDate.parse(date ?: fallback ?: LocalDate.now().toString()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            onChange(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                        }
                        open = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}
