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

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.espressif.ui.models.EspNode;

import java.util.List;

@Dao
public interface NodeDao {

    @Query("SELECT * FROM " + AppConstants.NODE_TABLE)
    List<EspNode> getNodesFromStorage();

    /**
     * Insert node in the database.
     *
     * @param node Node to be inserted.
     */
    @Insert
    void insert(EspNode node);

    /**
     * Update node in the database.
     *
     * @param node Node to be updated.
     */
    @Update
    void update(EspNode node);

    /**
     * Delete the node from database.
     *
     * @param node Node to be deleted.
     */
    @Delete
    void delete(EspNode node);

    /**
     * Delete all nodes from node table.
     */
    @Query("DELETE FROM " + AppConstants.NODE_TABLE)
    void deleteAll();
}
