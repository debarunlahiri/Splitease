package com.lambrk.splitease.data

import android.content.Context
import android.provider.ContactsContract
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A person from the phone's address book, with whatever we can reach them on. */
internal data class DeviceContact(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
)

/** Reads the address book. Needs READ_CONTACTS; contacts are only used on this device to find people to invite. */
internal suspend fun loadDeviceContacts(context: Context): List<DeviceContact> =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val names = linkedMapOf<String, String>()
        val phones = mutableMapOf<String, String>()
        val emails = mutableMapOf<String, String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC",
        )?.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                names.putIfAbsent(id, it.getString(1).orEmpty())
                it.getString(2)?.takeIf { n -> n.isNotBlank() }?.let { n -> phones.putIfAbsent(id, n) }
            }
        }
        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            ),
            null, null, ContactsContract.CommonDataKinds.Email.DISPLAY_NAME + " COLLATE NOCASE ASC",
        )?.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                names.putIfAbsent(id, it.getString(1).orEmpty())
                it.getString(2)?.takeIf { e -> e.isNotBlank() }
                    ?.let { e -> emails.putIfAbsent(id, e.trim().lowercase(Locale.ROOT)) }
            }
        }
        names.map { (id, name) ->
            DeviceContact(id, name.ifBlank { emails[id] ?: phones[id].orEmpty() }, phones[id], emails[id])
        }.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
