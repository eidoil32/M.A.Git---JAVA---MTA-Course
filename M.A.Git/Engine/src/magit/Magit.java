package magit;


import com.google.gson.Gson;
import exceptions.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import puk.team.course.magit.ancestor.finder.AncestorFinder;
import settings.Settings;
import utils.*;
import xml.basic.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Magit {
    private Path rootFolder;
    private Branch currentBranch;
    private String currentUser;
    private Repository currentRepository;
    private Magit remoteRepository;

    public Magit() {
        this.currentUser = Settings.language.getString("USER_ADMINISTRATOR");
        this.currentBranch = null;
        this.rootFolder = null;
        this.currentRepository = null;
        this.remoteRepository = null;
    }

    public Path getRootFolder() {
        return rootFolder;
    }

    public void setRootFolder(Path rootFolder) {
        this.rootFolder = rootFolder;
    }

    public Magit getRemoteRepository() {
        return remoteRepository;
    }

    private void setRemoteRepository(Magit remoteRepository) throws IOException {
        this.remoteRepository = remoteRepository;
        createRemoteRepositoryFile(
                remoteRepository.getRootFolder().toString(),
                remoteRepository.getCurrentRepository().getName(),
                currentRepository.getMagitPath().toString());
    }

    public static void createRemoteRepositoryFile(String path, String name, String currentPath) throws IOException {
        File remoteRepositoryData = new File(currentPath + File.separator + Settings.REMOTE_REPOSITORY_FILE_DATA);

        if (!remoteRepositoryData.exists()) {
            remoteRepositoryData.createNewFile();
        }

        PrintWriter writer = new PrintWriter(remoteRepositoryData);
        writer.write(path);
        writer.write(System.lineSeparator() + name);
        writer.close();
    }


    public Branch getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(Branch currentBranch) throws RepositoryException {
        this.currentBranch = currentBranch;
        File head = new File(currentRepository.getBranchesPath() + File.separator + Settings.MAGIT_BRANCH_HEAD);
        if (head.exists()) {
            try {
                PrintWriter writer = new PrintWriter(head);
                writer.print(currentBranch.getName());
                writer.close();
            } catch (FileNotFoundException e) {
                throw new RepositoryException(eErrorCodes.OPEN_FILE_FAILED);
            }

        } else
            throw new RepositoryException(eErrorCodes.OPEN_FILE_FAILED);
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }

    public Repository getCurrentRepository() {
        return currentRepository;
    }

    public void setCurrentRepository(Repository currentRepository) throws IOException {
        this.currentRepository = currentRepository;
        this.currentBranch = currentRepository.getActiveBranch();
        if (currentRepository.getRootFolder() != null)
            this.rootFolder = currentRepository.getRootFolder().getFilePath();
    }

    public void commitMagit(String currentUser, String comment) throws IOException, MyFileException, RepositoryException {
        Commit newCommit = new Commit(currentBranch.getCommit());
        commit(currentUser, comment, newCommit);
    }

    private void commitMagit(String currentUser, String comment, Commit anotherPrevCommit) throws IOException, MyFileException, RepositoryException {
        Commit newCommit = new Commit(currentBranch.getCommit());
        newCommit.addPrevCommit(anotherPrevCommit);
        commit(currentUser, comment, newCommit);
    }

    public void commit(String currentUser, String comment, Commit newCommit) throws IOException, MyFileException, RepositoryException {
        newCommit.createCommitFile(currentRepository, currentRepository.scanRepository(currentUser), currentUser, comment);
        currentBranch.setCommit(newCommit, currentRepository.getBranchesPath().toString());
        currentRepository.setLastCommit(newCommit);
    }

    private void deleteOldFiles(String rootPath) {
        File root = new File(rootPath);
        if (root.exists()) {
            if (root.isDirectory()) {
                File[] files = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.getName().equals(Settings.MAGIT_FOLDER)) {
                            if (file.isDirectory())
                                deleteOldFiles(file.getAbsolutePath());
                            file.delete();
                        }
                    }
                }
            }
        }
    }

    public boolean tryCheckout(String branchName) throws RepositoryException, MyFileException, IOException {
        Map<MapKeys, List<BasicFile>> delta = currentRepository.scanRepository(currentUser);
        if (delta == null) {
            return checkout(branchName);
        }
        return false;
    }

    private void setNewActiveBranch(Branch branch) throws RepositoryException, FileNotFoundException {
        currentBranch = branch;
        File head = new File(currentRepository.getBranchesPath().toString() + File.separator + Settings.MAGIT_BRANCH_HEAD);
        if (head.exists()) {
            PrintWriter writer = new PrintWriter(head);
            writer.print(branch.getName());
            writer.close();
        } else {
            throw new RepositoryException(eErrorCodes.MAGIT_FOLDER_CORRUPTED);
        }
    }

    private void layoutRepositoryByRootFolder(Map<BasicFile, Blob> map, String copyToOtherPath) throws IOException {
        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            Blob temp = entry.getValue();
            String path = copyToOtherPath != null ? copyToOtherPath + File.separator + temp.getName() : temp.getFullPathName();
            File file = new File(path);
            if (temp.getType() == eFileTypes.FOLDER) {
                file.mkdirs();
                layoutRepositoryByRootFolder(((Folder) temp).getBlobMap().getMap(), file.getPath());
            } else {
                file.createNewFile();
                PrintWriter writer = new PrintWriter(file);
                writer.print(temp.getContent());
                writer.close();
            }
        }
    }

    private void removeBranch(String branchName) {
        currentRepository.removeBranch(branchName);
    }

    public String showCurrentCommitHistory() throws RepositoryException, IOException, MyFileException {
        Folder rootFolder = currentRepository.getRootFolder();
        if (rootFolder.getBlobMap().isEmpty()) {
            rootFolder.setBlobMap(currentRepository.loadDataFromCommit(currentRepository.getLastCommit()));
        }

        return getFullDataFromBlobMap(rootFolder.getBlobMap().getMap());
    }

    private String getFullDataFromBlobMap(Map<BasicFile, Blob> map) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Map.Entry<BasicFile, Blob> entry : map.entrySet()) {
            Blob blob = entry.getValue();
            if (blob.getType() == eFileTypes.FOLDER) {
                stringBuilder.append(getFullDataFromBlobMap(((Folder) blob).getBlobMap().getMap())).append(System.lineSeparator());
            }
            stringBuilder.append(blob).append(System.lineSeparator());
        }

        return stringBuilder.toString();
    }

    public boolean checkout(String branchName) throws RepositoryException, IOException, MyFileException {
        if (new File(currentRepository.getBranchesPath() + File.separator + branchName).exists()) {
            Branch branch = currentRepository.searchBranch(branchName);
            if (branch == null)
                throw new MyFileException(eErrorCodes.BRANCH_NOT_EXIST, branchName);
            else {
                setNewActiveBranch(branch);
                deleteOldFiles(this.rootFolder.toString());
                currentRepository.getRootFolder().setBlobMap(currentRepository.loadDataFromCommit(branch.getCommit()));
                layoutRepositoryByRootFolder(currentRepository.getRootFolder().getBlobMap().getMap(), null);
                return true;
            }
        } else {
            throw new RepositoryException(eErrorCodes.BRANCH_NOT_EXIST);
        }
    }

    public Commit changeBranchPoint(Branch branch, String user_sha_one, boolean doCheckout) throws RepositoryException, IOException, MyFileException {
        if (user_sha_one.length() == 40) {
            Commit commit = loadCommitBySHA(user_sha_one);
            if (commit == null) {
                throw new RepositoryException(eErrorCodes.NO_COMMIT_WITH_SHA_ONE_EXISTS);
            } else {
                Commit oldCommit = branch.getCommit();
                if (currentRepository.scanRepository(currentUser) != null && doCheckout) { //there is opened changes
                    return oldCommit;
                } else { // there's no opened changes or no need to do checkout
                    branch.setCommit(commit, currentRepository.getBranchesPath().toString());
                    if (doCheckout && checkout(branch.getName())) {
                        currentRepository.setLastCommit(branch.getCommit());
                        return null;
                    } else if (!doCheckout) {
                        throw new RepositoryException(eErrorCodes.UNKNOWN_ERROR);
                    } else {
                        return null;
                    }
                }
            }
        } else {
            throw new RepositoryException(eErrorCodes.NOT_SHA_ONE);
        }
    }

    private Commit loadCommitBySHA(String user_sha_one) throws IOException, RepositoryException {
        Path pathToObject = currentRepository.getObjectPath();
        File commit = new File(pathToObject + File.separator + user_sha_one);
        if (commit.exists()) {
            List<String> commitContent = Files.readAllLines(commit.toPath());
            if (checkIsRealCommitFile(commitContent)) {
                Commit recoveredCommit = new Commit();
                recoveredCommit.loadDataFromFile(pathToObject, user_sha_one);
                return recoveredCommit;
            } else {
                return null;
            }
        }
        return null;
    }

    private boolean checkIsRealCommitFile(List<String> lines) throws IOException, RepositoryException {
        if (lines.size() >= 6) {
            if (lines.get(0).length() != 40) {
                return false;
            } else {
                String prevCommit = lines.get(1);
                if (!prevCommit.equals(Settings.EMPTY_COMMIT)) {
                    if (prevCommit.length() != 40) {
                        return false;
                    } else {
                        if (loadCommitBySHA(prevCommit) == null) {
                            return false;
                        }
                    }
                }
            }
            try {
                new SimpleDateFormat(Settings.DATE_FORMAT).parse(lines.get(3));
                return true;
            } catch (ParseException e) {
                return false;
            }
        }
        return false;
    }

    public void recoverOldCommit(Commit oldCommit) throws RepositoryException {
        currentBranch.setCommit(oldCommit, currentRepository.getBranchesPath().toString());
    }

    public void afterXMLLayout() throws IOException, RepositoryException {
        if (currentRepository.getRootFolder() != null) {
            layoutRepositoryByRootFolder(currentRepository.getRootFolder().getBlobMap().getMap(), null);
        }

        if (currentRepository.getRemoteTrackingBranches() != null) {
            this.remoteRepository = loadRemoteRepository();
        }
    }

    private Magit loadRemoteRepository() throws IOException, RepositoryException {
        File remoteRepository = new File(currentRepository.getMagitPath() + File.separator + Settings.REMOTE_REPOSITORY_FILE_DATA);
        List<String> content = Files.readAllLines(remoteRepository.toPath());
        Magit magit = new Magit();
        Repository remote = new Repository(Paths.get(content.get(0)), currentUser, null, true);
        magit.setCurrentRepository(remote);
        return magit;
    }

    public void deleteOldMagitFolder(String path) {
        File[] listOfWC = new File(path).listFiles();
        if (listOfWC != null) {
            if (listOfWC.length != 0) {
                for (File file : listOfWC) {
                    deleteOldFiles(file.getPath());
                    file.delete();
                }
            }
        }
    }

    public static void basicCheckXML(MagitRepository magitRepository) throws MyXMLException {
        List<MagitBlob> blobs = magitRepository.getMagitBlobs().getMagitBlob();
        List<MagitSingleFolder> folders = magitRepository.getMagitFolders().getMagitSingleFolder();
        List<MagitSingleCommit> commits = magitRepository.getMagitCommits().getMagitSingleCommit();

        Map<Integer, Object> tempMap = new HashMap<>();

        for (MagitBlob blob : blobs) {
            if (tempMap.containsKey(Integer.parseInt(blob.getId()))) {
                throw new MyXMLException(eErrorCodesXML.DUPLICATE_ID_NUMBER, blob.getId());
            } else {
                tempMap.put(Integer.parseInt(blob.getId()), blob);
            }
        }

        tempMap = new HashMap<>();
        for (MagitSingleFolder folder : folders) {
            if (tempMap.containsKey(Integer.parseInt(folder.getId()))) {
                throw new MyXMLException(eErrorCodesXML.DUPLICATE_ID_NUMBER, folder.getId());
            } else {
                tempMap.put(Integer.parseInt(folder.getId()), folder);
            }
        }

        tempMap = new HashMap<>();
        for (MagitSingleCommit commit : commits) {
            if (tempMap.containsKey(Integer.parseInt(commit.getId()))) {
                throw new MyXMLException(eErrorCodesXML.DUPLICATE_ID_NUMBER, commit.getId());
            } else {
                tempMap.put(Integer.parseInt(commit.getId()), commit);
            }
        }

    }

    public void checkCleanDir(String path) throws RepositoryException {
        File target = new File(path);
        try {
            if (Files.list(target.toPath()).findFirst().isPresent())
                throw new RepositoryException(eErrorCodes.TARGET_DIR_NOT_EMPTY);
        } catch (IOException e) {
            throw new RepositoryException(eErrorCodes.OPEN_FILE_FAILED);
        }
    }

    public MagitRepository convertToXMLScheme() throws RepositoryException, IOException, MyFileException {
        WarpInteger counterCommits = new WarpInteger(),
                counterBlobs = new WarpInteger(), counterFolders = new WarpInteger();
        MagitRepository exportRepository = new MagitRepository();
        exportRepository.setLocation(rootFolder.toString());
        exportRepository.setName(currentRepository.getName());

        Map<WarpBasicFile, Integer> myBlobs = new HashMap<>();
        Map<WarpBasicFile, Integer> myFolders = new HashMap<>();
        Map<Commit, Integer> myCommits = new HashMap<>();
        Map<String, Object> values = new HashMap<>();
        values.put(Settings.KEY_COUNTER_FILES, counterBlobs);
        values.put(Settings.KEY_COUNTER_FOLDERS, counterFolders);
        values.put(Settings.KEY_ROOT_FOLDER_PATH, rootFolder.toString());
        values.put(Settings.KEY_ALL_FILES, myBlobs);
        values.put(Settings.KEY_ALL_FOLDERS, myFolders);
        values.put(Settings.KEY_COUNTER_COMMIT, counterCommits);
        values.put(Settings.KEY_ALL_COMMITS, myCommits);

        MagitBranches branchManager = new MagitBranches();
        MagitCommits commitsManager = new MagitCommits();
        branchManager.setHead(currentBranch.getName());

        List<MagitSingleBranch> branches = branchManager.getMagitSingleBranch();
        List<MagitSingleCommit> commits = commitsManager.getMagitSingleCommit();

        for (Branch branch : currentRepository.getAllBranches()) {
            if (!branch.getName().equals(Settings.MAGIT_BRANCH_HEAD)) {
                MagitSingleBranch tempBranch = new MagitSingleBranch();
                Commit realCommit = branch.getCommit();
                tempBranch.setName(branch.getName());
                MagitSingleBranch.PointedCommit pointedCommit = new MagitSingleBranch.PointedCommit();
                if (realCommit != null) {
                    myCommits.put(realCommit, counterCommits.number);
                    commits.addAll(realCommit.convertToXMLCommit(currentRepository, currentRepository.loadDataFromCommit(realCommit), values));
                    int temp = counterCommits.number;
                    pointedCommit.setId(Integer.toString(temp - 1));
                } else {
                    pointedCommit.setId(Settings.EMPTY_STRING);
                }
                tempBranch.setPointedCommit(pointedCommit);
                if (branch instanceof RemoteTrackingBranch) {
                    MagitSingleBranch temp = createRemoteXML(remoteRepository.getCurrentRepository().getName(), tempBranch);
                    branches.add(temp);
                    tempBranch.setTracking(true);
                    tempBranch.setTrackingAfter(temp.getName());
                }
                tempBranch.setIsRemote(branch.isIsRemote());
                branches.add(tempBranch);
            }
        }

        exportRepository.setMagitCommits(commitsManager);
        exportRepository.setMagitBranches(branchManager);
        exportRepository.setMagitBlobs(parseMagitBlobsToXMLBlobs(myBlobs));
        exportRepository.setMagitFolders(parseMagitFoldersToXMLFolders(myFolders, myBlobs));

        if (this.remoteRepository != null) {
            MagitRepository.MagitRemoteReference remoteReference = new MagitRepository.MagitRemoteReference();
            remoteReference.setLocation(remoteRepository.getRootFolder().toString());
            remoteReference.setName(remoteRepository.getCurrentRepository().getName());
            exportRepository.setMagitRemoteReference(remoteReference);
        }
        return exportRepository;
    }

    private MagitSingleBranch createRemoteXML(String remoteRepositoryName, MagitSingleBranch branch) {
        MagitSingleBranch temp = new MagitSingleBranch();
        temp.setIsRemote(true);
        temp.setPointedCommit(branch.getPointedCommit());
        temp.setName(remoteRepositoryName + File.separator + branch.getName());
        return temp;
    }

    private MagitBlobs parseMagitBlobsToXMLBlobs(Map<WarpBasicFile, Integer> myBlobs) {
        MagitBlobs blobs = new MagitBlobs();

        for (Map.Entry<WarpBasicFile, Integer> entry : myBlobs.entrySet()) {
            Blob tempBlob = entry.getKey().getFile();
            MagitBlob blob = new MagitBlob();
            blob.setId(entry.getValue().toString());
            blob.setLastUpdater(tempBlob.getEditorName());
            blob.setLastUpdateDate(new SimpleDateFormat(Settings.DATE_FORMAT).format(tempBlob.getDate()));
            blob.setContent(tempBlob.getContent());
            blob.setName(tempBlob.getName());
            blobs.getMagitBlob().add(blob);
        }

        return blobs;
    }

    private MagitFolders parseMagitFoldersToXMLFolders(Map<WarpBasicFile, Integer> myFolders, Map<WarpBasicFile, Integer> myBlobs) {
        MagitFolders folders = new MagitFolders();

        for (Map.Entry<WarpBasicFile, Integer> entry : myFolders.entrySet()) {
            Folder tempFolder = (Folder) entry.getKey().getFile();
            MagitSingleFolder folder = new MagitSingleFolder();
            folder.setId(entry.getValue().toString());
            folder.setName(tempFolder.getName());
            folder.setLastUpdater(tempFolder.getEditorName());
            folder.setItems(getItemsOfFolder(tempFolder, myFolders, myBlobs));
            folder.setLastUpdateDate(new SimpleDateFormat(Settings.DATE_FORMAT).format(tempFolder.getDate()));
            folder.setIsRoot(tempFolder.getRootFolder() == null);
            folders.getMagitSingleFolder().add(folder);
        }

        return folders;
    }

    private MagitSingleFolder.Items getItemsOfFolder(Folder tempFolder, Map<WarpBasicFile, Integer> myFolders, Map<WarpBasicFile, Integer> myBlobs) {
        MagitSingleFolder.Items listOfItems = new MagitSingleFolder.Items();

        for (Map.Entry<BasicFile, Blob> entry : tempFolder.getBlobMap().getMap().entrySet()) {
            Item temp = new Item();
            if (entry.getValue().getType() == eFileTypes.FILE) {
                temp.setId(myBlobs.get(new WarpBasicFile(entry.getValue())).toString());
                temp.setType(Settings.XML_ITEM_FILE_TYPE);
            } else {
                temp.setId(myFolders.get(new WarpBasicFile(entry.getValue())).toString());
                temp.setType(Settings.XML_ITEM_FOLDER_TYPE);
            }
            listOfItems.getItem().add(temp);
        }

        return listOfItems;
    }

    public void deleteBranch(String branchName) throws RepositoryException {
        if (branchName.equals(Settings.TEMP_BRANCH_FOR_EXPANDED_TREE)) {
            removeBranch(branchName);
        } else {
            if (currentBranch.getName().equals(branchName)) {
                throw new RepositoryException(eErrorCodes.CANNOT_DELETE_ACTIVE_BRANCH);
            } else {
                File branch = new File(currentRepository.getBranchesPath() + File.separator + branchName);
                if (branch.exists() && branch.getName().equals(branchName)) {
                    if (branch.delete()) {
                        removeBranch(branchName);
                    }
                } else {
                    throw new RepositoryException(eErrorCodes.BRANCH_NOT_EXIST);
                }
            }
        }
    }

    public void tryCreateNewBranch(String branchName, Commit commit) throws RepositoryException, IOException {
        File newBranch = new File(currentRepository.getBranchesPath() + File.separator + branchName);
        if (branchName.toLowerCase().equals(Settings.MAGIT_BRANCH_HEAD)) {
            throw new RepositoryException(eErrorCodes.FORBIDDEN_HEAD_NAME);
        }
        if (newBranch.exists()) {
            throw new RepositoryException(eErrorCodes.BRANCH_ALREADY_EXIST, branchName);
        } else {
            Branch branch = new Branch(branchName, commit == null ? currentBranch.getCommit() : commit, currentRepository.getBranchesPath().toString());
            PrintWriter writer = new PrintWriter(newBranch);
            if (branch.getCommit() != null)
                writer.print(branch.getCommit().getSHA_ONE());
            else
                writer.print(Settings.EMPTY_COMMIT);
            writer.close();
            currentRepository.addBranch(branch);
        }
    }

    public String showAllBranches() throws RepositoryException {
        List<Branch> branches = currentRepository.getBranches();
        if (branches.size() > 0) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Branch branch : branches) {
                if (!branch.getName().equals(Settings.MAGIT_BRANCH_HEAD)) {
                    if (branch.equals(currentBranch))
                        stringBuilder.append(Settings.language.getString("HEAD_ACTIVE_BRANCH_SIGN"));
                    stringBuilder.append(branch).append(System.lineSeparator());
                }
            }
            return stringBuilder.toString();
        } else {
            throw new RepositoryException(eErrorCodes.NOTHING_TO_SEE);
        }
    }

    public Map<MapKeys, List<String>> showCurrentStatus() throws RepositoryException, IOException, MyFileException {
        Map<MapKeys, List<String>> data = new HashMap<>();

        List<String> newItems = new LinkedList<>();
        List<String> deletedItems = new LinkedList<>();
        List<String> editedItems = new LinkedList<>();

        data.put(MapKeys.LIST_NEW, newItems);
        data.put(MapKeys.LIST_DELETED, deletedItems);
        data.put(MapKeys.LIST_CHANGED, editedItems);

        Map<MapKeys, List<BasicFile>> fileLists = currentRepository.scanRepository(currentUser);

        if (fileLists == null) {
            throw new RepositoryException(eErrorCodes.NOTHING_TO_SEE);
        } else {
            for (Map.Entry<MapKeys, List<BasicFile>> entry : fileLists.entrySet()) {
                List<BasicFile> temp = entry.getValue();
                if (temp.size() > 0) {
                    List<String> correctList = data.get(entry.getKey());
                    for (BasicFile file : temp) {
                        correctList.add(file.getType() + Settings.TYPE_SPLITTER + file.shortPath());
                    }
                }
            }

            return data;
        }
    }

    public boolean changeRepo(String path) throws IOException, RepositoryException {
        File basicPath = new File(path + File.separator + Settings.MAGIT_FOLDER);
        if (basicPath.exists()) {
            File isRemote = new File(basicPath.getPath() + File.separator + Settings.REMOTE_REPOSITORY_FILE_DATA);
            boolean isTracking = false;
            String remoteRepositoryName = null;
            if (isRemote.exists()) {
                Magit remoteRepository = new Magit();
                List<String> pathToRemote = Files.readAllLines(isRemote.toPath());
                remoteRepository.changeRepo(pathToRemote.get(0));
                this.remoteRepository = remoteRepository;
                remoteRepositoryName = remoteRepository.getCurrentRepository().getName();
                isTracking = true;
            } else {
                this.remoteRepository = null;
            }
            currentRepository = new Repository(Paths.get(path), currentUser, remoteRepositoryName, true, isTracking);
            rootFolder = Paths.get(path);
            currentBranch = currentRepository.getActiveBranch();
            currentUser = Settings.language.getString("USER_ADMINISTRATOR");
            return true;
        }
        return false;
    }

    public void createNewRepository(String newValue, File selectedDirectory) throws IOException, RepositoryException {
        this.rootFolder = selectedDirectory.toPath();
        checkCleanDir(rootFolder.toString());
        this.currentRepository = new Repository(rootFolder, currentUser, newValue);
        this.currentBranch = currentRepository.getActiveBranch();
    }

    public void exportFile(MagitRepository magitRepository, String pathToXML) throws JAXBException, IOException {
        OutputStream os = new FileOutputStream(pathToXML + File.separator + currentRepository.getName() + Settings.DOT_XML);
        JAXBContext jaxbContext = JAXBContext.newInstance(MagitRepository.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        jaxbMarshaller.marshal(magitRepository, os);
        os.close();
    }

    public Commit getCommitData(String sha_one) throws IOException {
        if ((sha_one).length() != Settings.SHA_ONE_CORRECT_LENGTH) {
            return null;
        } else {
            File commit = new File(currentRepository.getObjectPath() + File.separator + sha_one);
            if (commit.exists()) {
                return new Commit(sha_one, currentRepository.getObjectPath().toString());
            } else {
                return null;
            }
        }
    }

    private Commit getAncestorCommit(Commit base, Commit target) throws IOException {
        AncestorFinder finder = new AncestorFinder(sha_one -> {
            try {
                return new Commit(sha_one, currentRepository.getObjectPath().toString());
            } catch (IOException e) {
                return null;
            }
        });

        String sha_one = finder.traceAncestor(base.getSHA_ONE(), target.getSHA_ONE());
        return new Commit(sha_one, currentRepository.getObjectPath().toString());
    }

    private Commit getAncestorCommit(Branch target) throws IOException {
        Commit first = currentBranch.getCommit(), second = target.getCommit();
        return getAncestorCommit(first, second);
    }

    public Branch findBranch(String branchName) {
        return currentRepository.searchBranch(branchName);
    }

    public Map<String, BlobMap> findChanges(Branch target) throws RepositoryException, IOException, MyFileException {
        Map<String, BlobMap> allFiles = new HashMap<>();
        Commit ancestor = getAncestorCommit(target);

        if (checkIsFFMerge(ancestor, target.getCommit(), currentBranch.getCommit())) {
            allFiles.put(Settings.FAST_FORWARD_MERGE, new BlobMap(new HashMap<>()));
            return allFiles;
        }

        BlobMap ancestorFileTree = getCurrentRepository().loadDataFromCommit(ancestor),
                activeFileTree = getCurrentRepository().loadDataFromCommit(currentBranch.getCommit()),
                targetFileTree = getCurrentRepository().loadDataFromCommit(target.getCommit());

        BlobMap finalMap = mergeMaps(ancestorFileTree, activeFileTree, targetFileTree);
        List<BlobMap> blobMapList = calculateChanges(finalMap, ancestorFileTree, activeFileTree, targetFileTree);

        allFiles.put(Settings.KEY_CHANGE_MAP, blobMapList.get(0));
        allFiles.put(Settings.KEY_ANCESTOR_MAP, ancestorFileTree);
        allFiles.put(Settings.KEY_ACTIVE_MAP, activeFileTree);
        allFiles.put(Settings.KEY_TARGET_MAP, targetFileTree);
        allFiles.put(Settings.KEY_FINAL_MAP, finalMap);
        allFiles.put(Settings.KEY_EASY_TAKE_MAP, blobMapList.get(1));
        allFiles.put(Settings.KEY_FOLDER_ONLY_MAP, finalMap.getOnlyFolders());

        return allFiles;
    }

    private boolean checkIsFFMerge(Commit ancestor, Commit target, Commit active) {
        return ancestor.equals(target) || ancestor.equals(active);
    }

    private BlobMap mergeMaps(BlobMap... values) {
        BlobMap merge = new BlobMap(new HashMap<>());

        for (BlobMap temp : values) {
            if (temp != null) {
                for (Map.Entry<BasicFile, Blob> entry : temp.getMap().entrySet()) {
                    Blob tempBlob = entry.getValue();
                    if (merge.getMap().containsKey(tempBlob) && tempBlob.getType() == eFileTypes.FOLDER) {
                        addAllToMap((Folder) merge.getMap().get(tempBlob), (Folder) tempBlob);
                    } else {
                        merge.addToMap(tempBlob);
                    }
                }
            }
        }

        return merge;
    }

    private void addAllToMap(Folder merge, Folder value) {
        for (Map.Entry<BasicFile, Blob> entry : value.getBlobMap().getMap().entrySet()) {
            if (merge.getBlobMap().getMap().containsKey(entry.getValue()) && entry.getValue().getType() == eFileTypes.FOLDER) {
                addAllToMap((Folder) merge.getBlobMap().getMap().get(entry.getValue()), (Folder) entry.getValue());
            } else {
                merge.getBlobMap().addToMap(entry.getValue());
            }
        }
    }

    private List<BlobMap> calculateChanges(BlobMap... values) {
        BlobMap allFiles = values[0], ancestor = values[1], active = values[2], target = values[3];
        BlobMap files = new BlobMap(new HashMap<>()), easyTake = new BlobMap(new HashMap<>());

        List<BlobMap> blobMapList = new ArrayList<>(2);
        blobMapList.add(0, files);
        blobMapList.add(1, easyTake);

        for (Map.Entry<BasicFile, Blob> entry : allFiles.getMap().entrySet()) {
            Blob blob = entry.getValue();
            if (blob.getType() == eFileTypes.FOLDER) {
                List<BlobMap> tempBlobMapList = calculateChanges(((Folder) blob).getBlobMap(), ancestor, active, target);
                files.merge(tempBlobMapList.get(0));
                easyTake.merge(tempBlobMapList.get(1));
            } else {
                Folder rootFolder = blob.getRootFolder();
                boolean isInRootFolder = rootFolder.getBlobMap().equals(currentRepository.getRootFolder().getBlobMap());
                WarpBasicFile pointerAncestor = new WarpBasicFile(null), pointerActive = new WarpBasicFile(null), pointerTarget = new WarpBasicFile(null);
                WarpBasicFile[] pointers = {pointerAncestor, pointerActive, pointerTarget};
                boolean check_1 = ancestor.contain(blob, isInRootFolder, pointerAncestor),
                        check_2 = active.contain(blob, isInRootFolder, pointerActive),
                        check_3 = target.contain(blob, isInRootFolder, pointerTarget),
                        check_4 = pointerAncestor.equals(pointerActive),
                        check_5 = pointerAncestor.equals(pointerTarget),
                        check_6 = pointerActive.equals(pointerTarget);
                eConflictChecker condition = eConflictChecker.getItem(check_1, check_2, check_3, check_4, check_5, check_6).get();
                if (condition.isConflict()) {
                    if (condition.take()) {
                        easyTake.addToMap(pointers[condition.whatToTake()].getFile());
                    } else if (!condition.notTake()) {
                        files.addToMap(blob);
                    }
                }
            }
        }

        return blobMapList;
    }

    public void merge(Commit our, Commit theirs, String comment, boolean includeLayout) throws IOException, MyFileException, RepositoryException {
        Commit ancestor = getAncestorCommit(our, theirs);
        BlobMap outMap = currentRepository.loadDataFromCommit(our),
                theirsMap = currentRepository.loadDataFromCommit(theirs),
                ancestorMap = currentRepository.loadDataFromCommit(ancestor);

        BlobMap mergedMaps = mergeMaps(outMap, theirsMap, ancestorMap);
        List<BlobMap> blobMapList = calculateChanges(mergedMaps, ancestorMap, theirsMap, outMap);
        Map<String, BlobMap> changes = new HashMap<>();
        changes.put(Settings.KEY_FINAL_MAP, mergedMaps);
        changes.put(Settings.KEY_EASY_TAKE_MAP, blobMapList.get(1));
        merge(changes,
                new BlobMap[]{new BlobMap(new HashMap<>()), new BlobMap(new HashMap<>())},
                new Commit[]{our, theirs}, comment, includeLayout);
    }

    public void merge(Map<String, BlobMap> changes, BlobMap[] userApprove, Commit theirCommit, String comment)
            throws IOException, MyFileException, RepositoryException {

        merge(changes, userApprove, new Commit[]{currentBranch.getCommit(), theirCommit}, comment, true);
    }

    public void merge(Map<String, BlobMap> changes, BlobMap[] userApprove, Commit[] commits, String comment, boolean includeLayout)
            throws IOException, MyFileException, RepositoryException {

        BlobMap finalMap = changes.get(Settings.KEY_FINAL_MAP);
        replaceEasyTakeInFinalMap(finalMap, changes.get(Settings.KEY_EASY_TAKE_MAP));
        buildFromTwoBlobMaps(userApprove, finalMap);

        if(includeLayout) {
            deleteOldFiles(rootFolder.toString());
            layoutRepositoryByRootFolder(finalMap.getMap(), null);
        }
        try {
            commitMagit(currentUser, comment, commits[1]);
        } catch (RepositoryException e) {
            if (e.getCode() == eErrorCodes.NOTHING_NEW) {
                Commit newCommit = new Commit(commits[0]);
                newCommit.addPrevCommit(commits[1]);
                newCommit.createCommitFileWithoutChanges(currentRepository, currentUser, comment);
                currentBranch.setCommit(newCommit, currentRepository.getBranchesPath().toString());
                currentRepository.setLastCommit(newCommit);
            }
        }
    }

    private void replaceEasyTakeInFinalMap(BlobMap finalMap, BlobMap blobMap) {
        for (Map.Entry<BasicFile, Blob> entry : blobMap.getMap().entrySet()) {
            finalMap.replace(entry.getValue());
        }
    }

    private void buildFromTwoBlobMaps(BlobMap[] userApprove, BlobMap finalMap) {
        BlobMap deleted = userApprove[1], edited = userApprove[0];
        for (Map.Entry<BasicFile, Blob> entry : edited.getMap().entrySet()) {
            finalMap.replace(entry.getValue());
        }

        for (Map.Entry<BasicFile, Blob> entry : deleted.getMap().entrySet()) {
            removeFromBlobMap(finalMap, entry.getValue());
        }
    }

    private void removeFromBlobMap(BlobMap map, Blob file) {
        map.remove(file);
        Folder rootFolder = file.getRootFolder();
        if (rootFolder != currentRepository.getRootFolder()) {
            if (rootFolder.getBlobMap().getSize() == 0) {
                removeFromBlobMap(map, rootFolder);
            }
        }
    }

    private Map<Branch, List<PairBranchCommit>> getAllCommits() {
        Map<Branch, List<PairBranchCommit>> commitBranchMap = new HashMap<>();
        Map<Commit, String> nonDuplicateInLists = new HashMap<>();
        List<Branch> branches = currentRepository.getAllBranches();
        for (Branch branch : branches) {
            if (!branch.isHead()) {
                List<Commit> commitList = new LinkedList<>(branch.getAllCommits());
                commitList.sort(Comparator.comparing(Commit::getDate));
                List<PairBranchCommit> pairBranchCommits = new LinkedList<>();
                boolean atLeastOne = false;
                for (Commit commit : commitList) {
                    if (!nonDuplicateInLists.containsKey(commit)) {
                        pairBranchCommits.add(new PairBranchCommit(commit, branch));
                        nonDuplicateInLists.put(commit, "noneDuplicate");
                        atLeastOne = true;
                    }
                }
                if (!atLeastOne) {
                    pairBranchCommits.add(new PairBranchCommit(branch.getCommit(), branch));
                }
                if (!commitBranchMap.containsKey(branch)) {
                    commitBranchMap.put(branch, pairBranchCommits);
                } else {
                    commitBranchMap.get(branch).addAll(pairBranchCommits);
                }
            }
        }
        return commitBranchMap;
    }

    public Map<Branch, List<PairBranchCommit>> getAllCommits(boolean includeAllCommits) throws RepositoryException {
        Map<Branch, List<PairBranchCommit>> commitBranchMap = new HashMap<>(getAllCommits());
        if (!includeAllCommits) {
            return commitBranchMap;
        }
        Branch temp = new Branch(Settings.TEMP_BRANCH_FOR_EXPANDED_TREE);

        File objectFolder = new File(currentRepository.getObjectPath().toString());
        File[] objectFiles = objectFolder.listFiles();
        if (objectFiles != null) {
            Set<PairBranchCommit> list = new LinkedHashSet<>();
            List<Commit> commitList = new LinkedList<>();
            for (File file : objectFiles) {
                try {
                    if (checkIsRealCommitFile(Files.readAllLines(file.toPath()))) {
                        commitList.add(new Commit(file.getName(), currentRepository.getObjectPath().toString()));
                    }
                } catch (IOException ignored) {
                }
            }

            commitList.sort(Comparator.comparing(Commit::getDate));

            for (Commit commit : commitList) {
                list.add(new PairBranchCommit(commit, temp));
            }
            List<PairBranchCommit> updateList = new ArrayList<>(list);
            commitBranchMap.put(temp, updateList);
        }

        currentRepository.addBranch(temp);
        return commitBranchMap;
    }

    public Map<Commit, Branch> getPointedBranchesToCommitsMap() {
        Map<Commit, Branch> map = new HashMap<>();
        for (Branch branch : currentRepository.getAllBranches()) {
            map.put(branch.getCommit(), branch);
        }
        return map;
    }

    public void tryCreateNewRemoteTrackingBranch(String newBranchName, Branch oldBranch) throws RepositoryException, IOException {
        if (checkAlreadyExistRTBPointedOnRemote(oldBranch)) {
            throw new RepositoryException(eErrorCodes.ONLY_ONE_RTB);
        }
        Branch branch = new RemoteTrackingBranch(oldBranch, currentRepository.getBranchesPath().toString(), newBranchName);
        currentRepository.addBranch(branch);
    }

    private boolean checkAlreadyExistRTBPointedOnRemote(Branch oldBranch) {
        for (Branch branch : currentRepository.getActiveBranches()) {
            if (branch.getRemoteBranch().getName().equals(oldBranch.getName())) {
                return true;
            }
        }
        return false;
    }

    public void addNewBranchToLocal(String name, RemoteTrackingBranch remoteTrackingBranch) throws IOException {
        File branchesFolder = new File(currentRepository.getBranchesPath() + File.separator + name);
        File branch = new File(branchesFolder.getPath() + File.separator + remoteTrackingBranch.getName());
        branch.createNewFile();
        PrintWriter writer = new PrintWriter(branch);
        writer.write(remoteTrackingBranch.getCommit().getSHA_ONE());
        writer.close();
    }

    public Magit magitClone(File magitPath, File destPath) throws IOException, RepositoryException {
        if (destPath.exists()) {
            if (Files.list(destPath.toPath()).findFirst().isPresent()) {
                throw new RepositoryException(eErrorCodes.MAGIT_FOLDER_ALREADY_EXIST);
            }
        }

        FileUtils.copyDirectory(magitPath, destPath);
        Magit remote = new Magit();
        remote.changeRepo(destPath.getPath());
        File branchesFolder = new File(remote.getCurrentRepository().getBranchesPath().toString());
        File remoteFolder = new File(branchesFolder.getPath() + File.separator + this.currentRepository.getName());
        File[] branchesFiles = branchesFolder.listFiles();
        for (File file : branchesFiles != null ? branchesFiles : new File[0]) {
            file.delete();
        }
        remoteFolder.mkdir();
        remote.setRemoteRepository(this);
        remote.cloneFrom(this);
        remote.getCurrentRepository().updateHeadFile(remote.getCurrentBranch().getName());
        return remote;
    }

    private void cloneFrom(Magit magit) throws IOException {
        List<Branch> remoteBranches = new LinkedList<>();
        List<Branch> remoteTrackingBranches = new LinkedList<>();
        Branch branch = magit.getCurrentRepository().getActiveBranch();
        RemoteTrackingBranch active = branch.createRemoteTrackingBranch(
                this.getCurrentRepository().getBranchesPath().toString(),
                null);
        remoteTrackingBranches.add(active);

        for (Branch b : magit.getCurrentRepository().getActiveBranches()) {
            if (!b.isHead()) {
                remoteBranches.add(new Branch(b.makeRemote(magit.getCurrentRepository().getName(), this.getCurrentRepository().getBranchesPath())));
            }
        }

        this.getCurrentRepository().updateRemoteBranchesList(remoteBranches);
        this.getCurrentRepository().updateRemoteTrackingBranchList(remoteTrackingBranches);
        this.currentBranch = active;
    }

    public void fetch(Magit remoteRepository) throws IOException {
        File objectFolder = new File(remoteRepository.getCurrentRepository().getObjectPath().toString()),
                branchesFolder = new File(remoteRepository.getCurrentRepository().getBranchesPath().toString()),
                myObjectFolder = new File(currentRepository.getObjectPath().toString()),
                myRemoteBranchesFolder = new File(currentRepository.getBranchesPath() + File.separator + remoteRepository.getCurrentRepository().getName());

        FileUtils.copyDirectory(objectFolder, myObjectFolder);
        File[] branchesFiles = branchesFolder.listFiles();

        if (branchesFiles != null) {
            for (File branch : branchesFiles) {
                if (!branch.getName().equals(Settings.MAGIT_BRANCH_HEAD)) {
                    FileUtils.copyFileToDirectory(branch, myRemoteBranchesFolder);
                }
            }
        }
    }

    public void pull() throws RepositoryException, MyFileException, IOException {
        if (currentBranch instanceof RemoteTrackingBranch) {
            Branch activeRemoteBranch = remoteRepository.getCurrentBranch();
            Commit activeRemoteCommit = activeRemoteBranch.getCommit();

            List<Commit> activeRBCommitHistory = activeRemoteCommit.getChainOfCommits();
            boolean isContains = false;
            for (Commit commit : activeRBCommitHistory) {
                if (commit.equals(currentBranch.getCommit())) {
                    isContains = true;
                    break;
                }
            }

            if (!isContains) {
                throw new RepositoryException(eErrorCodes.OPEN_CHANGES_NEEDS_PULL);
            }

            copyFilesFromCommit(activeRemoteCommit,
                    remoteRepository.getCurrentRepository().getObjectPath().toString(),
                    currentRepository.getObjectPath().toFile());

            updateBranchesData(activeRemoteBranch);
            deleteOldFiles(this.rootFolder.toString());
            BlobMap newRootFolder = currentRepository.loadDataFromCommit(activeRemoteCommit);
            Folder rootFolder = currentRepository.getRootFolder();
            rootFolder.setBlobMap(newRootFolder);
            layoutRepositoryByRootFolder(newRootFolder.getMap(), null);
        } else {
            throw new RepositoryException(eErrorCodes.CANNOT_PULL_DATA_FOR_NONE_RTB);
        }
    }

    private void copyFilesFromCommit(Commit commit, String objectPathFrom, File objectPathTo) throws RepositoryException {
        Set<File> files = commit.getCommitsFiles(objectPathFrom);
        for (File file : files) {
            try {
                FileUtils.copyFileToDirectory(file, objectPathTo);
            } catch (IOException e) {
                throw new RepositoryException(eErrorCodes.COPY_FILE_FROM_REMOTE_TO_LOCAL_FAILED);
            }
        }
    }

    private void updateBranchesData(Branch activeRemoteBranch) throws RepositoryException, IOException {
        Branch remote = findInRemoteBranches(activeRemoteBranch, remoteRepository.getCurrentRepository().getName());
        if (remote == null) {
            throw new RepositoryException(eErrorCodes.REMOTE_BRANCH_NOT_FOUND);
        }

        remote.setCommit(
                activeRemoteBranch.getCommit(),
                currentRepository.getBranchesPath().toString());
        Branch remoteTrackingBranch = findBranch(activeRemoteBranch.getName());
        if (!(remoteTrackingBranch instanceof RemoteTrackingBranch)) {
            remoteTrackingBranch = new RemoteTrackingBranch(remote,
                    currentRepository.getBranchesPath().toString(),
                    remoteRepository.getCurrentRepository().getName());
        }
        remoteTrackingBranch.setCommit(activeRemoteBranch.getCommit(), currentRepository.getBranchesPath().toString());
        setNewActiveBranch(remoteTrackingBranch);
    }

    private Branch findInRemoteBranches(Branch activeRemoteBranch, String remoteRepositoryName) {
        for (Branch branch : currentRepository.getRemoteBranches()) {
            if (branch.getName().equals(remoteRepositoryName + File.separator + activeRemoteBranch.getName())) {
                return branch;
            }
        }
        return null;
    }

    public void push(String branchName) throws RepositoryException {
        Branch branch = findBranch(branchName);
        if (branch instanceof RemoteTrackingBranch) {

        } else {
            throw new RepositoryException(eErrorCodes.NOT_RTB_CANNOT_PUSH);
        }
    }

    public void push() throws RepositoryException, IOException, MyFileException {
        Branch headBranch = currentBranch;
        if (headBranch instanceof RemoteTrackingBranch) {
            Branch remoteBranch = findInRemoteBranches(headBranch, remoteRepository.getCurrentRepository().getName());
            if (remoteBranch != null) {
                Branch branchFromRemoteRepository = remoteRepository.findBranch(remoteBranch.getName());
                // checking if those branches pointed on the same commit -> mean that user already do fetch & pull commands!
                if (branchFromRemoteRepository != null &&
                        remoteBranch.getCommit().getSHA_ONE().equals(branchFromRemoteRepository.getCommit().getSHA_ONE())) {

                    copyFilesFromCommit(headBranch.getCommit(),
                            currentRepository.getObjectPath().toString(),
                            remoteRepository.getCurrentRepository().getObjectPath().toFile());

                    remoteRepository.updateBranchesDataFarAway(headBranch);
                    if (remoteRepository.getCurrentBranch().getName().equals(headBranch.getName())) {
                        remoteRepository.deleteOldFiles(remoteRepository.getRootFolder().toString());
                        remoteRepository.layoutRepositoryByRootFolder(currentRepository.loadDataFromCommit(headBranch.getCommit()).getMap(), remoteRepository.getRootFolder().toString());
                    }
                    remoteBranch.setCommit(headBranch.getCommit(), currentRepository.getBranchesPath().toString());
                } else {
                    throw new RepositoryException(eErrorCodes.REMOTE_BRANCH_NOT_POINTED_ON_SAME_COMMIT);
                }
            } else {
                throw new RepositoryException(eErrorCodes.REMOTE_BRANCH_NOT_FOUND);
            }
        } else {
            throw new RepositoryException(eErrorCodes.NOT_RTB_CANNOT_PUSH);
        }
    }

    private void updateBranchesDataFarAway(Branch branch) throws RepositoryException, FileNotFoundException {
        Branch remote = findBranch(branch.getName());
        if (remote == null) {
            throw new RepositoryException(eErrorCodes.REMOTE_BRANCH_NOT_FOUND);
        }

        remote.setCommit(branch.getCommit(), currentRepository.getBranchesPath().toString());
        Branch remoteBranch = findBranch(branch.getName());
        remoteBranch.setCommit(branch.getCommit(), currentRepository.getBranchesPath().toString());
        setNewActiveBranch(remoteBranch);
    }

    public void updateRemoteAfterMerge(Branch remote) throws RepositoryException {
        Branch localRemote = findInRemoteBranches(remote, remoteRepository.getCurrentRepository().getName());
        if (localRemote == null) {
            throw new RepositoryException(eErrorCodes.REMOTE_BRANCH_NOT_FOUND);
        } else {
            localRemote.setCommit(remote.getCommit(), currentRepository.getBranchesPath().toString());
        }
    }

    public List<Commit> getCommitList() {
        List<Commit> commits = new LinkedList<>();
        for (Branch branch : currentRepository.getAllBranches()) {
            commits.addAll(branch.getAllCommits());
        }

        return commits.stream().distinct().collect(Collectors.toList());
    }

    public void fastForwardMerge(Branch target) throws RepositoryException {
        Commit targetCommit = target.getCommit(), activeCommit = currentBranch.getCommit();

        if (targetCommit.getDate().compareTo(activeCommit.getDate()) > 0) {
            currentBranch.setCommit(targetCommit, currentRepository.getBranchesPath().toString());
        }
    }

    public void pushBranch(Branch branch) throws RepositoryException, IOException {
        if (getRemoteRepository().findBranch(branch.getName()) != null) {
            throw new RepositoryException(eErrorCodes.REMOTE_REPO_CONTAIN_BRANCH, branch.getName());
        }
        Commit commit = branch.getCommit();
        copyFilesFromCommit(commit,
                currentRepository.getObjectPath().toString(),
                remoteRepository.getCurrentRepository().getObjectPath().toFile());

        remoteRepository.tryCreateNewBranch(branch.getName(), commit);
        convertLocalBranchToRTB(branch, remoteRepository.getCurrentRepository().getName());
    }

    private void convertLocalBranchToRTB(Branch branch, String remoteRepositoryName) throws IOException {
        RemoteTrackingBranch remoteTrackingBranch =
                new RemoteTrackingBranch(branch, currentRepository.getBranchesPath().toString(), null);

        currentRepository.addBranch(remoteTrackingBranch);
        currentRepository.removeBranch(branch.getName());
        branch.setRemote(true);
        branch.setName(remoteRepositoryName + File.separator + branch.getName());
        File branchFile = new File(currentRepository.getBranchesPath() + File.separator + branch.getName());
        try (PrintWriter writer = new PrintWriter(branchFile)) {
            writer.write(branch.getCommit().getSHA_ONE());
        }
    }

    public Map<String, String> createRepositoryJson() {
        Map<String, String> result = new HashMap<>();

        result.put(Settings.WSA_REPOSITORY_NAME, currentRepository.getName());
        result.put(Settings.WSA_JSON_ACTIVE_BRANCH, currentBranch.getName());
        result.put(Settings.WSA_JSON_CURRENT_PATH, rootFolder.toString());
        result.put(Settings.WSA_JSON_NUM_OF_BRANCHES, Integer.toString(getCurrentRepository().getAllBranches().size() - 1)); // minus 1 because HEAD branch
        Commit lastCommit = currentRepository.getLastCommit();
        result.put(Settings.WSA_JSON_LAST_COMMIT_DATA,
                new SimpleDateFormat(Settings.WEB_DATE_FORMAT).format(lastCommit.getDate()));
        result.put(Settings.WSA_JSON_LAST_COMMIT_COMMENT, lastCommit.getComment());

        return result;
    }

    public Map<String, List<String>> getRepositoryMap() throws IOException, MyFileException, RepositoryException {
        Map<String, List<String>> result = new HashMap<>();

        result.put(Settings.WSA_REPOSITORY_NAME, Utilities.createSingleItemList(currentRepository.getName()));
        List<Branch> branches = currentRepository.getAllBranches();
        result.put(Settings.WSA_SINGLE_REPOSITORY_BRANCHES,
                branches.stream()
                        .filter(branch -> !branch.getName().equals(Settings.MAGIT_BRANCH_HEAD))
                        .map(Branch::getName)
                        .collect(Collectors.toList()));
        result.put(Settings.WSA_SINGLE_REPOSITORY_HEAD_BRANCH, Utilities.createSingleItemList(currentRepository.getActiveBranch().getName()));
        result.put(Settings.WSA_SINGLE_REPOSITORY_ALL_COMMITS, createAllCommitJSONList(getSimpleAllCommitList()));
        result.put(Settings.WSA_SINGLE_REPOSITORY_FILE_TREE, getFileTreeJSON());
        result.put(Settings.WSA_SINGLE_REPOSITORY_OPENED_CHANGES, getOpenChangesJSON());
        if (remoteRepository != null)
            result.put(Settings.WSA_SINGLE_REPOSITORY_IS_RT, Utilities.createSingleItemList("Remote tracking"));

        return result;
    }

    private List<String> getOpenChangesJSON() throws RepositoryException, MyFileException, IOException {
        Map<MapKeys, List<BasicFile>> results = currentRepository.scanRepository(currentUser);
        if (results == null) {
            return new LinkedList<>();
        } else {
            List<String> changes = new LinkedList<>();
            changes.add(mapWithTypes(results.get(MapKeys.LIST_NEW)));
            changes.add(mapWithTypes(results.get(MapKeys.LIST_CHANGED)));
            changes.add(mapWithTypes(results.get(MapKeys.LIST_DELETED)));
            return changes;
        }
    }

    private String mapWithTypes(List<BasicFile> files) {
        Map<String, String> result = new HashMap<>();
        for (BasicFile basicFile : files) {
            result.put(basicFile.shortPath(), basicFile.getType().toString());
        }

        return new Gson().toJson(result);
    }

    private List<String> getFileTreeJSON() throws IOException {
        List<String> result = new LinkedList<>();
        BlobMap currentFileTree = currentRepository.getCurrentFilesState(currentUser);

        result.add(new Gson().toJson(getFilesData(currentFileTree)));
        return result;
    }

    private List<String> createAllCommitJSONList(Set<Commit> commits) {
        List<String> results = new LinkedList<>();
        List<Branch> branches = currentRepository.getAllBranches();

        Gson gson = new Gson();

        for (Commit commit : commits) {
            Map<String, String> singleCommit = new HashMap<>();
            singleCommit.put(Settings.WSA_SINGLE_COMMIT_SHA1_KEY, commit.getSHA_ONE());
            singleCommit.put(Settings.WSA_SINGLE_COMMIT_COMMENT_KEY, commit.getComment());
            singleCommit.put(Settings.WSA_SINGLE_COMMIT_DATE_KEY, new SimpleDateFormat(Settings.WEB_DATE_FORMAT).format(commit.getDate()));
            singleCommit.put(Settings.WSA_SINGLE_COMMIT_CREATOR_KEY, commit.getCreator());
            singleCommit.put(Settings.WSA_SINGLE_COMMIT_POINTED_BRANCHES, commit.getPointedBranches(branches));
            results.add(gson.toJson(singleCommit));
        }

        return results;
    }

    public Map<String, String> getFilesData(BlobMap blobMap) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<BasicFile, Blob> entry : blobMap.getMap().entrySet()) {
            Blob blob = entry.getValue();
            if (blob.getType() == eFileTypes.FOLDER) {
                map.put(blob.getName(), new Gson().toJson(getFilesData(((Folder) blob).getBlobMap())));
            } else {
                map.put(blob.getName(), blob.getContent());
            }
        }

        return map;
    }

    private Set<Commit> getSimpleAllCommitList() {
        Set<Commit> commits = new LinkedHashSet<>();
        for (Branch branch : currentRepository.getAllBranches()) {
            if (!branch.isHead()) {
                commits.addAll(branch.getAllCommits());
            }
        }
        commits = commits.stream()
                .sorted((o1, o2) -> o2.getDate().compareTo(o1.getDate()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return commits;
    }

    public void updateFileContent(String path, String content) {
        File file = new File(currentRepository.getCurrentPath() + File.separator + path);
        if (file.exists()) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.print(content);
            } catch (FileNotFoundException ignored) {
            }
        }
    }

    public String CreateFilesDataJSON(Map<String, BlobMap> changes) {
        Gson gson = new Gson();
        Map<String, Map<String, String>> result = new HashMap<>();
        BlobMap filesChanges = changes.get(Settings.KEY_CHANGE_MAP);

        Blob random = filesChanges.getRandomBlob();
        while (random != null) {
            Map<String, String> fileContent = new HashMap<>();
            Map<eUserMergeChoice, Blob> contents = filesChanges.getDuplicate(random, changes);
            fileContent.put(Settings.KEY_ACTIVE_MAP, contents.get(eUserMergeChoice.ACTIVE).getContent());
            fileContent.put(Settings.KEY_TARGET_MAP, contents.get(eUserMergeChoice.TARGET).getContent());
            fileContent.put(Settings.KEY_ANCESTOR_MAP, contents.get(eUserMergeChoice.ANCESTOR).getContent());
            result.put(random.shortPath(), fileContent);
            filesChanges.remove(random);
            random = filesChanges.getRandomBlob();
        }
        return gson.toJson(result);
    }

    public void finishMerge(Branch target, Map<String, String[]> filesContents, String comment) throws RepositoryException, MyFileException, IOException {
        Map<String, BlobMap> changes = findChanges(target);
        BlobMap[] userApprove = updateUserChoiceFromWeb(filesContents, changes.get(Settings.KEY_CHANGE_MAP));

        merge(changes, userApprove, target.getCommit(), comment);
    }

    private BlobMap[] updateUserChoiceFromWeb(Map<String, String[]> filesContents, BlobMap files) {
        BlobMap[] userApprove = new BlobMap[2];
        for (int i = 0; i < 2; i++) {
            userApprove[i] = new BlobMap(new HashMap<>());
        }

        Blob random = files.getRandomBlob();
        while (random != null) {
            files.remove(random);
            if (filesContents.containsKey(random.shortPath())) {
                random.setContent(filesContents.get(random.shortPath())[0]);
                random.setSHA_ONE(DigestUtils.sha1Hex(random.getContent()));
                random.setDate(new Date());
                random.setEditorName(currentUser);
                userApprove[0].addToMap(random);
            } else {
                userApprove[1].addToMap(random);
            }
            random = files.getRandomBlob();
        }

        return userApprove;
    }

    public void resetBranch(Branch currentBranch, String commitSHA_ONE) throws RepositoryException, IOException, MyFileException {
        if (commitSHA_ONE.length() == 40) {
            Commit commit = loadCommitBySHA(commitSHA_ONE);
            if (commit == null) {
                throw new RepositoryException(eErrorCodes.NO_COMMIT_WITH_SHA_ONE_EXISTS);
            } else {
                currentBranch.setCommit(commit, currentRepository.getBranchesPath().toString());
                if (checkout(currentBranch.getName())) {
                    currentRepository.setLastCommit(currentBranch.getCommit());
                } else {
                    throw new RepositoryException(eErrorCodes.UNKNOWN_ERROR);
                }
            }
        }
    }

    public Branch findRemoteBranch(String targetBranch) {
        List<Branch> branches = currentRepository.getRemoteBranches();
        for (Branch branch : branches) {
            if (branch.getName().equals(targetBranch)) {
                return branch;
            }
        }

        return null;
    }

    public void createPullRequest(Map<String, String> data) {

    }

    public List<Commit> getAllCommitsForPR(Map<String, String> details, Magit localRepository) throws RepositoryException, IOException {
        if (details == null) {
            throw new RepositoryException(eErrorCodes.PR_NOT_FOUND);
        }

        Commit ancestorCommit = localRepository.findCommitsFromRepositories(
                currentRepository.getName() + File.separator + details.get(Settings.PR_REMOTE_BRANCH_NAME),
                details.get(Settings.PR_LOCAL_BRANCH_NAME));

        Commit current = localRepository.findBranch(details.get(Settings.PR_LOCAL_BRANCH_NAME)).getCommit();
        return new LinkedList<>(findAllCommits(current, ancestorCommit));
    }

    private List<Commit> findAllCommits(Commit current, Commit ancestorCommit) {
        List<Commit> commits = new LinkedList<>();

        if (current != null) {
            if (!current.equals(ancestorCommit)) {
                commits.add(current);
                commits.addAll(findAllCommits(current.getPrevCommit(), ancestorCommit));
                commits.addAll(findAllCommits(current.getAnotherPrevCommit(), ancestorCommit));
            }
        }

        return commits;
    }

    private Commit findCommitsFromRepositories(String remoteBranchName, String localBranchName) throws IOException {
        Branch remote = findRemoteBranch(remoteBranchName),
                local = findBranch(localBranchName);

        Commit remoteCommit = remote.getCommit(),
                localCommit = local.getCommit();

        return getAncestorCommit(remoteCommit, localCommit);
    }

    //Map<FilePath, deleted, edited or new>
    public Map<String, String> getDeltaFromCommits(List<Commit> allCommitsForPR, String targetSHA_ONE) throws RepositoryException, IOException, MyFileException {
        Map<String, String> filesDelta = new HashMap<>();

        Commit commit = allCommitsForPR.stream().filter(commit1 -> commit1.getSHA_ONE().equals(targetSHA_ONE)).findFirst().get();

        Map<MapKeys, List<BasicFile>> changes = currentRepository.createMapForScanning();
        BlobMap current = currentRepository.loadDataFromCommit(commit),
                prevCommit = currentRepository.loadDataFromCommit(commit.getPrevCommit()),
                anotherPrevCommit = currentRepository.loadDataFromCommit(commit.getAnotherPrevCommit());


        if (commit.getPrevCommit() != null) {
            currentRepository.scanBetweenMaps(current.getMap(), prevCommit.getMap(), changes);
            currentRepository.scanForDeletedFiles(current.getMap(), prevCommit.getMap(),
                    changes.get(MapKeys.LIST_DELETED));
        }
        if (commit.getAnotherPrevCommit() != null) {
            currentRepository.scanBetweenMaps(current.getMap(), anotherPrevCommit.getMap(), changes);
            currentRepository.scanForDeletedFiles(current.getMap(), anotherPrevCommit.getMap(),
                    changes.get(MapKeys.LIST_DELETED));
        }

        for (Map.Entry<MapKeys, List<BasicFile>> entry : changes.entrySet()) {
            for (BasicFile file : entry.getValue()) {
                filesDelta.put(file.shortPath(), entry.getKey().name());
            }
        }

        return filesDelta;
    }

    public Set<File> getDeltaFilesForChanges(List<Commit> allCommitsForPR) throws RepositoryException {
        Set<File> files = new LinkedHashSet<>();
        for (Commit commit : allCommitsForPR) {
            files.addAll(commit.getCommitsFiles(currentRepository.getObjectPath().toString()));
        }

        return files;
    }

    public void getDeltaFromPR(Set<File> delta) {
        for (File file : delta) {
            try {
                Files.copy(file.toPath(),Paths.get((currentRepository.getObjectPath() + File.separator + file.getName())));
            } catch (IOException ignored) {
            }
        }
    }

    public File commitFile(Commit localCommit) {
        return new File(currentRepository.getObjectPath() + File.separator + localCommit.getSHA_ONE());
    }

    public Map<String, String> getFileContent(String filePath, String commitSHA_one) throws IOException, RepositoryException, MyFileException {
        Commit commit = loadCommitBySHA(commitSHA_one);
        BlobMap map = currentRepository.loadDataFromCommit(commit);
        Blob file = map.findFile(filePath);
        Map<String, String> result = new HashMap<>();
        result.put(Settings.PR_FILE_CONTENT, file.getContent());

        return result;
    }
}