// Copyright 2022 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.adapters;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.espressif.ui.models.Action;

import java.util.List;

public class ActionDiffCallback extends DiffUtil.Callback {

    private final List<Action> oldActionList;
    private final List<Action> newActionList;

    public ActionDiffCallback(List<Action> oldActionList, List<Action> newActionList) {
        this.oldActionList = oldActionList;
        this.newActionList = newActionList;
    }

    @Override
    public int getOldListSize() {
        return oldActionList.size();
    }

    @Override
    public int getNewListSize() {
        return newActionList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        boolean areItemsTheSame = oldActionList.get(oldItemPosition).getNodeId().equals(newActionList.get(newItemPosition).getNodeId());
        return areItemsTheSame;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        final Action oldParam = oldActionList.get(oldItemPosition);
        final Action newParam = newActionList.get(newItemPosition);
        int a = oldParam.compareTo(newParam);
        if (a == 0) {
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        // Implement method if you're going to use ItemAnimator
        return super.getChangePayload(oldItemPosition, newItemPosition);
    }
}
