package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class BillingService(
    private val dal: AntaeusDal, private val paymentProvider: PaymentProvider
) {
    private val logger = KotlinLogging.logger { BillingService::class.java.name }

    fun scheduleTransactionsManual(): Flux<Invoice> {
        return this.dal.fetchPendingInvoices()
            .flatMap(this::markInvoiceInProgress, 10)
            .flatMap(this::sendPayment)
            .flatMap(this.dal::updateInvoiceStatus)
    }

    private fun markInvoiceInProgress(invoice: Invoice): Mono<Invoice> {
        return this.dal.updateInvoiceStatus(invoice.copy(status = InvoiceStatus.IN_PROGRESS))
    }

    private fun sendPayment(invoice: Invoice): Mono<Invoice> =
        Mono.fromCallable { paymentProvider.charge(invoice) }.map {
            if (it) {
                logger.info { "Payment completed successful, invoiceId: ${invoice.id}" }
                invoice.copy(status = InvoiceStatus.PAID, statusMessage = null)
            } else {
                logger.warn { "Payment not completed, invoiceId: ${invoice.id}, account balance insufficient" }
                invoice.copy(
                    status = InvoiceStatus.FAILURE, statusMessage = "account balance did not allow the charge"
                )
            }
        }
            .retry(1) { it is NetworkException }
            .onErrorResume(Exception::class.java) { copyFailureInvoice(invoice, it.message) }

    private fun copyFailureInvoice(invoice: Invoice, message: String?) = Mono.fromCallable {
        logger.error { "Error occurs for invoiceId: ${invoice.id}, message: $message" }
        invoice.copy(
            status = InvoiceStatus.FAILURE, statusMessage = message
        )
    }
}
