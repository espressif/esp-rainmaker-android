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

package com.espressif;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.espressif.ui.models.EspNode;

@Database(entities = {EspNode.class}, version = 1)
public abstract class EspDatabase extends RoomDatabase {

    private static EspDatabase espDatabase;

    public abstract NodeDao getNodeDao();

    public static EspDatabase getInstance(Context context) {
        if (null == espDatabase) {
            espDatabase = buildDatabaseInstance(context);
        }
        return espDatabase;
    }

    private static EspDatabase buildDatabaseInstance(Context context) {
        return Room.databaseBuilder(context,
                EspDatabase.class,
                AppConstants.ESP_DATABASE_NAME)
                .allowMainThreadQueries().build();
    }

    public void cleanUp() {
        espDatabase = null;
    }
}
