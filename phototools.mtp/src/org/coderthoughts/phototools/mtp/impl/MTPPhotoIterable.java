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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import jpmp.device.UsbDevice;
import jpmp.notifier.IDeviceTransferNotifier;
import jpmp.notifier.IParseTreeNotifier;

import org.coderthoughts.phototools.api.PhotoIterable;

public class MTPPhotoIterable implements PhotoIterable {
    static final String DEVICE_SEPARATOR = "->";
    private final UsbDevice device;
    private final String rootLocation;
    private final Map<String, String> files = new TreeMap<String, String>();
    private String[] extensions = null;
    boolean frozen;

    public MTPPhotoIterable(UsbDevice device, String rootLocation) {
        this.device = device;
        this.rootLocation = rootLocation;
    }

    @Override
    public MTPPhotoIterable freeze() {
        if (!frozen) {
            frozen = true;
            initFiles(rootLocation);
        }
        return this;
    }

    private void initFiles(final String location) {
        device.parseFolder(location, new IParseTreeNotifier() {
            @Override
            public long addFolder(String folderName, String mtpItemIid) {
                initFiles(location + "/" + folderName);
                return 0;
            }

            @Override
            public long addFile(String fileName, String mtpItemIid) {
                boolean allowedExtension = false;
                if (extensions != null) {
                    for (String ext : extensions) {
                        if (fileName.toLowerCase().endsWith(ext.toLowerCase())) {
                            allowedExtension = true;
                            break;
                        }
                    }
                }

                if (extensions == null || allowedExtension)
                    files.put(location + "/" + fileName, mtpItemIid);

                return 0;
            }
        });
    }

    @Override
    public String getLocationString() {
        return device.getName() + DEVICE_SEPARATOR + rootLocation;
    }

    @Override
    public MTPPhotoIterable setExtensions(String... extensions) {
        if (frozen)
            throw new IllegalStateException("This method cannot be called after init() is called");

        this.extensions = extensions;
        return this;
    }

    @Override
    public Iterator<Entry> iterator() {
        return new Iterator<Entry>() {
            private Iterator<String> it = files.keySet().iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Entry next() {
                final String path = it.next();
                String name = path;
                int idx = name.lastIndexOf('/');
                if (idx >= 0)
                    name = name.substring(idx + 1);

                try {
                    File tempFile = File.createTempFile("Photocopy", ".tmp");
                    device.getFile(tempFile.getAbsolutePath(), path, new IDeviceTransferNotifier() {
                        @Override
                        public void notifyEnd() {
                            System.out.println("done.");
                        }

                        @Override
                        public void notifyCurrent(long position) {
                            System.out.print(".");
                        }

                        @Override
                        public void notifyBegin(long estimatedSize) {}

                        @Override
                        public boolean getAbort() {
                            return false;
                        }
                    });
                    return new Entry(name, null, new DeleteOnCloseInputStream(tempFile));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static class DeleteOnCloseInputStream extends FileInputStream {
        private final File file;

        public DeleteOnCloseInputStream(File file) throws FileNotFoundException {
            super(file);
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            super.close();
            file.delete();
        }
    }
}
