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

import jpmp.manager.DeviceManager;

import org.coderthoughts.phototools.api.AboutInfo;
import org.coderthoughts.phototools.api.PhotoSource;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class Activator implements BundleActivator {
    private volatile DeviceManager deviceManager;

    @Override
    public void start(final BundleContext context) throws Exception {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    deviceManager = DeviceManager.getInstance();
                    deviceManager.createInstance();
                    deviceManager.scanDevices();
                } catch (Throwable e) {
                    e.printStackTrace();
                    try {
                        context.getBundle().stop();
                    } catch (BundleException be) {
                        be.printStackTrace();
                    }
                }
            }
        }).start();

        context.registerService(PhotoSource.class.getName(), new MTPPhotoSource(this), null);

        context.registerService(AboutInfo.class.getName(), new MyAboutInfo(), null);
    }

    DeviceManager getDeviceManager() {
        return deviceManager;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }
}
