package io.pleo.antaeus.models

import java.time.LocalDate

data class Billing(
    val id: Int,
    val invoiceId: Int,
    val status: InvoiceStatus,
    val chargingDate: LocalDate,
    val statusMessage: String?
)
