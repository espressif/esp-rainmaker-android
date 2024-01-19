/*
 * Copyright 2023 Google LLC
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

object MatterConstants {
  val DeviceTypesMap =
      mapOf<Long, String>(
          22L to "Root Node",
          256L to "On/Off Light",
          266L to "Outlet",
      )

  val ClustersMap =
      mapOf<Long, String>(
          3L to "Identify",
          4L to "Groups",
          5L to "Scenes",
          6L to "On/Off",
          8L to "Level Control",
          29L to "Descriptor",
          30L to "Binding",
          31L to "Access Control",
          40L to "Basic Information",
          41L to "OTA Software Update Provider",
          42L to "OTA Software Update Requestor",
          43L to "Localization Configuration",
          44L to "Time Format Localization",
          44L to "Time Format Localization",
          44L to "Unit Localization",
          48L to "General Commissioning",
          49L to "Network Commissioning",
          50L to "Diagnostics Logs",
          51L to "General Diagnostics",
          52L to "Software Diagnostics",
          53L to "Thread Network Diagnostics",
          54L to "Wi-Fi Network Diagnostics",
          55L to "Ethernet Network Diagnostics",
          56L to "Time Synchronization",
          59L to "Switch",
          60L to "Administrator Commissioning",
          62L to "Node Operational Credentials",
          63L to "Group Key Management",
          64L to "Fixed label",
          65L to "User label",
          1030L to "Occupancy Sensing",
      )

  // Well known cluster attributes
  data class ClusterAttribute(val clusterId: Long, val attributeId: Long)
  val OnOffAttribute = ClusterAttribute(6L, 0L)
}
