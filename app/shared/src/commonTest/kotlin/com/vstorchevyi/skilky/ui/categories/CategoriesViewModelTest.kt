package com.vstorchevyi.skilky.ui.categories

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.FakeCategoryRepository
import com.vstorchevyi.skilky.domain.usecase.CreateCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.DeleteCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.ObserveCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.UpdateCategoryUseCase
import com.vstorchevyi.skilky.support.runTestWithMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
    @Test
    fun `state reflects the latest list emitted by the repository`() =
        runTestWithMain {
            // Arrange
            val repository = FakeCategoryRepository(initial = listOf(aDefaultCategory()))
            val sut = createSut(repository = repository)

            // Act
            advanceUntilIdle()

            // Assert
            assertEquals(1, sut.state.value.categories.size)
            assertEquals("Food", sut.state.value.categories.single().name)
        }

    @Test
    fun `refresh sets isRefreshing then clears it`() =
        runTestWithMain {
            // Arrange
            val sut = createSut(repository = FakeCategoryRepository())
            advanceUntilIdle()

            // Act
            sut.onRefresh()
            advanceUntilIdle()

            // Assert
            assertEquals(false, sut.state.value.isRefreshing)
        }

    @Test
    fun `refresh failure emits a ShowError event`() =
        runTestWithMain {
            // Arrange
            val repository =
                FakeCategoryRepository().apply { refreshResult = Either.Left(AppError.Network) }
            val sut = createSut(repository = repository)

            // Act
            sut.onRefresh()
            advanceUntilIdle()

            // Assert
            val event = sut.events.first()
            assertEquals(CategoriesEvent.ShowError(AppError.Network), event)
        }

    @Test
    fun `onAdd opens an empty draft and onDismissDialog closes it`() =
        runTestWithMain {
            // Arrange
            val sut = createSut()

            // Act
            sut.onAdd()
            val opened = sut.state.value.editing
            sut.onDismissDialog()

            // Assert
            assertEquals(EditingCategory(), opened)
            assertNull(sut.state.value.editing)
        }

    @Test
    fun `onEdit ignores a default category`() =
        runTestWithMain {
            // Arrange
            val sut = createSut()

            // Act
            sut.onEdit(aDefaultCategory())

            // Assert
            assertNull(sut.state.value.editing)
        }

    @Test
    fun `onEdit populates the draft from a user category`() =
        runTestWithMain {
            // Arrange
            val sut = createSut()
            val custom = aCustomCategory(id = 5, name = "Coffee", icon = "☕", color = "#8B4513")

            // Act
            sut.onEdit(custom)

            // Assert
            assertEquals(
                EditingCategory(
                    id = 5,
                    name = "Coffee",
                    icon = "☕",
                    color = "#8B4513",
                    originalIsDefault = false,
                ),
                sut.state.value.editing,
            )
        }

    @Test
    fun `onSave on a new draft calls create with trimmed inputs and closes the dialog`() =
        runTestWithMain {
            // Arrange
            val repository =
                FakeCategoryRepository().apply {
                    createResult = Either.Right(aCustomCategory(id = 9, name = "Coffee"))
                }
            val sut = createSut(repository = repository)
            sut.onAdd()
            sut.onDraftNameChange("  Coffee  ")
            sut.onDraftIconChange("☕")
            sut.onDraftColorChange("#8B4513")

            // Act
            sut.onSave()
            advanceUntilIdle()

            // Assert
            val call = repository.calls.last() as FakeCategoryRepository.Call.Create
            assertEquals("Coffee", call.name)
            assertEquals("☕", call.icon)
            assertEquals("#8B4513", call.color)
            assertNull(sut.state.value.editing)
        }

    @Test
    fun `onSave on an existing draft calls update`() =
        runTestWithMain {
            // Arrange
            val repository =
                FakeCategoryRepository(initial = listOf(aCustomCategory(id = 5))).apply {
                    updateResult = Either.Right(aCustomCategory(id = 5, name = "Updated"))
                }
            val sut = createSut(repository = repository)
            advanceUntilIdle()
            sut.onEdit(aCustomCategory(id = 5, name = "Old"))
            sut.onDraftNameChange("Updated")

            // Act
            sut.onSave()
            advanceUntilIdle()

            // Assert
            val call = repository.calls.last() as FakeCategoryRepository.Call.Update
            assertEquals(5L, call.id)
            assertEquals("Updated", call.name)
        }

    @Test
    fun `save failure leaves the dialog open and emits ShowError`() =
        runTestWithMain {
            // Arrange
            val repository =
                FakeCategoryRepository().apply { createResult = Either.Left(AppError.Conflict) }
            val sut = createSut(repository = repository)
            sut.onAdd()
            sut.onDraftNameChange("Taken")
            sut.onDraftIconChange("X")
            sut.onDraftColorChange("#000")

            // Act
            sut.onSave()
            advanceUntilIdle()

            // Assert
            assertEquals("Taken", sut.state.value.editing?.name)
            val event = sut.events.first()
            assertEquals(CategoriesEvent.ShowError(AppError.Conflict), event)
        }

    @Test
    fun `onDelete on a default category is a no-op`() =
        runTestWithMain {
            // Arrange
            val repository = FakeCategoryRepository()
            val sut = createSut(repository = repository)

            // Act
            sut.onDelete(aDefaultCategory())
            advanceUntilIdle()

            // Assert: the only recorded call comes from the init refresh.
            assertTrue(repository.calls.none { it is FakeCategoryRepository.Call.Delete })
        }

    @Test
    fun `onDelete on a custom category forwards to the repository`() =
        runTestWithMain {
            // Arrange
            val repository = FakeCategoryRepository(initial = listOf(aCustomCategory(id = 9)))
            val sut = createSut(repository = repository)
            advanceUntilIdle()

            // Act
            sut.onDelete(aCustomCategory(id = 9))
            advanceUntilIdle()

            // Assert
            assertTrue(repository.calls.any { it is FakeCategoryRepository.Call.Delete && it.id == 9L })
        }

    private fun createSut(repository: FakeCategoryRepository = FakeCategoryRepository()): CategoriesViewModel =
        CategoriesViewModel(
            observeCategories = ObserveCategoriesUseCase(repository),
            refreshCategories = RefreshCategoriesUseCase(repository),
            createCategory = CreateCategoryUseCase(repository),
            updateCategory = UpdateCategoryUseCase(repository),
            deleteCategory = DeleteCategoryUseCase(repository),
        )

    private fun aDefaultCategory(): Category =
        Category(
            id = 1,
            name = "Food",
            icon = "🍎",
            color = "#FF6B6B",
            isDefault = true,
            nameKey = "food",
        )

    private fun aCustomCategory(
        id: Long,
        name: String = "Custom",
        icon: String = "📁",
        color: String = "#888888",
    ): Category = Category(id = id, name = name, icon = icon, color = color, isDefault = false)
}
