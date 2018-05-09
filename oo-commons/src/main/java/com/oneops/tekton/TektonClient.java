/*******************************************************************************
 *
 *   Copyright 2018 Walmart, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *******************************************************************************/
package com.oneops.tekton;

import com.google.gson.Gson;
import okhttp3.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

public class TektonClient {
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private Gson gson = new Gson();
    private static Logger logger = Logger.getLogger(TektonClient.class);
    private String tektonBaseUrl = System.getProperty("tekton.base.url", "http://localhost:9000");
    private String authHeader = Base64.getEncoder().encodeToString(System.getProperty("tekton.auth.token").getBytes());

    public void reserveQuota(Map<String, Map<String, Integer>> quotaNeeded, long deploymentId, String entity,
                              String createdBy) throws IOException {
        for (String subscriptionId : quotaNeeded.keySet()) {
            Map<String, Integer> reservation = quotaNeeded.get(subscriptionId);
            RequestBody body = RequestBody.create(JSON, gson.toJson(reservation));

            String reservationId = composeReservationId(deploymentId, subscriptionId);
            String url = tektonBaseUrl + "/api/v1/quota/reservation?subscription=" + subscriptionId + "&entity=" + entity
                    + "&reservationId=" + reservationId + "&createdBy=" + createdBy;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", authHeader)
                    .post(body)
                    .build();

            OkHttpClient client = new OkHttpClient();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();
            int responseCode = response.code();
            logger.info("Reserve quota for reservationId: " + reservationId + " with resources: " + reservation
                                + " Tekton api response body: " + responseBody + ", code: " + responseCode);

            if (responseCode >= 300 && responseCode != 404) {
                throw new RuntimeException("Error while reserving quota. Response from Tekton: " + responseBody
                                                   + " ResponseCode : " + responseCode);
            }
        }
    }

    public int commitReservation(Map<String, Integer> resourceNumbers, long deploymentId, String subscriptinoId) throws IOException {
        String reservationId = composeReservationId(deploymentId, subscriptinoId);
        String url = tektonBaseUrl + "/api/v1/quota/reservation/" + reservationId + "/commit";
        RequestBody body = RequestBody.create(JSON, gson.toJson(resourceNumbers));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        int responseCode = response.code();
        logger.info("Commit quota for reservationId: " + reservationId + " with resources: " + resourceNumbers
                + " Tekton api response body: " + responseBody + ", code: " + responseCode);

        if (responseCode >= 300 && responseCode != 404) {
            throw new RuntimeException("Error while committing reservation. Response from Tekton: " + responseBody
                    + " ResponseCode : " + responseCode + " for reservationId: " + reservationId);
        }
        return responseCode;
    }

    public int rollbackReservation(Map<String, Integer> resourceNumbers, long deploymentId, String subscriptinoId) throws IOException {
        String reservationId = composeReservationId(deploymentId, subscriptinoId);
        String url = tektonBaseUrl + "/api/v1/quota/reservation/" + reservationId + "/rollback";
        RequestBody body = RequestBody.create(JSON, gson.toJson(resourceNumbers));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        int responseCode = response.code();

        logger.info("Rollback quota for reservationId: " + reservationId + " with resources: " + resourceNumbers
                + " Tekton api response body: " + responseBody + ", code: " + responseCode);

        if (responseCode >= 300 && responseCode != 404) {
            throw new RuntimeException("Error in rollback for reservation. Response from Tekton: " + responseBody
                    + " ResponseCode : " + responseCode + " for reservationId: " + reservationId);
        }

        return responseCode;
    }

    public int releaseResources(String entity, String subscriptionId, Map<String, Integer> resourceNumbers) throws IOException {
        String url = tektonBaseUrl + "/api/v1/quota/release/" + subscriptionId + "/" + entity;
        RequestBody body = RequestBody.create(JSON, gson.toJson(resourceNumbers));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        int responseCode = response.code();
        logger.info("Release resources for entity : " + entity + " for resources: " + resourceNumbers
                + " Tekton api response body: " + responseBody + ", code: " + responseCode);

        if (responseCode >= 300 && responseCode != 404) {
            throw new RuntimeException("Error while releasing resources for soft quota. Response from Tekton: " + responseBody
                    + " ResponseCode : " + responseCode + " for entity: " + entity);
        }

        return responseCode;
    }

    public void deleteReservations(long deploymentId, Set<String> subsciptionIds) throws IOException {
        OkHttpClient client = new OkHttpClient();
        for (String subscriptionId : subsciptionIds) {
            String reservationId = composeReservationId(deploymentId, subscriptionId);
            String url = tektonBaseUrl + "/api/v1/quota/reservation/" + reservationId;

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", authHeader)
                    .delete()
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();
            int responseCode = response.code();
            logger.info("Delete reservation: Tekton api response body: " + responseBody + ", code: " + responseCode
                                + " for reservation id: " + reservationId);

            if (responseCode >= 300 && responseCode != 404) {
                throw new RuntimeException("Error while deleting reservation for soft quota. Response from Tekton: " + responseBody
                                                   + " ResponseCode : " + responseCode);
            }
        }
    }

    private String composeReservationId(long deploymentId, String subsciptionId) {
        return deploymentId + ":" + subsciptionId;
    }
}
