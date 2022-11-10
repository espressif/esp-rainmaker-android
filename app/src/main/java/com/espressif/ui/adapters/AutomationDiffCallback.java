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

import com.espressif.ui.models.Automation;

import java.util.List;

public class AutomationDiffCallback extends DiffUtil.Callback {

    private final List<Automation> oldAutomationList;
    private final List<Automation> newAutomationList;

    public AutomationDiffCallback(List<Automation> oldAutomationList, List<Automation> newAutomationList) {
        this.oldAutomationList = oldAutomationList;
        this.newAutomationList = newAutomationList;
    }

    @Override
    public int getOldListSize() {
        return oldAutomationList.size();
    }

    @Override
    public int getNewListSize() {
        return newAutomationList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        boolean areItemsTheSame = oldAutomationList.get(oldItemPosition).getName().equals(newAutomationList.get(newItemPosition).getName());
        return areItemsTheSame;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        final Automation oldAutomation = oldAutomationList.get(oldItemPosition);
        final Automation newAutomation = newAutomationList.get(newItemPosition);
        int a = oldAutomation.compareTo(newAutomation);
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
