package io.pleo.antaeus.data

import io.pleo.antaeus.models.InvoiceStatus
import org.assertj.core.api.Assertions.assertThat

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

class AntaeusDalTest : AbstractBaseTest() {

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

        this.sut.fetchPendingInvoices()
        StepVerifier
            .create(sut.fetchPendingInvoices())
            .expectErrorSatisfies { assertThat(it).hasMessageContaining("Table \"INVOICE\" not found") }
            .verify()
    }

    @Test
    fun `update invoice`() {
        val updatedInvoice = this.sut.fetchPendingInvoices()
            .take(1)
            .flatMap { this.sut.updateInvoiceStatus(it.copy(status = InvoiceStatus.IN_PROGRESS)) }

        StepVerifier
            .create(updatedInvoice)
            .expectNextMatches { it.status == InvoiceStatus.IN_PROGRESS }
            .expectComplete()
            .verify()
    }
}