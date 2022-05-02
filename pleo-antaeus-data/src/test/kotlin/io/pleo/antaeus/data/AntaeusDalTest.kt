package io.pleo.antaeus.data

import io.pleo.antaeus.models.BillingStatus
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.LocalDate
import java.time.YearMonth

class AntaeusDalTest : AbstractBaseTest() {

    @Nested
    inner class InvoiceTableTest {
        @Test
        fun `fetching only pending Invoices`() {
            StepVerifier
                .create(sut.fetchPendingInvoices())
                .thenConsumeWhile { it.status == InvoiceStatus.PENDING }
                .verifyComplete()
        }

        @Test
        fun `expect error`() {
            transaction(db) { SchemaUtils.drop(*tables) }

            sut.fetchPendingInvoices()
            StepVerifier
                .create(sut.fetchPendingInvoices())
                .expectErrorSatisfies { assertThat(it).hasMessageContaining("Table \"INVOICE\" not found") }
                .verify()
        }

        @Test
        fun `update invoice`() {
            val updatedInvoice = sut.fetchPendingInvoices()
                .take(1)
                .flatMap { sut.updateInvoiceStatus(it.copy(status = InvoiceStatus.PAID)) }

            StepVerifier
                .create(updatedInvoice)
                .expectNextMatches { it.status == InvoiceStatus.PAID }
                .expectComplete()
                .verify()
        }
    }

    @Nested
    inner class BillingTableTest {
        @Test
        fun `should create billing`() {
            val invoice = sut.createInvoice(Money(1.toBigDecimal(), Currency.EUR), sut.fetchCustomer(1)!!)!!
            StepVerifier
                .create(sut.createBilling(invoice))
                .expectNextMatches { it.chargingDate.isEqual(LocalDate.now()) }
                .verifyComplete()
        }

        @Test
        fun `should failed create billing if invoice id already present`() {
            val invoice = sut.fetchInvoice(1)!!
            StepVerifier
                .create(sut.createBilling(invoice))
                .expectErrorSatisfies { assertThat(it.message).containsSequence("Unique index or primary key violation") }
                .verify()
        }

        @Test
        fun `update billing`() {
            val updateFlux = sut.fetchBilling(1)
                .flatMap { sut.updateBilling(it.copy(status = BillingStatus.IN_PROGRESS, statusMessage = "test")) }


            StepVerifier
                .create(updateFlux)
                .expectNextMatches { it.status == BillingStatus.IN_PROGRESS && it.statusMessage == "test" }
                .expectComplete()
                .verify()
        }

        @Test
        fun `update billing should not update invoice status if not successful`() {
            val updateFlux = sut.fetchBilling(1)
                .flatMap { sut.updateBilling(it.copy(status = BillingStatus.IN_PROGRESS, statusMessage = "test")) }


            StepVerifier
                .create(updateFlux)
                .expectNextMatches { it.status == BillingStatus.IN_PROGRESS && it.statusMessage == "test" }
                .expectComplete()
                .verify()

            StepVerifier
                .create(sut.fetchBilling(1).map { sut.fetchInvoice(it.invoiceId)!! })
                .expectNextMatches { it.status == InvoiceStatus.PENDING }
                .expectComplete()
                .verify()
        }

        @Test
        fun `update billing should update invoice status if successful`() {
            val updateFlux = sut.fetchBilling(1)
                .flatMap { sut.updateBilling(it.copy(status = BillingStatus.SUCCESSFUL)) }

            StepVerifier
                .create(updateFlux)
                .expectNextMatches { it.status == BillingStatus.SUCCESSFUL }
                .expectComplete()
                .verify()

            StepVerifier
                .create(sut.fetchBilling(1).map { sut.fetchInvoice(it.invoiceId)!! })
                .expectNextMatches { it.status == InvoiceStatus.PAID }
                .expectComplete()
                .verify()
        }

        @Test
        fun `fetch one`() {
            StepVerifier
                .create(sut.fetchBilling(10))
                .expectNextCount(1)
                .expectComplete()
                .verify()
        }

        @Test
        fun `fetch all should be the same number like pending invoices`() {
            val count = sut.fetchInvoices().count { it.status == InvoiceStatus.PENDING }
            StepVerifier
                .create(sut.fetchBillings())
                .expectNextCount(count.toLong())
                .expectComplete()
                .verify()
        }

        @Test
        fun `fetchBillingsByBillingDate should return empty list`() {
            val currentDate = LocalDate.now()
            val previousMonth = currentDate.minusMonths(1)
            sut.fetchBillings().flatMap { sut.updateBilling(it.copy(chargingDate = previousMonth)) }.blockLast()

            StepVerifier.create(sut.fetchBillingsByBillingDate(YearMonth.from(currentDate)))
                .expectComplete()
                .verify()
        }

        @Test
        fun `fetchBillingsByBillingDate should return current months`() {
            val count = sut.fetchBillings().count().block()!!

            StepVerifier.create(sut.fetchBillingsByBillingDate(YearMonth.now()))
                .expectNextCount(count)
                .expectComplete()
                .verify()
        }
    }
}