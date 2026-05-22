package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudioSyncDaos
    @Inject
    constructor(
        val studioMediaDao: StudioMediaDao,
        val studioAlbumDao: StudioAlbumDao,
    )
