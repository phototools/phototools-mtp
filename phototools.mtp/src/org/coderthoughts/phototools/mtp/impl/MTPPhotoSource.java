/*
 * Licensed under the Apache License, Version 2.0 (the "License").
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
package org.coderthoughts.phototools.mtp.impl;

import java.awt.Window;

import org.coderthoughts.phototools.api.PhotoIterable;
import org.coderthoughts.phototools.api.PhotoSource;

public class MTPPhotoSource implements PhotoSource {
    private final Activator activator;

    public MTPPhotoSource(Activator activator) {
        this.activator = activator;
    }

    @Override
    public String getLabel() {
        return "Mobile Device via USB";
    }

    @Override
    public PhotoIterable getPhotoIterable(Window parentWindow, String initialSelection) {
        return MTPChooser.openSelectionDialog(parentWindow, activator.getDeviceManager());
    }

    @Override
    public PhotoIterable getPhotoIterableFromLocation(String location) {
        int idx = location.indexOf(MTPPhotoIterable.DEVICE_SEPARATOR);
        if (idx < 0)
            return null;

        String deviceName = location.substring(0, idx);
        String rootLocation = location.substring(idx + MTPPhotoIterable.DEVICE_SEPARATOR.length());
        return null;
    }
}
