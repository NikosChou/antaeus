package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Billing
import io.pleo.antaeus.models.BillingStatus
import io.pleo.antaeus.models.Invoice
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Year
import java.time.YearMonth

class BillingService(
    private val dal: AntaeusDal, private val paymentProvider: PaymentProvider
) {
    private val logger = KotlinLogging.logger { BillingService::class.java.name }

    fun fetchBillingsForMonth(year: Int = Year.now().value, month: Int = YearMonth.now().monthValue): Flux<Billing> {
        return this.dal.fetchBillingsByBillingDate(YearMonth.of(year, month))
    }

    fun scheduleTransactionsManual(): Flux<Billing> {
        return this.dal.fetchPendingInvoices()
            .flatMap(this::createBilling, 10)
            .flatMap(this::sendPayment)
            .flatMap(this.dal::updateBilling)
    }

    private fun createBilling(invoice: Invoice): Mono<Pair<Invoice, Billing>> {
        return this.dal.createBilling(invoice).map { Pair(invoice, it) }
    }

    private fun sendPayment(pair: Pair<Invoice, Billing>): Mono<Billing> =
        Mono.fromCallable { paymentProvider.charge(pair.first) }.map {
            val billing = pair.second
            if (it) {
                logger.info { "Payment completed successful, invoiceId: ${billing.invoiceId}" }
                billing.copy(status = BillingStatus.SUCCESSFUL)
            } else {
                logger.warn { "Payment not completed, invoiceId: ${billing.invoiceId}, account balance insufficient" }
                billing.copy(
                    status = BillingStatus.FAILURE, statusMessage = "account balance did not allow the charge"
                )
            }
        }
            .retry(1) { it is NetworkException }
            .onErrorResume(Exception::class.java) { copyFailureInvoice(pair.second, it.message) }

    private fun copyFailureInvoice(billing: Billing, message: String?): Mono<Billing> = Mono.fromCallable {
        logger.error { "Error occurs for invoiceId: ${billing.invoiceId}, message: $message" }
        billing.copy(
            status = BillingStatus.FAILURE, statusMessage = message
        )
    }
}
