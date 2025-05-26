/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.espressif.matter

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import chip.devicecontroller.*
import chip.devicecontroller.GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback
import chip.devicecontroller.model.*
import chip.platform.*
import com.espressif.AppConstants
import com.espressif.cloudapi.ApiManager
import com.espressif.ui.Utils
import com.espressif.utils.NodeUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import matter.tlv.AnonymousTag
import matter.tlv.TlvWriter
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Class to interact with the CHIP APIs. */
class ChipClient constructor(
    @ApplicationContext private val context: Context,
    private val groupId: String,
    private val fabricId: String,
    private val rootCa: String,
    private val ipk: String,
    private val groupCatIdOperate: String
) {

    companion object {
        const val TAG = "ChipClient"
    }

    /* 0x131B is a Espressif's vendor ID, replace with your assigned company ID */
    private val VENDOR_ID = 0x131B

    private val DEFAULT_TIMEOUT = 1000
    private val INVOKE_COMMAND_TIMEOUT = 15000

    val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    var ipkEpochKey: ByteArray? = null
    lateinit var nocKey: ByteArray
    var requestId: String? = null
    var matterNodeId: String? = null
    var rmNodeId: String? = null
    var challenge: String? = null
    var tempDeviceId: Long? = null

    // Lazily instantiate [ChipDeviceController] and hold a reference to it.
    val chipDeviceController: ChipDeviceController by lazy {
        ChipDeviceController.loadJni()
        AndroidChipPlatform(
            AndroidBleManager(),
            PreferencesKeyValueStoreManager(context),
            PreferencesConfigurationManager(context),
            NsdManagerServiceResolver(context),
            NsdManagerServiceBrowser(context),
            ChipMdnsCallbackImpl(),
            DiagnosticDataProviderImpl(context)
        )

        val decodedHex: ByteArray = Utils.decodeHex(ipk)
        val encodedHexB64: ByteArray = Base64.getEncoder().encode(decodedHex)
        var ipk = String(encodedHexB64)
        ipkEpochKey = Base64.getDecoder().decode(ipk)

        ChipDeviceController(
            ControllerParams.newBuilder(operationalKeyConfig()).setUdpListenPort(0)
                .setControllerVendorId(VENDOR_ID)
                .build()
        ).also { chipDeviceController ->
            chipDeviceController.setNOCChainIssuer(EspNOCChainIssuer())
        }
    }

    private fun operationalKeyConfig(): OperationalKeyConfig {

        Log.d(TAG, "OperationalKeyConfig called.....................")
        val chain = keyStore.getCertificateChain(fabricId)

        try {
            Log.d(TAG, "Init OperationalKeyConfig : fabric id : $fabricId")

            val sequence = DERSequence.getInstance(chain[0].publicKey.encoded)
            val subjectPublicKey = sequence.getObjectAt(1) as DERBitString
            nocKey = subjectPublicKey.bytes
            Log.d(TAG, "NOC key : $nocKey")

            var s = chain[0].toString()
            Log.d(TAG, "NOC : $s")

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return OperationalKeyConfig(
            EspKeypairDelegate(),
            chain[1].encoded,
            chain[1].encoded,
            chain[0].encoded,
            ipkEpochKey
        )
    }

    inner class EspKeypairDelegate : KeypairDelegate {

        /**
         * Ensure that a private key is generated when this method returns.
         * @throws KeypairException if a private key could not be generated or resolved
         */
        @Throws(KeypairDelegate.KeypairException::class)
        override fun generatePrivateKey() {
            Log.d("EspKeypairDelegate", "====================== generatePrivateKey")
        }

        /**
         * Returns an operational PKCS#10 CSR in DER-encoded form, signed by the underlying private key.
         * @throws KeypairException if the CSR could not be generated
         */
        @Throws(KeypairDelegate.KeypairException::class)
        override fun createCertificateSigningRequest(): ByteArray? {
            Log.d("EspKeypairDelegate", "======================== createCertificateSigningRequest")
            return null;
        }

        /**
         * Returns the DER-encoded X.509 public key, generating a new private key if one has not already
         * been created.
         * @throws KeypairException if a private key could not be resolved
         */
        @Throws(KeypairDelegate.KeypairException::class)
        override fun getPublicKey(): ByteArray? {
            Log.d("EspKeypairDelegate", "======================== getPublicKey")
            return nocKey
        }

        /**
         * Signs the given message with the private key (generating one if it has not yet been created)
         * using ECDSA and returns a DER-encoded signature.
         * @throws KeypairException if a private key could not be resolved, or the message could not be
         * signed
         */
        @Throws(KeypairDelegate.KeypairException::class)
        override fun ecdsaSignMessage(message: ByteArray?): ByteArray? {
            Log.d("EspKeypairDelegate", "======================== ecdsaSignMessage")
            var privateKey = keyStore.getKey(fabricId, null) as PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(message)
            return signature.sign()
        }
    }

    inner class EspNOCChainIssuer : ChipDeviceController.NOCChainIssuer {
        override fun onNOCChainGenerationNeeded(
            csrInfo: CSRInfo?,
            attestationInfo: AttestationInfo?
        ) {

            Log.d(TAG, "======================== Received callback for CSR =======================")

            var nodeNoc: String = ""
            CoroutineScope(Dispatchers.IO).launch {

                if (csrInfo != null) {

                    val tempCsr = Base64.getEncoder().encodeToString(csrInfo.csr)
                    var finalCSR = AppConstants.CERT_BEGIN + "\n" +
                            tempCsr + "\n" + AppConstants.CERT_END

                    val body = JsonObject()
                    body.addProperty(AppConstants.KEY_OPERATION, AppConstants.KEY_OPERATION_ADD)
                    body.addProperty(AppConstants.KEY_CSR_TYPE, "node")

                    val csrReqJson = JsonObject()
                    csrReqJson.addProperty(AppConstants.KEY_GROUP_ID, groupId)
                    csrReqJson.addProperty(AppConstants.KEY_CSR, finalCSR)

                    var csrReqJsonArr = JsonArray()
                    csrReqJsonArr.add(csrReqJson)

                    body.add(AppConstants.KEY_CSR_REQUESTS, csrReqJsonArr)

                    var bundleData: Bundle = ApiManager.getInstance(context).getNodeNoc(body)
                    nodeNoc = bundleData.getString("node_noc", "")
                    requestId = bundleData.getString(AppConstants.KEY_REQ_ID, "")
                    matterNodeId = bundleData.getString(AppConstants.KEY_MATTER_NODE_ID, "")

                    Log.d(
                        TAG,
                        "Matter node id ::: >>>>> ${matterNodeId} and request id ::: >>>>>  ${requestId}"
                    )

                    nodeNoc = nodeNoc.replace("-----BEGIN CERTIFICATE-----", "")
                    nodeNoc = nodeNoc.replace("-----END CERTIFICATE-----", "")
                    nodeNoc = nodeNoc.replace("\n", "")
                    Log.d(TAG, "Calling onNOCChainGeneration with node NOC")

                    val chain = arrayOf(decode(nodeNoc), decode(rootCa))
                    val err = chipDeviceController.onNOCChainGeneration(
                        ControllerParams.newBuilder()
                            .setRootCertificate(chain[1].encoded)
                            .setIntermediateCertificate(chain[1].encoded)
                            .setOperationalCertificate(chain[0].encoded)
                            .setOperationalCertificate(decode(nodeNoc).encoded)
                            .setIpk(ipkEpochKey)
                            .build()
                    )
                    Log.e(TAG, "NOCChainGenerated Error $err")
                }
            }
        }
    }

    fun decode(cert: String?): X509Certificate {
        val encodedCert: ByteArray = Base64.getDecoder().decode(cert)
        val inputStream = ByteArrayInputStream(encodedCert)
        val certFactory = CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(inputStream) as X509Certificate
    }

    /**
     * Wrapper around [ChipDeviceController.getConnectedDevicePointer] to return the value directly.
     */
    suspend fun getConnectedDevicePointer(nodeId: Long): Long {
        return suspendCoroutine { continuation ->
            chipDeviceController.getConnectedDevicePointer(
                nodeId,
                object : GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        Log.d(TAG, "Got connected device pointer")
                        continuation.resume(devicePointer)
                    }

                    override fun onConnectionFailure(nodeId: Long, error: Exception) {
                        val errorMessage = "Unable to get connected device with nodeId $nodeId."
                        Log.e(TAG, errorMessage, error)
                        continuation.resumeWithException(IllegalStateException(errorMessage))
                    }
                })
        }
    }

    suspend fun readDescriptorClusterPartsListAttribute(
        devicePtr: Long,
        endpoint: Int
    ): List<Any>? {
        return suspendCoroutine { continuation ->
            getDescriptorClusterForDevice(devicePtr, endpoint)
                .readPartsListAttribute(
                    object : ChipClusters.DescriptorCluster.PartsListAttributeCallback {
                        override fun onSuccess(values: MutableList<Int>?) {
                            continuation.resume(values)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    private fun getDescriptorClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.DescriptorCluster {
        return ChipClusters.DescriptorCluster(devicePtr, endpoint)
    }

    /**
     * Removes the app's fabric from the device.
     *
     * @param nodeId node identifier
     */
    suspend fun awaitUnpairDevice(nodeId: Long) {
        return suspendCoroutine { continuation ->
            Log.d(TAG, "Calling chipDeviceController.unpair")
            val callback: UnpairDeviceCallback =
                object : UnpairDeviceCallback {
                    override fun onError(status: Int, nodeId: Long) {
                        continuation.resumeWithException(
                            java.lang.IllegalStateException(
                                "Failed unpairing device [$nodeId] with status [$status]"
                            )
                        )
                    }

                    override fun onSuccess(nodeId: Long) {
                        Log.d(TAG, "awaitUnpairDevice.onSuccess: deviceId [$nodeId]")
                        continuation.resume(Unit)
                    }
                }
            chipDeviceController.unpairDeviceCallback(nodeId, callback)
        }
    }

    fun computePaseVerifier(
        devicePtr: Long,
        pinCode: Long,
        iterations: Long,
        salt: ByteArray
    ): PaseVerifierParams {
        Log.d(
            TAG,
            "computePaseVerifier: devicePtr [${devicePtr}] pinCode [${pinCode}] iterations [${iterations}] salt [${salt}]"
        )
        return chipDeviceController.computePaseVerifier(devicePtr, pinCode, iterations, salt)
    }

    suspend fun awaitEstablishPaseConnection(
        deviceId: Long,
        ipAddress: String,
        port: Int,
        setupPinCode: Long
    ) {
        return suspendCoroutine { continuation ->
            chipDeviceController.setCompletionListener(
                object : BaseCompletionListener() {
                    override fun onConnectDeviceComplete() {
                        super.onConnectDeviceComplete()
                        continuation.resume(Unit)
                    }

                    // Note that an error in processing is not necessarily communicated via onError().
                    // onCommissioningComplete with a "code != 0" also denotes an error in processing.
                    override fun onPairingComplete(code: Long) {
                        super.onPairingComplete(code)
                        if (code != 0L) {
                            continuation.resumeWithException(
                                IllegalStateException("Pairing failed with error code [${code}]")
                            )
                        } else {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onError(error: Throwable) {
                        super.onError(error)
                        continuation.resumeWithException(error)
                    }

                    override fun onReadCommissioningInfo(
                        vendorId: Int,
                        productId: Int,
                        wifiEndpointId: Int,
                        threadEndpointId: Int
                    ) {
                        super.onReadCommissioningInfo(
                            vendorId,
                            productId,
                            wifiEndpointId,
                            threadEndpointId
                        )
                        continuation.resume(Unit)
                    }

                    override fun onCommissioningStatusUpdate(
                        nodeId: Long,
                        stage: String?,
                        errorCode: Long
                    ) {
                        super.onCommissioningStatusUpdate(nodeId, stage, errorCode)
                        continuation.resume(Unit)
                    }

                    override fun onICDRegistrationInfoRequired() {
                        Log.d(TAG, "onICDRegistrationInfoRequired")
                    }

                    override fun onICDRegistrationComplete(
                        errorCode: Long,
                        icdDeviceInfo: ICDDeviceInfo?
                    ) {
                        Log.d(
                            TAG,
                            "onICDRegistrationComplete - errorCode: $errorCode, icdDeviceInfo : $icdDeviceInfo"
                        )
                    }
                })

            // Temporary workaround to remove interface indexes from ipAddress
            // due to https://github.com/project-chip/connectedhomeip/pull/19394/files
            chipDeviceController.establishPaseConnection(
                deviceId, stripLinkLocalInIpAddress(ipAddress), port, setupPinCode
            )
        }
    }

    suspend fun awaitCommissionDevice(deviceId: Long, networkCredentials: NetworkCredentials?) {
        this.tempDeviceId = deviceId

        return suspendCoroutine { continuation ->
            chipDeviceController.setCompletionListener(
                object : BaseCompletionListener() {
                    // Note that an error in processing is not necessarily communicated via onError().
                    // onCommissioningComplete with an "errorCode != 0" also denotes an error in processing.
                    override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {
                        super.onCommissioningComplete(nodeId, errorCode)
                        if (errorCode != 0L) {
                            continuation.resumeWithException(
                                IllegalStateException("Commissioning failed with error code [${errorCode}]")
                            )
                        } else {

                            CoroutineScope(Dispatchers.IO).launch {

                                val id = BigInteger(matterNodeId, 16)
                                val deviceNodeId = id.toLong()
                                val devicePtr = awaitGetConnectedDevicePointer(deviceNodeId)
                                Log.d(TAG, "=============== Commissioning Complete ===============")

                                val clustersHelper = ClustersHelper(this@ChipClient)
                                val deviceMatterInfo =
                                    clustersHelper.fetchDeviceMatterInfo(deviceNodeId)
                                var isRmClusterAvailable = false
                                var isControllerClusterAvailable = false
                                val metadataJson = JsonObject()
                                val body = JsonObject()
                                var deviceName = ""

                                if (deviceMatterInfo != null && deviceMatterInfo.isNotEmpty()) {
                                    try {
                                        var endpointsArray = JsonArray()
                                        var serversDataJson = JsonObject()
                                        var clientsDataJson = JsonObject()

                                        for (info in deviceMatterInfo) {
                                            Log.d(TAG, "Endpoint : ${info.endpoint}")
                                            Log.d(TAG, "Server Clusters : ${info.serverClusters}")
                                            Log.d(TAG, "Client Clusters : ${info.clientClusters}")
                                            Log.d(TAG, "Types : ${info.types}")

                                            if (info.types != null && info.types.isNotEmpty()) {
                                                metadataJson.addProperty(
                                                    "deviceType",
                                                    info.types[0].toInt()
                                                )

                                                if (TextUtils.isEmpty(deviceName)) {
                                                    deviceName =
                                                        NodeUtils.getDefaultNameForMatterDevice(
                                                            info.types[0].toInt()
                                                        )
                                                }
                                            }

                                            endpointsArray.add(info.endpoint)

                                            if (info.serverClusters != null && info.serverClusters.isNotEmpty()) {
                                                var serverClustersArr = JsonArray()
                                                for (serverCluster in info.serverClusters) {
                                                    serverClustersArr.add(
                                                        serverCluster.toString().toInt()
                                                    )
                                                }
                                                serversDataJson.add(
                                                    info.endpoint.toString(),
                                                    serverClustersArr
                                                )
                                            }

                                            if (info.clientClusters != null && info.clientClusters.isNotEmpty()) {
                                                var clientClustersArr = JsonArray()
                                                for (clientCluster in info.clientClusters) {
                                                    clientClustersArr.add(
                                                        clientCluster.toString().toInt()
                                                    )
                                                }
                                                clientsDataJson.add(
                                                    info.endpoint.toString(),
                                                    clientClustersArr
                                                )
                                            }

                                            if (info.endpoint == 0) {
                                                for (serverCluster in info.serverClusters) {
                                                    var clusterId: Long = serverCluster as Long
                                                    if (clusterId == AppConstants.RM_CLUSTER_ID) {
                                                        Log.d(TAG, "RainMaker Cluster Available")
                                                        isRmClusterAvailable = true
                                                    }

                                                    if (clusterId == AppConstants.CONTROLLER_CLUSTER_ID) {
                                                        isControllerClusterAvailable = true
                                                        deviceName =
                                                            AppConstants.DEVICE_NAME_MATTER_CONTROLLER
                                                    }

                                                    if (clusterId == AppConstants.THREAD_BR_MANAGEMENT_CLUSTER_ID) {
                                                        deviceName =
                                                            AppConstants.DEVICE_NAME_THREAD_BR
                                                    }
                                                }
                                            }
                                        }

                                        metadataJson.addProperty(
                                            AppConstants.KEY_IS_RAINMAKER,
                                            isRmClusterAvailable
                                        )
                                        metadataJson.addProperty(
                                            AppConstants.KEY_DEVICENAME,
                                            deviceName
                                        )
                                        metadataJson.addProperty(AppConstants.KEY_GROUP_ID, groupId)
                                        metadataJson.add("endpointsData", endpointsArray)

                                        if (serversDataJson.size() > 0) {
                                            metadataJson.add("serversData", serversDataJson)
                                        }
                                        if (clientsDataJson.size() > 0) {
                                            metadataJson.add("clientsData", clientsDataJson)
                                        }

                                    } catch (e: ExecutionException) {
                                        throw RuntimeException(e)
                                    } catch (e: InterruptedException) {
                                        throw RuntimeException(e)
                                    }
                                }

                                if (isRmClusterAvailable) {

                                    // Read RM node Id
                                    val rmNodeIdAttributePath =
                                        ChipAttributePath.newInstance(
                                            0x0,
                                            AppConstants.RM_CLUSTER_ID_HEX,
                                            0x1L
                                        )
                                    val rmNodeIdData =
                                        readAttribute(devicePtr, rmNodeIdAttributePath)
                                    Log.d(TAG, "RainMaker Node Id : ${rmNodeIdData?.value}")
                                    rmNodeId = rmNodeIdData?.value as String?

                                    // Write Matter Node Id
                                    if (matterNodeId != null) {
                                        val tlvWriter = TlvWriter()
                                        tlvWriter.put(AnonymousTag, matterNodeId!!)

                                        val attributePath3 =
                                            ChipAttributePath.newInstance(
                                                0x0,
                                                AppConstants.RM_CLUSTER_ID_HEX,
                                                0x3L
                                            )
                                        val matterNodeIdData =
                                            writeAttribute(
                                                devicePtr,
                                                attributePath3,
                                                tlvWriter.getEncoded()
                                            )
                                        Log.d(
                                            TAG,
                                            "Write matter node id, response : $matterNodeIdData"
                                        )
                                    }

                                    // Read challenge response
                                    val challengeAttributePath =
                                        ChipAttributePath.newInstance(
                                            0x0,
                                            AppConstants.RM_CLUSTER_ID_HEX,
                                            0x2L
                                        )
                                    val challengeData: AttributeState? =
                                        readAttribute(devicePtr, challengeAttributePath)
                                    Log.d(TAG, "Challenge Data : ${challengeData.toString()}")
                                    if (challengeData != null) {
                                        Log.d(TAG, "Challenge Data : ${challengeData.value}")
                                        challenge = challengeData?.value as String?
                                    }

                                    body.addProperty(AppConstants.KEY_RAINMAKER_NODE_ID, rmNodeId)
                                    body.addProperty(AppConstants.KEY_CHALLENGE, challenge)
                                } else {
                                    // Nothing to do
                                }

                                val matterMetadataJson = JsonObject()
                                matterMetadataJson.add(AppConstants.KEY_MATTER, metadataJson)
                                Log.d(TAG, "Metadata Json : $matterMetadataJson")

                                body.addProperty(AppConstants.KEY_REQ_ID, requestId)
                                body.addProperty(AppConstants.KEY_STATUS, "success")
                                body.add(AppConstants.KEY_METADATA, matterMetadataJson)

                                val responseData: Bundle? =
                                    ApiManager.getInstance(context).confirmMatterNode(body, groupId)
                                var isRainMaker: Boolean = isRmClusterAvailable

                                responseData.let {
                                    isRainMaker =
                                        it?.getBoolean(AppConstants.KEY_IS_RAINMAKER_NODE, false)
                                            ?: false
                                    val status: String? =
                                        it?.getString(AppConstants.KEY_STATUS)
                                    val description: String? =
                                        it?.getString(AppConstants.KEY_DESCRIPTION)
                                }

                                if (isControllerClusterAvailable && isRainMaker) {

                                    Log.d(TAG, "Controller cluster available")
                                    val sharedPreferences =
                                        context.getSharedPreferences(
                                            AppConstants.ESP_PREFERENCES,
                                            Context.MODE_PRIVATE
                                        )
                                    val editor = sharedPreferences.edit()
                                    editor.putBoolean(rmNodeId, true)
                                    val key = "ctrl_setup_$rmNodeId"
                                    editor.putBoolean(key, false)
                                    editor.apply()
                                }

                                var aclClusterHelper = AccessControlClusterHelper(this@ChipClient)
                                var aclAttr: MutableList<ChipStructs.AccessControlClusterAccessControlEntryStruct>? =
                                    null

                                Log.d(TAG, "Reading ACL Attributes")
                                aclAttr = aclClusterHelper.readAclAttributeAsync(
                                    deviceNodeId,
                                    AppConstants.ENDPOINT_0
                                ).get()
                                Log.d(TAG, "ACL attributes : $aclAttr")

                                var entries: java.util.ArrayList<ChipStructs.AccessControlClusterAccessControlEntryStruct> =
                                    java.util.ArrayList<ChipStructs.AccessControlClusterAccessControlEntryStruct>()

                                val it = aclAttr?.listIterator()
                                var fabricIndex = 0
                                var authMode = 0
                                if (it != null) {
                                    for (entry in it) {
                                        entries.add(entry)
                                        if (entry.privilege == AppConstants.PRIVILEGE_ADMIN) {
                                            fabricIndex = entry.fabricIndex
                                            authMode = entry.authMode
                                        }
                                    }
                                }

                                var subjects: ArrayList<Long> = ArrayList<Long>()
                                subjects.add(Utils.getCatId(groupCatIdOperate))

                                var entry =
                                    ChipStructs.AccessControlClusterAccessControlEntryStruct(
                                        AppConstants.PRIVILEGE_OPERATE,
                                        authMode, subjects,
                                        null,
                                        fabricIndex
                                    )

                                entries.add(entry)

                                aclClusterHelper.writeAclAttributeAsync(
                                    deviceNodeId,
                                    AppConstants.ENDPOINT_0,
                                    entries
                                ).get()

                                continuation.resume(Unit)
                            }
                        }
                    }

                    override fun onError(error: Throwable) {
                        super.onError(error)
                        continuation.resumeWithException(error)
                    }

                    override fun onICDRegistrationInfoRequired() {
                    }

                    override fun onICDRegistrationComplete(
                        errorCode: Long,
                        icdDeviceInfo: ICDDeviceInfo?
                    ) {
                    }
                })
            chipDeviceController.commissionDevice(deviceId, networkCredentials)
        }
    }

    suspend fun awaitOpenPairingWindowWithPIN(
        connectedDevicePointer: Long,
        duration: Int,
        iteration: Long,
        discriminator: Int,
        setupPinCode: Long
    ) {
        return suspendCoroutine { continuation ->
            Log.d(TAG, "Calling chipDeviceController.openPairingWindowWithPIN")
            val callback: OpenCommissioningCallback =
                object : OpenCommissioningCallback {
                    override fun onError(status: Int, deviceId: Long) {
                        Log.e(
                            TAG,
                            "ShareDevice: awaitOpenPairingWindowWithPIN.onError: status [${status}] device [${deviceId}]"
                        )
                        continuation.resumeWithException(
                            java.lang.IllegalStateException(
                                "Failed opening the pairing window with status [${status}]"
                            )
                        )
                    }

                    override fun onSuccess(
                        deviceId: Long,
                        manualPairingCode: String?,
                        qrCode: String?
                    ) {
                        Log.d(
                            TAG,
                            "ShareDevice: awaitOpenPairingWindowWithPIN.onSuccess: deviceId [${deviceId}]"
                        )
                        continuation.resume(Unit)
                    }
                }
            chipDeviceController.openPairingWindowWithPINCallback(
                connectedDevicePointer,
                duration,
                iteration,
                discriminator,
                setupPinCode,
                callback
            )
        }
    }

    /**
     * Wrapper around [ChipDeviceController.getConnectedDevicePointer] to return the value directly.
     */
    suspend fun awaitGetConnectedDevicePointer(nodeId: Long): Long {
        return suspendCoroutine { continuation ->
            chipDeviceController.getConnectedDevicePointer(
                nodeId,
                object : GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        Log.d(TAG, "Got connected device pointer")
                        continuation.resume(devicePointer)
                    }

                    override fun onConnectionFailure(nodeId: Long, error: Exception) {
                        val errorMessage = "Unable to get connected device with nodeId $nodeId"
                        Log.e(TAG, errorMessage, error)
                        continuation.resumeWithException(IllegalStateException(errorMessage))
                    }
                })
        }
    }

    // ---------------------------------------------------------------------------
    // We use our own mDNS discovery code, but interesting to note that
    // ChipDeviceController also offers that feature.

    fun getCommissionableNodes() {
        chipDeviceController.discoverCommissionableNodes()
    }

    fun getDiscoveredDevice(index: Int): DiscoveredDevice? {
        Log.d(TAG, "getDiscoveredDevice(${index})")
        return chipDeviceController.getDiscoveredDevice(index)
    }

    // ---------------------------------------------------------------------------
    // Access clusters via numeric ids. Useful to access manufacturer specific clusters.

    suspend fun writeAttribute(
        devicePtr: Long,
        attributePath: ChipAttributePath,
        tlv: ByteArray,
        timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
        imTimeoutMs: Int = DEFAULT_TIMEOUT
    ) {
        return writeAttributes(
            devicePtr, mapOf(attributePath to tlv), timedRequestTimeoutMs, imTimeoutMs
        )
    }

    /** Wrapper around [ChipDeviceController.write] */
    suspend fun writeAttributes(
        devicePtr: Long,
        attributes: Map<ChipAttributePath, ByteArray>,
        timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
        imTimeoutMs: Int = DEFAULT_TIMEOUT
    ) {
        return suspendCoroutine { continuation ->
            val requests: List<AttributeWriteRequest> =
                attributes.toList().map {
                    AttributeWriteRequest.newInstance(
                        it.first.endpointId, it.first.clusterId, it.first.attributeId, it.second
                    )
                }
            val callback: WriteAttributesCallback =
                object : WriteAttributesCallback {
                    override fun onError(
                        attributePath: ChipAttributePath?,
                        e: java.lang.Exception?
                    ) {
//                        continuation.resumeWithException(
//                            IllegalStateException(
//                                "writeAttributes failed",
//                                e
//                            )
//                        )
                        continuation.resume(Unit)
                    }

                    override fun onResponse(attributePath: ChipAttributePath?, status: Status?) {

                        if (attributePath!! ==
                            ChipAttributePath.newInstance(
                                requests.last().endpointId,
                                requests.last().clusterId,
                                requests.last().attributeId
                            )
                        ) {
                            continuation.resume(Unit)
                        }
                    }
                }

            chipDeviceController.write(
                callback,
                devicePtr,
                requests,
                timedRequestTimeoutMs,
                imTimeoutMs
            )
        }
    }

    suspend fun readAttribute(
        devicePtr: Long,
        attributePath: ChipAttributePath
    ): AttributeState? {
        return readAttributes(devicePtr, listOf(attributePath))[attributePath]
    }

    /** Wrapper around [ChipDeviceController.readAttributePath] */
    suspend fun readAttributes(
        devicePtr: Long,
        attributePaths: List<ChipAttributePath>
    ): Map<ChipAttributePath, AttributeState> {
        return suspendCoroutine { continuation ->
            val callback: ReportCallback =
                object : ReportCallback {

                    override fun onError(
                        attributePath: ChipAttributePath?,
                        eventPath: ChipEventPath?,
                        e: java.lang.Exception
                    ) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "readAttributes failed",
                                e
                            )
                        )
                    }

                    override fun onReport(nodeState: NodeState?) {
                        val states: HashMap<ChipAttributePath, AttributeState> = HashMap()

                        if (nodeState != null) {
                            Log.d(TAG, "Node state : ${nodeState.toString()}")
                            for (path in attributePaths) {
                                var endpoint: Int = path.endpointId.id.toInt()
                                Log.d(TAG, "endpoint : ${endpoint}")
                                states[path] =
                                    nodeState!!
                                        .getEndpointState(endpoint)!!
                                        .getClusterState(path.clusterId.id)!!
                                        .getAttributeState(path.attributeId.id)!!
                            }
                        }
                        continuation.resume(states)
                    }

                    override fun onDone() {
                        super.onDone()
                        Log.d(TAG, "Report callback onDone")
                    }
                }
            chipDeviceController.readAttributePath(
                callback, devicePtr, attributePaths, DEFAULT_TIMEOUT
            )
        }
    }
    
    /** Wrapper around [ChipDeviceController.invoke] */
    suspend fun invoke(
        devicePtr: Long,
        invokeElement: InvokeElement,
        timedRequestTimeoutMs: Int = INVOKE_COMMAND_TIMEOUT,
        imTimeoutMs: Int = INVOKE_COMMAND_TIMEOUT
    ): Long {
        return suspendCoroutine { continuation ->
            val invokeCallback: InvokeCallback =
                object : InvokeCallback {
                    override fun onError(e: java.lang.Exception?) {

                        e?.printStackTrace()
                        continuation.resumeWithException(
                            IllegalStateException(
                                "invoke failed",
                                e
                            )
                        )
                    }

                    override fun onResponse(invokeElement: InvokeElement?, successCode: Long) {
                        Log.d(TAG, "Invoke command success")
                        continuation.resume(successCode)
                    }
                }
            chipDeviceController.invoke(
                invokeCallback, devicePtr, invokeElement, timedRequestTimeoutMs, imTimeoutMs
            )
        }
    }
}
