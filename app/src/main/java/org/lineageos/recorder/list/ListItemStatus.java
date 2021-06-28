/*
 * Copyright (C) 2021 The LineageOS Project
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
package org.lineageos.recorder.list;

import androidx.annotation.IntDef;

@IntDef(value = {
        ListItemStatus.DEFAULT,
        ListItemStatus.UNCHECKED,
        ListItemStatus.CHECKED,
})
public @interface ListItemStatus {
    int DEFAULT = 0;
    int UNCHECKED = 1;
    int CHECKED = 2;
}
