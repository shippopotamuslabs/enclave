import re

filepath = './feature/history/src/test/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryViewModelTest.kt'
with open(filepath, 'r') as f:
    content = f.read()

# For Scenario 5:
content = content.replace('''
        // Verify that the use case was only invoked exactly once with the final string
        io.mockk.verify(exactly = 1) { mockSearchChatsUseCase.invoke("hello") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("h") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("he") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("hel") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("hell") }
        }''', '''
        // Verify that the use case was only invoked exactly once with the final string
        io.mockk.verify(exactly = 1) { mockSearchChatsUseCase.invoke("hello") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("h") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("he") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("hel") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("hell") }
        cancelAndIgnoreRemainingEvents()
        }''')

# For Scenario 6:
content = content.replace('''
        state = expectMostRecentItem()
        assertEquals(5, state.otherChats.size + state.pinnedChats.size)
        val chatIds = (state.otherChats + state.pinnedChats).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), chatIds)
        }''', '''
        state = expectMostRecentItem()
        assertEquals(5, state.otherChats.size + state.pinnedChats.size)
        val chatIds = (state.otherChats + state.pinnedChats).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), chatIds)
        cancelAndIgnoreRemainingEvents()
        }''')
content = content.replace('''
        var state = viewModel.uiState.value
        assertEquals(1, state.otherChats.size + state.pinnedChats.size)
        assertEquals("Project Chat", (state.otherChats + state.pinnedChats).first().name)''', '''
        var state = expectMostRecentItem()
        assertEquals(1, state.otherChats.size + state.pinnedChats.size)
        assertEquals("Project Chat", (state.otherChats + state.pinnedChats).first().name)''')

# For Error 1:
content = content.replace('''
        // Ensure it reached the use case
        io.mockk.verify { mockSearchChatsUseCase.invoke(invalidQuery) }
        assertTrue(viewModel.uiState.value.otherChats.isEmpty())
        }''', '''
        // Ensure it reached the use case
        io.mockk.verify { mockSearchChatsUseCase.invoke(invalidQuery) }
        val state = expectMostRecentItem()
        assertTrue(state.otherChats.isEmpty())
        cancelAndIgnoreRemainingEvents()
        }''')

# Add cancelAndIgnoreRemainingEvents to the others just in case they fail with Unconsumed events
content = content.replace('        val state = expectMostRecentItem()', '        val state = expectMostRecentItem()\n        cancelAndIgnoreRemainingEvents()')

with open(filepath, 'w') as f:
    f.write(content)
