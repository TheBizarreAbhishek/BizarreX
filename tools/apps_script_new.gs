// BizarreX Drive API — Hierarchical Folder Navigation
// Paste this entire code in Google Apps Script, save, and deploy as new version.

const ROOT_FOLDER_ID = "1hDmJnrrUwmUpLHviiYCLrSFQ8uLC7jfn";

function doGet(e) {
  const params = e.parameter || {};

  try {
    // Mode 1: ?folderId=ID  → contents of any folder (sub-folders + videos)
    if (params.folderId) {
      return jsonResponse(getFolderContents(params.folderId));
    }

    // Mode 2: ?subject=Name → contents of subject folder
    if (params.subject) {
      const subjectFolder = findFolderByName(ROOT_FOLDER_ID, params.subject);
      if (!subjectFolder) {
        return jsonResponse({ error: "Subject folder not found: " + params.subject });
      }
      const result = getFolderContents(subjectFolder.getId());
      result.folderId = subjectFolder.getId();
      result.folderName = subjectFolder.getName();
      return jsonResponse(result);
    }

    // Mode 3: no params → list all subject folders at root
    const rootFolder = DriveApp.getFolderById(ROOT_FOLDER_ID);
    const folders = [];
    const iter = rootFolder.getFolders();
    while (iter.hasNext()) {
      const f = iter.next();
      folders.push({ id: f.getId(), name: f.getName() });
    }
    folders.sort((a, b) => a.name.localeCompare(b.name));
    return jsonResponse({ success: true, hasSubFolders: true, folders: folders, videos: [] });

  } catch (err) {
    return jsonResponse({ error: err.toString() });
  }
}

function getFolderContents(folderId) {
  const folder = DriveApp.getFolderById(folderId);

  // Sub-folders
  const subFolders = [];
  const folderIter = folder.getFolders();
  while (folderIter.hasNext()) {
    const sf = folderIter.next();
    subFolders.push({ id: sf.getId(), name: sf.getName() });
  }
  subFolders.sort((a, b) => a.name.localeCompare(b.name));

  // Videos
  const videos = [];
  const fileIter = folder.getFiles();
  while (fileIter.hasNext()) {
    const file = fileIter.next();
    const mime = file.getMimeType();
    if (mime.startsWith('video/')) {
      videos.push({ id: file.getId(), title: file.getName() });
    }
  }
  videos.sort((a, b) => a.title.localeCompare(b.title));

  return {
    success: true,
    folderId: folderId,
    folderName: folder.getName(),
    hasSubFolders: subFolders.length > 0,
    folders: subFolders,
    videos: videos
  };
}

function findFolderByName(parentId, name) {
  const parent = DriveApp.getFolderById(parentId);
  const iter = parent.getFoldersByName(name);
  if (iter.hasNext()) return iter.next();
  return null;
}

function jsonResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
