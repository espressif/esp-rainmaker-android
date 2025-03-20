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
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

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

        fun extractActiveTimestamp(dataset: ByteArray, tlvTag: Int = 0x0E): Long {

            var index = 0
            while (index < dataset.size) {
                val tlvType = dataset[index].toInt() and 0xFF
                index++

                if (index >= dataset.size) break

                val length = dataset[index].toInt() and 0xFF
                index++

                if (index + length > dataset.size) break

                if (tlvType == tlvTag && length == 8) { // 0x0E = Active Timestamp TLV
                    return dataset.copyOfRange(index, index + 8)
                        .fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
                }

                index += length
            }
            return -1 // Return null if no active timestamp is found
        }

        fun updateActiveTimestamp(data: ByteArray, increment: Long): ByteArray {

            var index = 0
            while (index < data.size) {
                val tlvType = data[index].toInt() and 0xFF
                index++

                if (index >= data.size) break

                val length = data[index].toInt() and 0xFF
                index++

                if (index + length > data.size) break

                if (tlvType == 0x0E && length == 8) { // Active Timestamp TLV

                    // Extract current timestamp
                    val currentTimestamp = extractActiveTimestamp(data)

                    // Increment timestamp
                    val newTimestamp = currentTimestamp + increment

                    // Convert updated timestamp to byte array (big-endian)
                    val updatedBytes = newTimestamp.toByteArrayBigEndian()

                    // Replace old timestamp with new one
                    for (i in updatedBytes.indices) {
                        data[index + i] = updatedBytes[i]
                    }

                    // Convert back to Base64
                    return data
                }

                index += length
            }
            return data // Return original dataset if timestamp is not found
        }

        fun addDelayTimer(dataset: ByteArray, delay: Long): ByteArray {
            val delayBigEndian = ByteBuffer.allocate(4).putInt(delay.toInt())
                .array() // Convert delay to big-endian 4 bytes
            val delayTimerTag = byteArrayOf(0x34)
            val length = byteArrayOf(4) // Length of Delay Timer (4 bytes for UInt32)

            return dataset + delayTimerTag + length + delayBigEndian
        }

        // Extension function to convert Long to 8-byte big-endian ByteArray
        fun Long.toByteArrayBigEndian(): ByteArray {
            return ByteArray(8) { i -> ((this shr (8 * (7 - i))) and 0xFF).toByte() }
        }
    }
}