package com.espressif.ui.webrtc

import android.util.Log
import com.amazonaws.services.kinesisvideo.AWSKinesisVideoClient
import com.amazonaws.services.kinesisvideo.model.ChannelRole
import com.amazonaws.services.kinesisvideo.model.CreateSignalingChannelRequest
import com.amazonaws.services.kinesisvideo.model.DescribeSignalingChannelRequest
import com.amazonaws.services.kinesisvideo.model.GetSignalingChannelEndpointRequest
import com.amazonaws.services.kinesisvideo.model.ResourceEndpointListItem
import com.amazonaws.services.kinesisvideo.model.ResourceNotFoundException
import com.amazonaws.services.kinesisvideo.model.SingleMasterChannelEndpointConfiguration
import com.amazonaws.services.kinesisvideo.model.ListStreamsRequest
import com.amazonaws.services.kinesisvideo.model.StreamNameCondition
import com.amazonaws.services.kinesisvideosignaling.AWSKinesisVideoSignalingClient
import com.amazonaws.services.kinesisvideosignaling.model.GetIceServerConfigRequest
import com.amazonaws.services.kinesisvideosignaling.model.GetIceServerConfigResult
import com.amazonaws.services.kinesisvideosignaling.model.IceServer as AwsIceServer
import com.espressif.webrtc.WebRtcConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.PeerConnection.IceServer

data class WebRtcChannelInfo(
    val channelArn: String,
    val streamArn: String?,
    val wssEndpoint: String,
    val webrtcEndpoint: String?,
    val dataEndpoint: String? = null,
    val iceServers: List<IceServer>,
    val region: String
)

object WebRtcChannelInfoHelper {
    private const val TAG = "WebRtcChannelInfoHelper"

    // --- Cache ---

    private const val PREFS_NAME = "webrtc_endpoint_cache"
    private const val DEFAULT_ICE_TTL_MS = 5 * 60 * 1000L  // 5 minutes fallback

    private data class IceCacheEntry(
        val iceServers: List<IceServer>,
        val expiresAt: Long
    )

    /**
     * Serializable endpoint data for SharedPreferences persistence.
     * Does not include iceServers (those are cached separately in-memory with short TTL).
     */
    private data class PersistedEndpoint(
        val channelArn: String,
        val wssEndpoint: String,
        val webrtcEndpoint: String?,
        val dataEndpoint: String?,
        val region: String
    )

    private val iceCache = LinkedHashMap<String, IceCacheEntry>(4, 0.75f, true)
    private const val MAX_ICE_CACHE_ENTRIES = 10

    private var appContext: android.content.Context? = null

