package com.browntowndev.pocketcrew.core.ui.error

import app.cash.turbine.test
import com.browntowndev.pocketcrew.core.testing.TestClock
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ViewModelErrorHandlerImplTest {

    private lateinit var loggingPort: LoggingPort
    private lateinit var testClock: TestClock
    private lateinit var errorHandler: ViewModelErrorHandlerImpl

    @BeforeEach
    fun setup() {
        loggingPort = mockk(relaxed = true)
        testClock = TestClock()
        errorHandler = ViewModelErrorHandlerImpl(loggingPort, testClock)
    }

    @Test
    fun `handleError emits single error to flow`() = runTest {
        val tag = "TestTag"
        val message = "Test message"
        val throwable = RuntimeException("Test exception")
        val userMessage = "Something went wrong"

        errorHandler.errorEvents.test {
            errorHandler.handleError(tag, message, throwable, userMessage)
            assertEquals(userMessage, awaitItem())
        }

        verify {
            loggingPort.recordException(tag, message, throwable)
        }
    }

    @Test
    fun `handleError debounces rapid errors`() = runTest {
        val tag = "TestTag"
        val message = "Test message"
        val throwable = RuntimeException("Test exception")
        val userMessage = "Something went wrong"

        errorHandler.errorEvents.test {
            errorHandler.handleError(tag, message, throwable, userMessage)
            errorHandler.handleError(tag, message, throwable, userMessage)
            errorHandler.handleError(tag, message, throwable, userMessage)

            assertEquals(userMessage, awaitItem())
            expectNoEvents()
        }

        verify(exactly = 3) {
            loggingPort.recordException(tag, message, throwable)
        }
    }

    @Test
    fun `handleError shows errors after debounce interval`() = runTest {
        val tag = "TestTag"
        val message = "Test message"
        val throwable = RuntimeException("Test exception")
        val userMessage = "Something went wrong"

        errorHandler.errorEvents.test {
            // T=0
            errorHandler.handleError(tag, message, throwable, userMessage)
            assertEquals(userMessage, awaitItem())

            // T=5100
            testClock.advanceTime(5100)
            errorHandler.handleError(tag, message, throwable, userMessage)
            assertEquals(userMessage, awaitItem())
        }
    }

    @Test
    fun `handleError ignores CancellationException`() = runTest {
        val tag = "TestTag"
        val message = "Test message"
        val throwable = CancellationException("Cancelled")

        errorHandler.errorEvents.test {
            errorHandler.handleError(tag, message, throwable)
            expectNoEvents()
        }

        verify(exactly = 0) {
            loggingPort.recordException(any(), any(), any())
        }
    }
}
