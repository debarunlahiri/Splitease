package com.lambrk.splitease.data

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.lambrk.splitease.SplitEaseApplication
import okhttp3.OkHttpClient

internal fun OkHttpClient.Builder.configureNetworkInspection() {
    val application = SplitEaseApplication.instance ?: return
    addInterceptor(
        ChuckerInterceptor.Builder(application)
            .redactHeaders("Authorization", "Idempotency-Key")
            .build(),
    )
}
