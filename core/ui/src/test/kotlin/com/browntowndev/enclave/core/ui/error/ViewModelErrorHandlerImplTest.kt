package com.browntowndev.enclave.core.ui.error

import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelErrorHandlerImplTest {
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var testClock: TestClock
    private lateinit var errorHandler: ViewModelErrorHandlerImpl

    @BeforeEach
    fun setup() {
        loggingPort = FakeLoggingPort()
        testClock = TestClock()
        errorHandler = ViewModelErrorHandlerImpl(loggingPort, testClock)
    }

    @Test
    fun `handleError emits single error to flow`() =
        runTest {
            val tag = "TestTag"
            val message = "Test message"
            val throwable = RuntimeException("Test exception")
            val userMessage = "Something went wrong"

            errorHandler.errorEvents.test {
                errorHandler.handleError(tag, message, throwable, userMessage)
                assertEquals(userMessage, awaitItem())
            }

            assertEquals(
                listOf(FakeLoggingPort.ErrorCall(tag, message, throwable)),
                loggingPort.errorCalls,
            )
        }

    @Test
    fun `handleError debounces rapid errors`() =
        runTest {
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

            assertEquals(
                List(3) { FakeLoggingPort.ErrorCall(tag, message, throwable) },
                loggingPort.errorCalls,
            )
        }

    @Test
    fun `handleError shows errors after debounce interval`() =
        runTest {
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
    fun `coroutineExceptionHandler emits single error when launched coroutine fails`() =
        runTest {
            val tag = "TestTag"
            val message = "Coroutine failed"
            val throwable = RuntimeException("Coroutine exception")
            val userMessage = "Something went wrong"

            errorHandler.errorEvents.test {
                val handler =
                    errorHandler.coroutineExceptionHandler(
                        tag = tag,
                        message = message,
                        userMessage = userMessage,
                    )
                // Root scope mirrors viewModelScope.launch(handler) — handler consumes the failure.
                CoroutineScope(handler).launch {
                    throw throwable
                }
                advanceUntilIdle()

                assertEquals(userMessage, awaitItem())
                expectNoEvents()
            }

            assertEquals(
                listOf(FakeLoggingPort.ErrorCall(tag, message, throwable)),
                loggingPort.errorCalls,
            )
        }

    @Test
    fun `handleError ignores CancellationException`() =
        runTest {
            val tag = "TestTag"
            val message = "Test message"
            val throwable = CancellationException("Cancelled")

            errorHandler.errorEvents.test {
                errorHandler.handleError(tag, message, throwable)
                expectNoEvents()
            }

            assertEquals(emptyList<FakeLoggingPort.ErrorCall>(), loggingPort.errorCalls)
        }
}
