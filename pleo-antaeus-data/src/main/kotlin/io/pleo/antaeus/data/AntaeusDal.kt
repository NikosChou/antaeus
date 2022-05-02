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
            InvoiceTable.select { InvoiceTable.id.eq(id) }.firstOrNull()?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable.selectAll().map { it.toInvoice() }
        }
    }

    fun fetchPendingInvoices(): Flux<Invoice> {
        return Flux.create<Invoice> {
            runCatching {
                transaction(db) {
                    InvoiceTable.select { InvoiceTable.status.eq(InvoiceStatus.PENDING.name) }.map { it.toInvoice() }
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
                InvoiceTable.update({ InvoiceTable.id.eq(invoice.id) }) { table ->
                    table[this.value] = invoice.amount.value
                    table[this.currency] = invoice.amount.currency.toString()
                    table[this.status] = invoice.status.name
                    table[this.customerId] = invoice.customerId
                }

                fetchInvoice(invoice.id)
            }
        }.publishOn(Schedulers.elastic())
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable.insert {
                it[this.value] = amount.value
                it[this.currency] = amount.currency.toString()
                it[this.status] = status.toString()
                it[this.customerId] = customer.id
            } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun fetchBilling(id: Int): Mono<Billing> {
        return Mono.defer {
            transaction(db) {
                // Returns the first invoice with matching id.
                BillingTable.select { BillingTable.id.eq(id) }.firstOrNull()?.toBilling()
            }?.let { billing -> Mono.just(billing) } ?: Mono.empty()

        }
    }


    fun fetchBillings(): Flux<Billing> {
        return Flux.create { sink ->
            transaction(db) {
                BillingTable.selectAll().map { it.toBilling() }
            }.forEach { sink.next(it) }
            sink.complete()
        }
    }

    fun createBilling(
        invoice: Invoice, status: BillingStatus = BillingStatus.IN_PROGRESS, statusMessage: String? = null
    ): Mono<Billing> {
        return Mono.fromCallable {
            transaction(db) {
                // Insert the invoice and returns its new id.
                BillingTable.insertIgnore {
                    it[this.invoiceId] = invoice.id
                    it[this.status] = status.toString()
                    it[this.statusMessage] = statusMessage
                } get BillingTable.id
            }
        }.flatMap { fetchBilling(it) }
    }

    fun updateBilling(billing: Billing): Mono<Billing> {
        val invoiceStatus =
            if (billing.status == BillingStatus.SUCCESSFUL) InvoiceStatus.PAID else InvoiceStatus.PENDING
        return Mono.fromCallable {
            transaction(db) {
                BillingTable.update({ BillingTable.id.eq(billing.id) }) {
                    it[this.status] = billing.status.name
                    it[this.statusMessage] = billing.statusMessage
                }
            }
        }
            .map { fetchInvoice(billing.invoiceId)!! }
            .flatMap { updateInvoiceStatus(it.copy(status = invoiceStatus)) }
            .flatMap { fetchBilling(billing.id) }
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable.select { CustomerTable.id.eq(id) }.firstOrNull()?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable.selectAll().map { it.toCustomer() }
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
