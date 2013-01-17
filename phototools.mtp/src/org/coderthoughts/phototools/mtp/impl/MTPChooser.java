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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import jpmp.device.UsbDevice;
import jpmp.manager.DeviceManager;
import jpmp.notifier.IParseTreeNotifier;

import org.coderthoughts.phototools.api.PhotoIterable;

@SuppressWarnings("serial")
public class MTPChooser extends JDialog implements TreeWillExpandListener, TreeExpansionListener {
    private final JTree tree;
    private final SortedTreeModel treeModel;
    private String selectedLocation;
    private UsbDevice selectedDevice;
    private JLabel statusLabel;

    public static PhotoIterable openSelectionDialog(Window parent, DeviceManager deviceManager) {
        MTPChooser chooser = new MTPChooser(parent, deviceManager);
        chooser.setModal(true);
        chooser.setVisible(true);
        if (chooser.selectedLocation != null)
            return new MTPPhotoIterable(chooser.selectedDevice, chooser.selectedLocation);
        else
            return null;
    }

    MTPChooser(Window parent, DeviceManager deviceManager) {
        super(parent);
        setTitle("Select source location");

        JPanel panel = new JPanel(new BorderLayout());
        setContentPane(panel);

        JPanel treePanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(treePanel);
        panel.add(scrollPane, BorderLayout.CENTER);

        DefaultMutableTreeNode topNode = new DefaultMutableTreeNode("Top");
        treeModel = new SortedTreeModel(topNode);

        @SuppressWarnings("rawtypes")
        Map list = deviceManager.getDeviceList();

        for (Object device : list.values()) {
            UsbDevice usbDevice = (UsbDevice) device;
            TreeNode node = new TreeNode(usbDevice.getManufacturer() + " " + usbDevice.getName(), usbDevice, "/");
            treeModel.insertNode(node, topNode);
            addChildNodes(node);
        }

        tree = new JTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(false);
        tree.addTreeExpansionListener(this);
        tree.addTreeWillExpandListener(this);
        treePanel.add(tree, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        panel.add(bottomPanel, BorderLayout.SOUTH);

        statusLabel = new JLabel();
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        JButton okBtn = new JButton("OK");
        okBtn.setDefaultCapable(true);
        okBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateSelectedLocation();
                setVisible(false);
            }
        });
        btnPanel.add(okBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedLocation = null;
                setVisible(false);
            }
        });
        btnPanel.add(cancelBtn);
        bottomPanel.add(btnPanel, BorderLayout.EAST);

        setSize(400, 400);
    }

    private void addChildNodes(final TreeNode node) {
        final UsbDevice device = node.getDevice();
        device.parseFolder(node.getFullPath(), new IParseTreeNotifier() {
            @Override
            public long addFolder(String folderName, String mtpItemIid) {
                if (!folderName.startsWith(".")) {
                    String fullPath = node.getFullPath() + "/" + folderName;
                    if (fullPath.startsWith("//"))
                        fullPath = fullPath.substring(1);
                    TreeNode folderNode = new TreeNode(folderName, device, fullPath);
                    treeModel.insertNode(folderNode, node);
                    treeModel.insertNode(new DummyFileTreeNode(device), folderNode);
                }
                return 0;
            }

            @Override
            public long addFile(final String fileName, String mtpItemIid) {
                if (!fileName.startsWith("."))
                    treeModel.insertNode(new TreeNode(fileName, device), node);
                return 0;
            }
        });
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent arg0) throws ExpandVetoException {}

    @Override
    public void treeWillExpand(TreeExpansionEvent tee)
            throws ExpandVetoException {
        Object node = tee.getPath().getLastPathComponent();
        if (node instanceof TreeNode) {
            final TreeNode tn = (TreeNode) node;
            if (!tn.isProcessed()) {
                tn.setProcessed();
                final Object dummyChild = tn.children().nextElement();
                if (dummyChild instanceof DummyFileTreeNode) {
                    statusLabel.setText("Finding children of: " + tn.getName() + " ...");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            addChildNodes(tn);
                            if (tn.getChildCount() == 1) {
                                treeModel.insertNode(new EmptyFolderTreeNode(tn.getDevice()), tn);
                            }
                            treeModel.removeNodeFromParent((DefaultMutableTreeNode) dummyChild);
                            statusLabel.setText("");
                        }
                    }).start();
                }
            }
        }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent arg0) {}

    @Override
    public void treeExpanded(TreeExpansionEvent tee) {}

    private String updateSelectedLocation() {
        TreePath path = tree.getSelectionPath();

        UsbDevice d = null;
        String p = null;
        for (Object obj : path.getPath()) {
            if (obj instanceof TreeNode == false)
                continue;

            TreeNode tn = (TreeNode) obj;
            d = tn.getDevice();
            p = tn.getFullPath();
        }
        selectedDevice = d;
        selectedLocation = p;
        return tree.getSelectionPath().toString();
    }

    static class TreeNode extends DefaultMutableTreeNode {
        private final UsbDevice device;
        private final String name;
        private final String fullPath;
        private boolean processed = false;

        TreeNode(String name, UsbDevice dev) {
            this(name, dev, null);
        }

        TreeNode(String name, UsbDevice dev, String path) {
            super(name);
            this.name = name;
            this.device = dev;
            this.fullPath = path;
        }

        UsbDevice getDevice() {
            return device;
        }

        String getFullPath() {
            return fullPath;
        }

        String getName() {
            return name;
        }

        boolean isFolder() {
            return fullPath != null;
        }

        boolean isProcessed() {
            return processed;
        }

        void setProcessed() {
            processed = true;
        }
    }

    static class DummyFileTreeNode extends TreeNode {
        DummyFileTreeNode(UsbDevice dev) {
            super("Initializing...", dev);
        }
    }

    static class EmptyFolderTreeNode extends TreeNode {
        EmptyFolderTreeNode(UsbDevice dev) {
            super("<Empty Folder>", dev);
        }
    }

    static class SortedTreeModel extends DefaultTreeModel {
        public SortedTreeModel(javax.swing.tree.TreeNode root) {
            super(root);
        }

        synchronized void insertNode(MutableTreeNode newChild, MutableTreeNode parent) {
            int childCount = parent.getChildCount();
            if (newChild instanceof TreeNode) {
                String newChildName = ((TreeNode) newChild).getName();

                for (int i = 0; i < childCount; i++) {
                    Object c = parent.getChildAt(i);
                    if (c instanceof TreeNode) {
                        String childName = ((TreeNode) c).getName();
                        if (newChildName.compareTo(childName) < 0) {
                            insertNodeInto(newChild, parent, i);
                            return;
                        }
                    }
                }
            }
            insertNodeInto(newChild, parent, childCount);
        }
    }
}
