package com.browntowndev.pocketcrew.domain.port.repository

interface CloudSyncPort {
    /**
     * Starts listening to cloud changes and synchronizing them to the local database.
     * This should typically be called when the user is signed in and the app is foregrounded.
     */
    fun startListening()

    /**
     * Stops listening to cloud changes.
     * This should be called when the user signs out or the app goes to the background.
     */
    fun stopListening()

    /**
     * Pushes all local unsynced data to the cloud.
     * This is useful during the initial sign-in process to merge anonymous data.
     */
    suspend fun syncAllLocalDataToCloud(): Result<Unit>
}
