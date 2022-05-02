/*
    Defines database tables and their schemas.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import org.jetbrains.exposed.sql.Table
import org.joda.time.LocalDate

object InvoiceTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
    val value = decimal("value", 1000, 2)
    val customerId = reference("customer_id", CustomerTable.id)
    val status = text("status")
    val statusMessage = text("status_message").nullable()
}

object BillingTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val invoiceId = reference("invoice_id", InvoiceTable.id).uniqueIndex()
    val status = text("status")
    val statusMessage = text("status_message").nullable()
    val chargingDate = date("charging_date").default(LocalDate.now().toDateTimeAtStartOfDay())
}

object CustomerTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
}
