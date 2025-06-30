// Copyright 2025 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GroupSharingRequest(
    var reqId: String,
    var reqStatus: String? = null,
    var userName: ArrayList<String>? = null,
    var primaryUserName: ArrayList<String>? = null,
    var reqTime: Long = 0L,
    var group_ids: ArrayList<String>? = null,
    var group_names: ArrayList<String>? = null,
    var metadata: String? = null
) : Parcelable
