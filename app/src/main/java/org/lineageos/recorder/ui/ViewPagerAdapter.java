/*
 * Copyright (C) 2017 The LineageOS Project
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
package org.lineageos.recorder.ui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentPagerAdapter {
    public interface PageProvider {
        int getCount();
        Fragment createPage(int index);
        String getPageTitle(int index);
    }

    private final SparseArray<Fragment> mKnownFragments = new SparseArray<>();
    private final PageProvider mProvider;

    public ViewPagerAdapter(FragmentManager mManager, PageProvider provider) {
        super(mManager);
        mProvider = provider;
    }

    @Override
    public Fragment getItem(int mPosition) {
        return mProvider.createPage(mPosition);
    }

    @Override
    public int getCount() {
        return mProvider.getCount();
    }

    @Override
    public CharSequence getPageTitle(int mPosition) {
        return mProvider.getPageTitle(mPosition);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        mKnownFragments.put(position, fragment);
        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        mKnownFragments.remove(position);
        super.destroyItem(container, position, object);
    }

    public Fragment getFragment(int position) {
        return mKnownFragments.get(position);
    }
}
