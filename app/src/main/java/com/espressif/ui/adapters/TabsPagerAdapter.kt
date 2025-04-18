// Copyright 2023 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.adapters

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.ui.fragments.LoginFragment
import com.espressif.ui.fragments.SignUpFragment

class TabsPagerAdapter(val context: Context, fm: FragmentManager) : FragmentPagerAdapter(fm) {

    @StringRes
    private val TAB_TITLES = intArrayOf(R.string.tab_login, R.string.tab_sign_up)

    override fun getCount(): Int {
        // Show 2 pages.

        if (BuildConfig.isChinaRegion) {
            return 1
        }
        return 2
    }

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> LoginFragment()
            1 -> SignUpFragment()
            else -> LoginFragment()
        }
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.resources.getString(TAB_TITLES[position])
    }
}
