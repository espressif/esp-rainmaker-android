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
    val iceServers: List<IceServer>,
    val region: String
)

object WebRtcChannelInfoHelper {
    private const val TAG = "WebRtcChannelInfoHelper"

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
            val iceServerList = mutableListOf<IceServer>()
            try {
                val awsKinesisVideoSignalingClient = WebRtcConstants.getAwsKinesisVideoSignalingClient(region, dataEndpoint)
                val iceServerConfigResult = awsKinesisVideoSignalingClient.getIceServerConfig(
                    GetIceServerConfigRequest()
                        .withChannelARN(channelArn)
                        .withClientId(role.name)
                )
                // Convert AWS SDK IceServer to WebRTC IceServer
                // The AWS SDK returns IceServer objects with uris list, username, password, ttl
                // We need to convert each URI to a WebRTC IceServer
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
