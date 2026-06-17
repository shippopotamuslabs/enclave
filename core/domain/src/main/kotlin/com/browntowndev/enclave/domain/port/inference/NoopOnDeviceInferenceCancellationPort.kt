package com.browntowndev.enclave.domain.port.inference

object NoopOnDeviceInferenceCancellationPort : OnDeviceInferenceCancellationPort {
    override fun cancelActiveGeneration() = Unit
}
