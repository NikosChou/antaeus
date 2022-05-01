package io.pleo.antaeus.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import setupInitialData
import java.sql.Connection

abstract class AbstractBaseTest {
    val tables = arrayOf(InvoiceTable, CustomerTable)
    lateinit var sut: AntaeusDal
    lateinit var db: Database

    @BeforeEach
    fun setUp() {
        db = Database
            .connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                user = "root",
                password = ""
            )
            .also {
                TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                transaction(it) {
                    addLogger(StdOutSqlLogger)
                    // Drop all existing tables to ensure a clean slate on each run
                    SchemaUtils.drop(*tables)
                    // Create all tables
                    SchemaUtils.create(*tables)
                }
            }
        this.sut = AntaeusDal(db)
        setupInitialData(this.sut)
    }
}