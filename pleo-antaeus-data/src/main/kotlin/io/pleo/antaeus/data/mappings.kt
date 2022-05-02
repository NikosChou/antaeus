/*
    Defines mappings between database rows and Kotlin objects.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.ResultRow
import org.joda.time.LocalDate

fun ResultRow.toInvoice(): Invoice = Invoice(
    id = this[InvoiceTable.id],
    amount = Money(
        value = this[InvoiceTable.value],
        currency = Currency.valueOf(this[InvoiceTable.currency])
    ),
    status = InvoiceStatus.valueOf(this[InvoiceTable.status]),
    customerId = this[InvoiceTable.customerId]
)

fun ResultRow.toBilling(): Billing = Billing(
    id = this[BillingTable.id],
    invoiceId = this[BillingTable.invoiceId],
    status = BillingStatus.valueOf(this[BillingTable.status]),
    chargingDate = toJavaTime(this[BillingTable.chargingDate].toLocalDate()),
    statusMessage = this[BillingTable.statusMessage]
)

fun ResultRow.toCustomer(): Customer = Customer(
    id = this[CustomerTable.id],
    currency = Currency.valueOf(this[CustomerTable.currency])
)

fun toJavaTime(time: LocalDate): java.time.LocalDate {
    return java.time.LocalDate.of(time.year, time.monthOfYear, time.dayOfMonth)
}
