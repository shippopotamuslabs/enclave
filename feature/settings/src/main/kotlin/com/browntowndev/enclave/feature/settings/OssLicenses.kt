@file:Suppress("StringLiteralDuplication", "MaxLineLength")

package com.browntowndev.enclave.feature.settings

internal object OssLicenses {
    val entries: List<OssLicenseEntry> =
        listOf(
            OssLicenseEntry(
                name = "Android Jetpack Activity",
                version = "1.13.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Biometric",
                version = "1.2.0-alpha05",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Compose",
                version = "BOM 2026.05.00",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Core KTX",
                version = "1.18.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Core SplashScreen",
                version = "1.2.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Credentials",
                version = "1.7.0-alpha02",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack DataStore Preferences",
                version = "1.2.1",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Hilt Navigation Compose",
                version = "1.3.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Hilt Work",
                version = "1.3.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Lifecycle",
                version = "2.10.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Media3",
                version = "1.10.1",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Navigation Compose",
                version = "2.9.8",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack Room",
                version = "2.8.4",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Android Jetpack WorkManager",
                version = "2.11.2",
                licenseType = "Apache 2.0",
                copyrightHolder = "The Android Open Source Project",
                licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Anthropic Java SDK",
                version = "2.31.0",
                licenseType = "MIT",
                copyrightHolder = "Anthropic",
                licenseText = "MIT License\n\nCopyright (c) Anthropic\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.",
            ),
            OssLicenseEntry(
                name = "Coil 3",
                version = "3.4.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "Coil Contributors",
                licenseText = "Copyright 2024 Coil Contributors\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Model: KoalaAI Text-Moderation",
                version = "INT8 ONNX",
                licenseType = "Apache 2.0",
                copyrightHolder = "KoalaAI",
                licenseText =
                    """
                    Copyright KoalaAI contributors

                    Licensed under the Apache License, Version 2.0.
                    DeBERTa-based text-safety classifier for response moderation (sexual, violence,
                    hate categories). ONNX weights are downloaded on demand from R2; only the
                    tokenizer/config ship in APK assets.
                    https://huggingface.co/KoalaAI/Text-Moderation
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Falconsai NSFW Image Detection",
                version = "quantized ONNX",
                licenseType = "Apache 2.0",
                copyrightHolder = "Falconsai",
                licenseText =
                    """
                    Copyright Falconsai contributors

                    Licensed under the Apache License, Version 2.0.
                    ViT-based image-safety classifier for generated-image moderation. ONNX weights
                    are downloaded on demand from R2; only the preprocessing config ships in APK assets.
                    https://huggingface.co/Falconsai/nsfw_image_detection
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "CommonMark Java",
                version = "0.28.0",
                licenseType = "BSD 2-Clause",
                copyrightHolder = "Atlassian Pty Ltd",
                licenseText = "BSD 2-Clause License\n\nCopyright (c) 2015, Atlassian Pty Ltd. All rights reserved.\n\nRedistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:\n\n1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.\n2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.",
            ),
            OssLicenseEntry(
                name = "Firebase Android SDK",
                version = "BOM 34.13.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "Google LLC",
                licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Google GenAI SDK",
                version = "1.53.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "Google LLC",
                licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Google Identity (googleid)",
                version = "1.2.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "Google LLC",
                licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "gRPC",
                version = "1.81.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "gRPC Authors",
                licenseText = "Copyright 2014 gRPC Authors. All Rights Reserved.\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Haze",
                version = "1.7.2",
                licenseType = "Apache 2.0",
                copyrightHolder = "Chris Banes",
                licenseText = "Copyright 2023 Chris Banes\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Hilt (Dagger)",
                version = "2.59.2",
                licenseType = "Apache 2.0",
                copyrightHolder = "Google LLC",
                licenseText = "Copyright 2020 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "javax.inject",
                version = "1",
                licenseType = "Apache 2.0",
                copyrightHolder = "javax.inject Contributors",
                licenseText = "Licensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "JNA (Java Native Access)",
                version = "5.18.1",
                licenseType = "Apache 2.0 (from dual LGPL-2.1 OR Apache-2.0)",
                copyrightHolder = "Timothy Wall and JNA Contributors",
                licenseText = "Copyright (c) Timothy Wall and JNA Contributors.\n\nLicensed under the Apache License, Version 2.0 (chosen from dual LGPL-2.1 OR Apache-2.0 license).",
            ),
            OssLicenseEntry(
                name = "jtokkit",
                version = "1.1.0",
                licenseType = "MIT",
                copyrightHolder = "Knuddels GmbH & Co. KG",
                licenseText = "MIT License\n\nCopyright (c) 2023 Knuddels GmbH & Co. KG\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.",
            ),
            OssLicenseEntry(
                name = "Kotlin",
                version = "2.3.21",
                licenseType = "Apache 2.0",
                copyrightHolder = "JetBrains s.r.o.",
                licenseText = "Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "llama.cpp",
                version = "vendor",
                licenseType = "MIT",
                copyrightHolder = "Georgi Gerganov and llama.cpp contributors",
                licenseText =
                    """
                    MIT License

                    Copyright (c) 2023-2024 Georgi Gerganov and contributors

                    Used for on-device GGUF language-model inference (llama-android).
                    https://github.com/ggerganov/llama.cpp
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Enclave diffusion engine",
                version = "1.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "BrownTown Dev (Enclave)",
                licenseText =
                    """
                    Apache License, Version 2.0

                    Copyright BrownTown Dev and Enclave contributors

                    In-process Stable Diffusion txt2img/img2img engine for Qualcomm QNN packs and
                    MNN CLIP conditioning. Replaces the former Local Dream fork/HTTP backend.

                    Licensed under the Apache License, Version 2.0.
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "kotlinx-serialization",
                version = "1.11.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "JetBrains s.r.o.",
                licenseText = "Copyright 2017-2024 JetBrains s.r.o. and contributors.\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "LiteRT (TFLite)",
                version = "0.11.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "Google LLC",
                licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Model: Gemma 4 E4B",
                version = "litert-lm",
                licenseType = "Gemma Terms of Use",
                copyrightHolder = "Google LLC",
                licenseText =
                    """
                    Gemma is provided under the Gemma Terms of Use.

                    Bundled for on-device chat inference when assigned in Model Configuration.
                    https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
                    https://ai.google.dev/gemma/terms
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Jamba Reasoning 3B",
                version = "Q8_0",
                licenseType = "Jamba Open Model License",
                copyrightHolder = "AI21 Labs",
                licenseText =
                    """
                    Provided under the Jamba Open Model License by AI21 Labs.

                    Bundled GGUF weights for on-device drafting/synthesis when assigned.
                    https://huggingface.co/bartowski/ai21labs_AI21-Jamba-Reasoning-3B-GGUF
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: MiniCPM-V-4.6",
                version = "Q8_0",
                licenseType = "Apache 2.0",
                copyrightHolder = "OpenBMB",
                licenseText =
                    """
                    Copyright OpenBMB contributors

                    Licensed under the Apache License, Version 2.0.
                    Bundled multimodal GGUF weights for on-device inference when assigned.
                    https://huggingface.co/openbmb/MiniCPM-V-4.6-Thinking-gguf
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Qwen 3.5 2B",
                version = "Q8_0",
                licenseType = "Apache 2.0",
                copyrightHolder = "Alibaba Cloud",
                licenseText =
                    """
                    Copyright Alibaba Cloud and contributors

                    Licensed under the Apache License, Version 2.0 (per upstream model card).
                    Bundled GGUF weights for on-device inference when assigned.
                    https://huggingface.co/Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Sentence Transformers (all-MiniLM-L6-v2)",
                version = "ONNX",
                licenseType = "Apache 2.0",
                copyrightHolder = "UKPLab",
                licenseText =
                    """
                    Copyright UKPLab and sentence-transformers contributors

                    Licensed under the Apache License, Version 2.0.
                    Bundled ONNX embedding model for on-device memory/search features.
                    https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Stable Diffusion 1.5",
                version = "Q4_0 GGUF",
                licenseType = "CreativeML Open RAIL-M",
                copyrightHolder = "Runway ML / Stability AI community",
                licenseText =
                    """
                    Stable Diffusion v1.5 is licensed under the CreativeML Open RAIL-M license.
                    Use is subject to the model card restrictions (research and responsible use).

                    Bundled for local text-to-image when the SD 1.5 pack is installed and assigned.
                    https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Stable Diffusion 1.5 NPU pack",
                version = "QNN 2.28 / 8 Gen 2",
                licenseType = "Upstream model terms",
                copyrightHolder = "xororz / Anything V5 derivative",
                licenseText =
                    """
                    QNN-converted weights distributed via xororz/sd-qnn (AnythingV5 derivative).
                    Subject to the base Stable Diffusion 1.5 license and upstream model-card terms.

                    Used with the Enclave diffusion engine for Hexagon NPU inference on supported devices.
                    https://huggingface.co/xororz/sd-qnn
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Stable Diffusion XL NPU pack",
                version = "QNN 2.28 / 8 Gen 3",
                licenseType = "Open RAIL++-M (SDXL base)",
                copyrightHolder = "xororz / RealVis XL derivative",
                licenseText =
                    """
                    QNN-converted SDXL weights (RealVis XL V5 DMD2) distributed via xororz/sdxl-qnn.
                    SDXL base models use the Open RAIL++-M license; derivative checkpoints may add terms.

                    Used with the Enclave diffusion engine for Hexagon NPU inference on Gen 3+ devices.
                    https://huggingface.co/xororz/sdxl-qnn
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Model: Whisper (ggml-base.en)",
                version = "base.en",
                licenseType = "MIT (code) / model terms",
                copyrightHolder = "OpenAI / ggerganov",
                licenseText =
                    """
                    whisper.cpp is MIT-licensed. Whisper model weights are distributed separately;
                    use is subject to the upstream model terms on Hugging Face.

                    Bundled for on-device speech-to-text utility features.
                    https://huggingface.co/ggerganov/whisper.cpp
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "markdown-parser & markdown-renderer",
                version = "1.2.7",
                licenseType = "Apache 2.0",
                copyrightHolder = "huarangmeng",
                licenseText = "Copyright huarangmeng\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "OkHttp",
                version = "5.3.2",
                licenseType = "Apache 2.0",
                copyrightHolder = "Square, Inc.",
                licenseText = "Copyright 2019 Square, Inc.\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "OpenAI Java SDK",
                version = "4.35.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "OpenAI",
                licenseText = "Copyright OpenAI\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "Qualcomm AI Runtime (QNN)",
                version = "QAIRT (dev extract)",
                licenseType = "Proprietary (Qualcomm EULA)",
                copyrightHolder = "Qualcomm Technologies, Inc.",
                licenseText =
                    """
                    Qualcomm AI Runtime (QNN) libraries (libQnn*.so, Hexagon skeleton libraries, etc.)
                    are proprietary and redistributable only under Qualcomm's license terms.

                    Debug builds may bundle QNN libraries extracted from a third-party APK for
                    development convenience. Release builds must use libraries synced from an
                    authorized QAIRT install after accepting Qualcomm's EULA:
                    https://softwarecenter.qualcomm.com/

                    Run ./scripts/sync-qnn-jniLibs.sh with qnn.sdk.dir set in local.properties.
                    Redistribution is limited to Snapdragon-targeted applications per Qualcomm terms.
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "stb_image_write",
                version = "1.16",
                licenseType = "Public Domain",
                copyrightHolder = "Sean Barrett",
                licenseText =
                    """
                    stb_image_write - public domain

                    Copyright Sean Barrett (2010-2015). No warranty implied; use at your own risk.
                    Vendored in local-image-gen-android for PNG output from generated images.
                    http://nothings.org/stb
                    """.trimIndent(),
            ),
            OssLicenseEntry(
                name = "Telephoto",
                version = "0.19.0",
                licenseType = "Apache 2.0",
                copyrightHolder = "Saket Narayan",
                licenseText = "Copyright 2023 Saket Narayan\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "uniffi-kotlin-runtime",
                version = "1.0.10",
                licenseType = "Apache 2.0",
                copyrightHolder = "Ubique Innovation AG",
                licenseText = "Copyright Ubique Innovation AG\n\nLicensed under the Apache License, Version 2.0.",
            ),
            OssLicenseEntry(
                name = "whisper.cpp",
                version = "vendor",
                licenseType = "MIT",
                copyrightHolder = "Georgi Gerganov and whisper.cpp contributors",
                licenseText =
                    """
                    MIT License

                    Copyright (c) 2023 Georgi Gerganov and contributors

                    Used for on-device speech-to-text inference (whisper-android).
                    https://github.com/ggerganov/whisper.cpp
                    """.trimIndent(),
            ),
        ).sortedBy { it.name.lowercase() }
}