    /**
     * Initialize with application context for persistent endpoint cache.
     * Call from Application.onCreate().
     */
    @JvmStatic
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private fun getPrefs(): android.content.SharedPreferences? {
        return appContext?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    private fun endpointCacheKey(region: String, channelName: String, role: ChannelRole) =
        "$region:$channelName:${role.name}"

    private fun iceCacheKey(region: String, channelArn: String, role: ChannelRole) =
        "$region:$channelArn:${role.name}"

    /**
     * Clear all cached data (endpoints + ICE servers).
     * Call on logout or device removal.
     */
    @JvmStatic
    fun clearCache() {
        getPrefs()?.edit()?.clear()?.apply()
        synchronized(iceCache) { iceCache.clear() }
        Log.d(TAG, "Cache cleared")
    }

    private fun loadEndpointFromDisk(key: String): WebRtcChannelInfo? {
        val prefs = getPrefs() ?: return null
        val json = prefs.getString(key, null) ?: return null
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            WebRtcChannelInfo(
                channelArn = obj.get("channelArn").asString,
                streamArn = null,
                wssEndpoint = obj.get("wssEndpoint").asString,
                webrtcEndpoint = obj.get("webrtcEndpoint")?.takeIf { !it.isJsonNull }?.asString,
                dataEndpoint = obj.get("dataEndpoint")?.takeIf { !it.isJsonNull }?.asString,
                iceServers = emptyList(),
                region = obj.get("region").asString
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse cached endpoint for $key: ${e.message}")
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun saveEndpointToDisk(key: String, info: WebRtcChannelInfo) {
        val prefs = getPrefs() ?: return
        val obj = com.google.gson.JsonObject().apply {
            addProperty("channelArn", info.channelArn)
            addProperty("wssEndpoint", info.wssEndpoint)
            addProperty("webrtcEndpoint", info.webrtcEndpoint)
            addProperty("dataEndpoint", info.dataEndpoint)
            addProperty("region", info.region)
        }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    // --- Endpoints ---

    /**
     * Fetch channel endpoints only (DescribeSignalingChannel + GetSignalingChannelEndpoint).
     * Returns WebRtcChannelInfo with empty iceServers — ICE servers can be fetched
     * in parallel with WebSocket connect using [fetchIceServersBlocking].
     *
     * Results are persisted to disk and cached indefinitely until explicitly cleared.
     */
    suspend fun fetchChannelEndpoints(
        region: String,
        channelName: String,
        role: ChannelRole = ChannelRole.VIEWER
    ): Result<WebRtcChannelInfo> {
        val key = endpointCacheKey(region, channelName, role)
        val cached = loadEndpointFromDisk(key)
        if (cached != null) {
            Log.d(TAG, "Endpoint cache hit for $key")
            return Result.success(cached)
        }

        return fetchChannelEndpointsFromAws(region, channelName, role).also { result ->
            result.onSuccess { info ->
                saveEndpointToDisk(key, info)
                Log.d(TAG, "Endpoint cache stored for $key")
            }
        }
    }

    private suspend fun fetchChannelEndpointsFromAws(
        region: String,
        channelName: String,
        role: ChannelRole
    ): Result<WebRtcChannelInfo> = withContext(Dispatchers.IO) {
        try {
            val awsKinesisVideoClient = WebRtcConstants.getAwsKinesisVideoClient(region)

            // Step 1. Describe or Create Signaling Channel
            var channelArn: String
            try {
                val describeResult = awsKinesisVideoClient.describeSignalingChannel(
                    DescribeSignalingChannelRequest().withChannelName(channelName)
                )
                channelArn = describeResult.channelInfo.channelARN
                Log.i(TAG, "Channel ARN is $channelArn")
            } catch (e: ResourceNotFoundException) {
                if (role == ChannelRole.MASTER) {
                    try {
                        val createResult = awsKinesisVideoClient.createSignalingChannel(
                            CreateSignalingChannelRequest().withChannelName(channelName)
                        )
                        channelArn = createResult.channelARN
                    } catch (ex: Exception) {
                        return@withContext Result.failure(Exception("Create Signaling Channel failed: ${ex.localizedMessage}"))
                    }
                } else {
                    return@withContext Result.failure(Exception("Signaling Channel $channelName doesn't exist!"))
                }
            } catch (ex: Exception) {
                return@withContext Result.failure(Exception("Describe Signaling Channel failed: ${ex.localizedMessage}"))
            }

            // Step 2. Get Signaling Channel Endpoint
            val protocols = arrayOf("WSS", "HTTPS")
            try {
                val endpointResult = awsKinesisVideoClient.getSignalingChannelEndpoint(
                    GetSignalingChannelEndpointRequest()
                        .withChannelARN(channelArn)
                        .withSingleMasterChannelEndpointConfiguration(
                            SingleMasterChannelEndpointConfiguration()
                                .withProtocols(protocols.toList())
                                .withRole(role)
                        )
                )

                Log.i(TAG, "Endpoints $endpointResult")

                var wssEndpoint: String? = null
                var webrtcEndpoint: String? = null
                var dataEndpoint: String? = null

                for (endpoint in endpointResult.resourceEndpointList) {
                    when (endpoint.protocol) {
                        "WSS" -> wssEndpoint = endpoint.resourceEndpoint
                        "WEBRTC" -> webrtcEndpoint = endpoint.resourceEndpoint
                        "HTTPS" -> dataEndpoint = endpoint.resourceEndpoint
                    }
                }

                if (wssEndpoint == null) {
                    return@withContext Result.failure(Exception("WSS endpoint not found"))
                }

                Result.success(
                    WebRtcChannelInfo(
                        channelArn = channelArn,
                        streamArn = null,
                        wssEndpoint = wssEndpoint,
                        webrtcEndpoint = webrtcEndpoint,
                        dataEndpoint = dataEndpoint,
                        iceServers = emptyList(),
                        region = region
                    )
                )
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Get Signaling Endpoint failed: ${e.localizedMessage}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch channel endpoints: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- ICE Servers ---

    /**
     * Fetch ICE server configuration (TURN servers).
     * Blocking call — intended to run on a background thread in parallel with WebSocket connect.
     *
     * Results are cached using the TTL provided by AWS (typically 5 minutes).
     */
    @JvmStatic
    fun fetchIceServersBlocking(
        region: String,
        channelArn: String,
        dataEndpoint: String?,
        role: ChannelRole = ChannelRole.VIEWER
    ): List<IceServer> {
        val key = iceCacheKey(region, channelArn, role)
        synchronized(iceCache) {
            val cached = iceCache[key]
            if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
                Log.d(TAG, "ICE cache hit for $key")
                return cached.iceServers
            }
        }

        val iceServerList = mutableListOf<IceServer>()
        val awsKinesisVideoSignalingClient = WebRtcConstants.getAwsKinesisVideoSignalingClient(region, dataEndpoint)
        val iceServerConfigResult = awsKinesisVideoSignalingClient.getIceServerConfig(
            GetIceServerConfigRequest()
                .withChannelARN(channelArn)
                .withClientId(role.name)
        )

        val awsIceServers: List<AwsIceServer> = iceServerConfigResult.iceServerList
        val userNames = mutableListOf<String>()
        val passwords = mutableListOf<String>()
        val ttls = mutableListOf<Int>()
        val urisList = mutableListOf<List<String>>()

        for (awsIceServer in awsIceServers) {
            userNames.add(awsIceServer.username ?: "")
            passwords.add(awsIceServer.password ?: "")
            ttls.add(awsIceServer.ttl ?: 0)
            urisList.add(awsIceServer.uris ?: emptyList())
        }

        // Convert each AWS TURN URI to its own WebRTC IceServer.
        // AWS typically returns multiple URIs per server (UDP/TCP/TLS); collapsing them
        // into one comma-joined string makes libwebrtc treat the result as a single
        // malformed URI and drop the TCP/TLS fallbacks.
        for (i in awsIceServers.indices) {
            val turnServerUris = urisList[i]
            for (uri in turnServerUris) {
                val builder = IceServer.builder(uri)
                if (userNames[i].isNotEmpty() && passwords[i].isNotEmpty()) {
                    builder.setUsername(userNames[i])
                    builder.setPassword(passwords[i])
                }
                iceServerList.add(builder.createIceServer())
            }
        }

        // Cache using minimum TTL from AWS response (seconds → ms), fallback to 5 min
        val minTtlSeconds = ttls.filter { it > 0 }.minOrNull() ?: 0
        val cacheTtlMs = if (minTtlSeconds > 0) minTtlSeconds * 1000L else DEFAULT_ICE_TTL_MS

        synchronized(iceCache) {
            if (iceCache.size >= MAX_ICE_CACHE_ENTRIES) {
                val oldest = iceCache.keys.first()
                iceCache.remove(oldest)
            }
            iceCache[key] = IceCacheEntry(
                iceServers = iceServerList,
                expiresAt = System.currentTimeMillis() + cacheTtlMs
            )
            Log.d(TAG, "ICE cache stored for $key (TTL: ${cacheTtlMs / 1000}s)")
        }

        return iceServerList
    }

    // --- Legacy (backward compat) ---

    /**
     * Fetch all channel info sequentially (endpoints + ICE servers).
     * Kept for backward compatibility; prefer [fetchChannelEndpoints] + parallel ICE fetch for better performance.
     */
    suspend fun fetchChannelInfo(
        region: String,
        channelName: String,
        role: ChannelRole = ChannelRole.VIEWER
    ): Result<WebRtcChannelInfo> = withContext(Dispatchers.IO) {
        try {
            // Step 1. Create Kinesis Video Client
            val awsKinesisVideoClient = WebRtcConstants.getAwsKinesisVideoClient(region)

            // Step 2. Describe or Create Signaling Channel
            var channelArn: String
            try {
                val describeResult = awsKinesisVideoClient.describeSignalingChannel(
                    DescribeSignalingChannelRequest().withChannelName(channelName)
                )
                channelArn = describeResult.channelInfo.channelARN
                Log.i(TAG, "Channel ARN is $channelArn")
            } catch (e: ResourceNotFoundException) {
                if (role == ChannelRole.MASTER) {
                    try {
                        val createResult = awsKinesisVideoClient.createSignalingChannel(
                            CreateSignalingChannelRequest().withChannelName(channelName)
                        )
                        channelArn = createResult.channelARN
                    } catch (ex: Exception) {
                        return@withContext Result.failure(Exception("Create Signaling Channel failed: ${ex.localizedMessage}"))
                    }
                } else {
                    return@withContext Result.failure(Exception("Signaling Channel $channelName doesn't exist!"))
                }
            } catch (ex: Exception) {
                return@withContext Result.failure(Exception("Describe Signaling Channel failed: ${ex.localizedMessage}"))
            }

            // Step 3. Get Signaling Channel Endpoint
            val protocols = arrayOf("WSS", "HTTPS")
            val endpointList = mutableListOf<ResourceEndpointListItem>()

            try {
                val endpointResult = awsKinesisVideoClient.getSignalingChannelEndpoint(
                    GetSignalingChannelEndpointRequest()
                        .withChannelARN(channelArn)
                        .withSingleMasterChannelEndpointConfiguration(
                            SingleMasterChannelEndpointConfiguration()
                                .withProtocols(protocols.toList())
                                .withRole(role)
                        )
                )

                Log.i(TAG, "Endpoints $endpointResult")
                endpointList.addAll(endpointResult.resourceEndpointList)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Get Signaling Endpoint failed: ${e.localizedMessage}"))
            }

            var wssEndpoint: String? = null
            var webrtcEndpoint: String? = null
            var dataEndpoint: String? = null

            for (endpoint in endpointList) {
                when (endpoint.protocol) {
                    "WSS" -> wssEndpoint = endpoint.resourceEndpoint
                    "WEBRTC" -> webrtcEndpoint = endpoint.resourceEndpoint
                    "HTTPS" -> dataEndpoint = endpoint.resourceEndpoint
                }
            }

            if (wssEndpoint == null) {
                return@withContext Result.failure(Exception("WSS endpoint not found"))
            }

            // Step 4. Get ICE Server Config
            val iceServerList = try {
                fetchIceServersBlocking(region, channelArn, dataEndpoint, role)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Get Ice Server Config failed: ${e.localizedMessage}"))
            }

            // Get stream ARN if available (for master role)
            var streamArn: String? = null
            if (role == ChannelRole.MASTER) {
                try {
                    val listStreamsResult = awsKinesisVideoClient.listStreams(
                        ListStreamsRequest().withStreamNameCondition(
                            StreamNameCondition().withComparisonOperator("BEGINS_WITH").withComparisonValue(channelName)
                        )
                    )
                    if (listStreamsResult.streamInfoList.isNotEmpty()) {
                        streamArn = listStreamsResult.streamInfoList[0].streamARN
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get stream ARN: ${e.localizedMessage}")
                }
            }

            Result.success(
                WebRtcChannelInfo(
                    channelArn = channelArn,
                    streamArn = streamArn,
                    wssEndpoint = wssEndpoint,
                    webrtcEndpoint = webrtcEndpoint,
                    dataEndpoint = dataEndpoint,
                    iceServers = iceServerList,
                    region = region
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch channel info: ${e.message}", e)
            Result.failure(e)
        }
    }
}
