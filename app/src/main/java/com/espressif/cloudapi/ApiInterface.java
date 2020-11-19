// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.cloudapi;

import com.espressif.AppConstants;
import com.google.gson.JsonObject;

import java.util.HashMap;

import io.reactivex.Observable;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * This class represents interface for all APIs.
 */
public interface ApiInterface {

    @FormUrlEncoded
    @POST
    Call<ResponseBody> loginWithGithub(@Url String url, @Header("Content-type") String contentType,
                                       @Field("grant_type") String grant_type,
                                       @Field("client_id") String client_id,
                                       @Field("code") String code,
                                       @Field("redirect_uri") String redirect_uri);

    // Get Supported Versions
    @GET
    Call<ResponseBody> getSupportedVersions(@Url String url);

    // Do login (for GitHub / Google login)
    @POST
    Call<ResponseBody> getOAuthLoginToken(@Url String url, @Body HashMap<String, String> body);

    // Get Nodes
    @GET
    Call<ResponseBody> getNodes(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token);

    // Get Node Details
    @GET
    Call<ResponseBody> getNode(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("node_id") String nodeId);

    // Add Node
    @PUT
    Call<ResponseBody> addNode(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Body DeviceOperationRequest rawJsonString);

    // Get Add Node request status
    @GET
    Call<ResponseBody> getAddNodeRequestStatus(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token,
                                               @Query("request_id") String requestId, @Query("user_request") boolean userReq);

    // Get param values
    @GET
    Call<ResponseBody> getParamValue(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId);

    // Update param value
    @PUT
    Call<ResponseBody> updateParamValue(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId, @Body JsonObject body);

    // Update schedules
    @PUT
    Observable<ResponseBody> updateSchedules(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId, @Body JsonObject body);

    // Remove Node
    @PUT
    Call<ResponseBody> removeNode(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Body DeviceOperationRequest rawJsonString);

    // Claiming initiate
    @POST
    Call<ResponseBody> initiateClaiming(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Body JsonObject body);

    // Claiming verify
    @POST
    Call<ResponseBody> verifyClaiming(@Url String url, @Header(AppConstants.HEADER_AUTHORIZATION) String token, @Body JsonObject body);
}
