/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun fetchPendingInvoices(): Flux<Invoice> {
        return Flux.create<Invoice> {
            runCatching {
                transaction(db) {
                    InvoiceTable
                        .select { InvoiceTable.status.eq(InvoiceStatus.PENDING.name) }
                        .map { it.toInvoice() }
                        .forEach { invoice -> it.next(invoice) }
                }
                it.complete()
            }.onFailure { error ->
                it.error(error)
            }
        }.publishOn(Schedulers.elastic())
    }

    fun updateInvoiceStatus(invoice: Invoice): Mono<Invoice> {
        return Mono.fromCallable<Invoice> {
            transaction(db) {
                InvoiceTable
                    .update({ InvoiceTable.id.eq(invoice.id) }) { table ->
                        table[this.value] = invoice.amount.value
                        table[this.currency] = invoice.amount.currency.toString()
                        table[this.status] = invoice.status.name
                        table[this.statusMessage] = invoice.statusMessage
                        table[this.customerId] = invoice.customerId
                    }

                fetchInvoice(invoice.id)
            }
        }.publishOn(Schedulers.elastic())
    }

    fun createInvoice(
        amount: Money,
        customer: Customer,
        status: InvoiceStatus = InvoiceStatus.PENDING,
        statusMessage: String? = null
    ): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.statusMessage] = statusMessage
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id)
    }
}
