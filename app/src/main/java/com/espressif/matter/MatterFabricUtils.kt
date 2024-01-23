// Copyright 2024 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif.matter

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

class MatterFabricUtils {

    companion object {

        fun generateKeypair(fabricId: String): KeyPair? {

            val keyPairGenerator: KeyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
            )

            val keySpecBuilder = fabricId.let {
                KeyGenParameterSpec.Builder(
                    it,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).setDigests(
                    KeyProperties.DIGEST_SHA256,
                )
            }
            keyPairGenerator.initialize(keySpecBuilder.build())
            return keyPairGenerator.generateKeyPair()
        }

        fun decode(cert: String?): X509Certificate {
            val encodedCert: ByteArray = Base64.getDecoder().decode(cert)
            val inputStream = ByteArrayInputStream(encodedCert)
            val certFactory = CertificateFactory.getInstance("X.509")
            return certFactory.generateCertificate(inputStream) as X509Certificate
        }
    }
}