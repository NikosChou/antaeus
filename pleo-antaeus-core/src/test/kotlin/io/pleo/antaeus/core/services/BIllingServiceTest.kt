package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDate
import java.util.stream.Stream

class BIllingServiceTest {

    private lateinit var sut: BillingService
    private lateinit var antaeusDal: AntaeusDal
    private lateinit var paymentProvider: PaymentProvider

    @BeforeEach
    fun setup() {
        antaeusDal = mockk()
        paymentProvider = mockk()
        this.sut = BillingService(dal = antaeusDal, paymentProvider = paymentProvider)
    }

    @Test
    fun `if payment succeed then status should be PAID`() {
        val invoice = Invoice(1, 1, Money(10.toBigDecimal(), Currency.EUR), InvoiceStatus.PENDING)
        val billing = Billing(1, 1, BillingStatus.IN_PROGRESS, LocalDate.now(), null)

        every { antaeusDal.fetchPendingInvoices() }.returns(Flux.just(invoice))
        every { antaeusDal.createBilling(any()) }.returns(Mono.just(billing))
        every { paymentProvider.charge(any()) }.returns(true)
        every { antaeusDal.updateBilling(any()) }.answers { Mono.just(firstArg()) }

        StepVerifier
            .create(this.sut.scheduleTransactionsManual())
            .assertNext { assertThat(it.status).isEqualTo(BillingStatus.SUCCESSFUL) }
            .verifyComplete()
    }

    @Test
    fun `if paymentProvider#charge returns false then a corresponding status and message should be available`() {
        val invoice = Invoice(1, 1, Money(10.toBigDecimal(), Currency.EUR), InvoiceStatus.PENDING)
        val billing = Billing(1, 1, BillingStatus.IN_PROGRESS, LocalDate.now(), null)

        every { antaeusDal.fetchPendingInvoices() }.returns(Flux.just(invoice))
        every { antaeusDal.createBilling(any()) }.returns(Mono.just(billing))
        every { paymentProvider.charge(any()) }.returns(false)
        every { antaeusDal.updateBilling(any()) }.answers { Mono.just(firstArg()) }

        StepVerifier
            .create(this.sut.scheduleTransactionsManual())
            .assertNext {
                assertThat(it.status).isEqualTo(BillingStatus.FAILURE)
                assertThat(it.statusMessage).isEqualTo("account balance did not allow the charge")
            }
            .verifyComplete()
    }

    @Test
    fun `if a network error happened then should try again`() {
        val invoice = Invoice(1, 1, Money(10.toBigDecimal(), Currency.EUR), InvoiceStatus.PENDING)
        val billing = Billing(1, 1, BillingStatus.IN_PROGRESS, LocalDate.now(), null)

        every { antaeusDal.fetchPendingInvoices() }.returns(Flux.just(invoice))
        every { antaeusDal.createBilling(any()) }.returns(Mono.just(billing))
        every { paymentProvider.charge(any()) }.throws(NetworkException()).andThen(true)
        every { antaeusDal.updateBilling(any()) }.answers { Mono.just(firstArg()) }

        StepVerifier
            .create(this.sut.scheduleTransactionsManual())
            .assertNext {
                assertThat(it.status).isEqualTo(BillingStatus.SUCCESSFUL)
                assertThat(it.statusMessage).isNull()
            }
            .verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("failureProvider")
    fun `if paymentProvider#charge throws Exception then a corresponding status and message should be available`(
        toThrow: Throwable,
        message: String
    ) {
        val invoice = Invoice(1, 1, Money(10.toBigDecimal(), Currency.EUR), InvoiceStatus.PENDING)
        val billing = Billing(1, 1, BillingStatus.IN_PROGRESS, LocalDate.now(), null)

        every { antaeusDal.fetchPendingInvoices() }.returns(Flux.just(invoice))
        every { antaeusDal.createBilling(any()) }.returns(Mono.just(billing))
        every { paymentProvider.charge(any()) }.throws(toThrow)
        every { antaeusDal.updateBilling(any()) }.answers { Mono.just(firstArg()) }

        StepVerifier
            .create(this.sut.scheduleTransactionsManual().take(1))
            .assertNext {
                assertThat(it.status).isEqualTo(BillingStatus.FAILURE)
                assertThat(it.statusMessage).isEqualTo(message)
            }
            .verifyComplete()
    }

    @Test
    fun `should fetch billings for specific month`() {
        val billing = Billing(1, 1, BillingStatus.IN_PROGRESS, LocalDate.now(), null)

        every { antaeusDal.fetchBillingsByBillingDate(any()) }.returns(Flux.just(billing))

        StepVerifier
            .create(this.sut.fetchBillingsForMonth(1, 1))
            .expectNextCount(1)
            .verifyComplete()
    }

    companion object {
        @JvmStatic
        fun failureProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(CustomerNotFoundException(1), "Customer '1' was not found"),
                Arguments.of(
                    CurrencyMismatchException(1, 1),
                    "Currency of invoice '1' does not match currency of customer '1'"
                ),
                Arguments.of(NetworkException(), "A network error happened please try again.")
            )
        }
    }
}