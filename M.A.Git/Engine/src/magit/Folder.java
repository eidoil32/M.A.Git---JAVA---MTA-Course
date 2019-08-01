package magit;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class Folder extends Blob {
    private BlobMap blobMap;

    public Folder(Path filePath, String editor) throws IOException {
        super(filePath, editor);
        setType(eFileTypes.FOLDER);
        // this line is to create the Comparator of BasicFile names :
        blobMap = new BlobMap(new TreeMap<>(Comparator.comparing(BasicFile::getFullPathName)));
    }

    public Folder() {}

    public void AddBlob(Blob blob) {
        blobMap.getMap().put(blob, blob);
    }

    public BlobMap getBlobMap() {
        return blobMap;
    }

    public void setBlobMap(BlobMap blobMap) {
        this.blobMap = blobMap;
    }

    public void calcFolderSHAONE()
    {
        for (Map.Entry<BasicFile,Blob> entry : blobMap.getMap().entrySet())
        {
            if(entry.getValue().getType() == eFileTypes.FOLDER)
                ((Folder)entry.getValue()).calcFolderSHAONE();
        }
        this.setContent(blobMap.toString());
        this.setSHA_ONE(DigestUtils.sha1Hex(getContent()));
    }
}
