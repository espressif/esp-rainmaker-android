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

package com.espressif.utils

import android.text.TextUtils
import com.espressif.ui.models.EspNode
import com.espressif.ui.models.Service

class NodeUtils {

    companion object {

        fun getService(node: EspNode, serviceType: String): Service? {

            for (service in node.services) {
                if (!TextUtils.isEmpty(service.type) && service.type.equals(serviceType)) {
                    return service
                }
            }
            return null
        }
    }
}