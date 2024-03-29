package magit;

import settings.Settings;
import utils.WarpBasicFile;
import utils.eUserMergeChoice;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BlobMap {
    private Map<BasicFile, Blob> map;

    public BlobMap(Map<BasicFile, Blob> fileBlobMap) {
        if (fileBlobMap != null) {
            this.map = fileBlobMap;
        } else
            this.map = new HashMap<>();
    }

    public Map<BasicFile, Blob> getMap() {
        return map;
    }

    public Blob getRandomBlob() {
        for (Map.Entry<BasicFile, Blob> blob : map.entrySet()) {
            return blob.getValue();
        }

        return null;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            Blob blob = entry.getValue();
            String type = blob.getType() == eFileTypes.FOLDER ? Settings.FOLDER_TYPE_IN_FOLDER_TABLE : Settings.FILE_TYPE_IN_FOLDER_TABLE;
            stringBuilder
                    .append(blob.getName()).append(Settings.FOLDER_TABLE_DELIMITER)
                    .append(blob.getSHA_ONE()).append(Settings.FOLDER_TABLE_DELIMITER)
                    .append(type).append(Settings.FOLDER_TABLE_DELIMITER)
                    .append(blob.getEditorName()).append(Settings.FOLDER_TABLE_DELIMITER)
                    .append(new SimpleDateFormat(Settings.DATE_FORMAT).format(blob.getDate())).append(System.lineSeparator());
        }

        return stringBuilder.toString();
    }

    public void addToMap(Blob blob) {
        map.put(blob, blob);
    }

    public void remove(BasicFile file) {
        if (map.remove(file) == null) //file not exist in map
        {
            for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
                if (entry.getValue().getType() == eFileTypes.FOLDER) {
                    ((Folder) entry.getValue()).getBlobMap().remove(file);
                }
            }
        }
    }

    public boolean replace(BasicFile file) {
        boolean flag = false;

        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            if (entry.getValue().equals(file)) {
                map.remove(entry.getValue());
                map.put(file, (Blob) file);
                return true;
            } else if (entry.getValue().getType() == eFileTypes.FOLDER) {
                flag = ((Folder) entry.getValue()).getBlobMap().replace(file);
                if (flag)
                    return true;
            }
        }

        return flag;
    }

    public void addNew(BasicFile file, Folder root) {
        if (file.getRootFolder().equals(root))
            map.put(file, (Blob) file);
        else {
            for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
                if (entry.getValue().getType() == eFileTypes.FOLDER) {
                    ((Folder) entry.getValue()).getBlobMap().addNew(file, (Folder) entry.getValue());
                }
            }
        }
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public String getBasicData() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            Blob blob = entry.getValue();
            stringBuilder
                    .append(blob.getName()).append(Settings.FOLDER_TABLE_DELIMITER)
                    .append(blob.getSHA_ONE()).append(Settings.FOLDER_TABLE_DELIMITER)
                    .append(System.lineSeparator());
        }

        return stringBuilder.toString();
    }

    public Map<BasicFile, Blob> getAllFiles() {
        Map<BasicFile, Blob> allFiles = new HashMap<>();
        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            if (entry.getValue().getType() == eFileTypes.FILE) {
                Blob blob = entry.getValue();
                allFiles.put(blob, blob);
            } else {
                allFiles.putAll(((Folder) entry.getKey()).getBlobMap().getAllFiles());
                allFiles.put(entry.getValue(), entry.getValue());
            }
        }

        return allFiles;
    }

    public boolean contain(Blob value, boolean isInRootFolder, WarpBasicFile pointer) {
        if (isInRootFolder) {
            pointer.setFile(map.get(value));
            return pointer.getFile() != null;
        }

        for (Map.Entry<BasicFile, Blob> entryFolder : map.entrySet()) {
            if (entryFolder.getValue().getType() == eFileTypes.FOLDER) {
                if (((Folder) entryFolder.getValue()).getBlobMap().contain(value, false, pointer)) {
                    return pointer.getFile() != null;
                }
            } else {
                this.contain(value, true, pointer);
            }
        }

        return pointer.getFile() != null;
    }

    public Map<eUserMergeChoice, Blob> getDuplicate(Blob value, Map<String, BlobMap> changes) {
        WarpBasicFile pointerAncestor = new WarpBasicFile(null),
                pointerActive = new WarpBasicFile(null),
                pointerTarget = new WarpBasicFile(null);

        BlobMap ancestorFileTree = changes.get(Settings.KEY_ANCESTOR_MAP),
                activeFileTree = changes.get(Settings.KEY_ACTIVE_MAP),
                targetFileTree = changes.get(Settings.KEY_TARGET_MAP);

        ancestorFileTree.contain(value, value.getRootFolder().getRootFolder() == null, pointerAncestor);
        activeFileTree.contain(value, value.getRootFolder().getRootFolder() == null, pointerActive);
        targetFileTree.contain(value, value.getRootFolder().getRootFolder() == null, pointerTarget);

        Map<eUserMergeChoice, Blob> map = new HashMap<>();
        map.put(eUserMergeChoice.ACTIVE, pointerActive.getFile());
        map.put(eUserMergeChoice.ANCESTOR, pointerAncestor.getFile());
        map.put(eUserMergeChoice.TARGET, pointerTarget.getFile());

        return map;
    }

    public List<Blob> toList() {
        List<Blob> blobList = new LinkedList<>();

        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            blobList.add(entry.getValue());
        }

        return blobList;
    }

    public void merge(BlobMap blobMap) {
        for (Map.Entry<BasicFile, Blob> entry : blobMap.getMap().entrySet()) {
            map.put(entry.getValue(), entry.getValue());
        }
    }

    public BlobMap getOnlyFolders() {
        BlobMap foldersOnly = new BlobMap(null);

        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            if (entry.getValue().getType() == eFileTypes.FOLDER) {
                foldersOnly.addToMap(entry.getValue());
                foldersOnly.getMap().putAll(((Folder) entry.getValue()).getBlobMap().getOnlyFolders().getMap());
            }
        }

        return foldersOnly;
    }

    public int getSize() {
        int size = map.size();
        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            if (entry.getValue().getType() == eFileTypes.FOLDER) {
                size += ((Folder) entry.getValue()).getBlobMap().getSize();
            }
        }

        return size;
    }

    public Blob findFile(String filePath) {
        File file = new File(filePath);

        List<String> folders = new LinkedList<>();
        boolean flag = true;
        int i = 0;

        while(flag) {
            try {
                String part = file.toPath().getName(i).toString();
                folders.add(part);
                i++;
            } catch (IllegalArgumentException e) {
                flag = false;
            }
        }

            while (i >= 0) {
                i--;
                Map<BasicFile, Blob> currentMap = map;
                for (Map.Entry<BasicFile, Blob> entry : currentMap.entrySet()) {
                    if (entry.getValue().getName().equals(folders.get(i))) {
                        if (entry.getValue().type == eFileTypes.FILE) {
                            return entry.getValue();
                        } else {
                            currentMap = ((Folder)entry.getValue()).getBlobMap().getMap();
                            break;
                        }
                    }
                }
            }

        return new Blob();
    }
}