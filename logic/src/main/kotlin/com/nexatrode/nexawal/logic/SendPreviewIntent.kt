package com.nexatrode.nexawal.logic

data class SendPreviewIntent(
    val destination: String,
    val amount: Long?,
    val session: String,
    val node: String,
    val maximum: Boolean,
)
