package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseModality
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.ParseCorrectionsTable
import com.vstorchevyi.skilky.support.newRepoFixture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ParseCorrectionsRepositoryIntegrationTest {
    private lateinit var factory: DatabaseFactory

    @BeforeTest
    fun setUp() {
        factory = newRepoFixture()
    }

    @AfterTest
    fun tearDown() {
        factory.close()
    }

    @Test
    fun `insert stores the modality, currency, and both JSON payloads`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = registerUser()

            // Act
            val id =
                sut.insert(
                    userId = userId,
                    modality = ParseModality.TEXT,
                    currency = Currency.UAH,
                    itemsOriginalJson = """[{"name":"milk","amount":45.0}]""",
                    itemsFinalJson = """[{"name":"milk","amount":40.0}]""",
                )

            // Assert
            id shouldNotBe 0L
            val row =
                factory.dbQuery {
                    ParseCorrectionsTable
                        .selectAll()
                        .where { ParseCorrectionsTable.id eq id }
                        .single()
                }
            row[ParseCorrectionsTable.userId].value shouldBe userId
            row[ParseCorrectionsTable.modality] shouldBe "TEXT"
            row[ParseCorrectionsTable.currency] shouldBe "UAH"
            row[ParseCorrectionsTable.itemsOriginal] shouldBe """[{"name":"milk","amount":45.0}]"""
            row[ParseCorrectionsTable.itemsFinal] shouldBe """[{"name":"milk","amount":40.0}]"""
        }
    }

    @Test
    fun `insert allows an empty final payload`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = registerUser()

            // Act
            val id =
                sut.insert(
                    userId = userId,
                    modality = ParseModality.AUDIO,
                    currency = Currency.USD,
                    itemsOriginalJson = """[{"name":"x"}]""",
                    itemsFinalJson = "[]",
                )

            // Assert
            val storedFinal =
                factory.dbQuery {
                    ParseCorrectionsTable
                        .selectAll()
                        .where { ParseCorrectionsTable.id eq id }
                        .single()[ParseCorrectionsTable.itemsFinal]
                }
            storedFinal shouldBe "[]"
        }
    }

    private suspend fun registerUser(): Long = UserRepository(factory).create("a@b.c", "h", "A").id

    private fun createSut() = ParseCorrectionsRepository(factory)
}
