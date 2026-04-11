cat << 'PATCH' > ProjectDataManager.java.patch
<<<<<<< SEARCH
    public boolean exportSingleProjectAsZip(String projectId, Uri outputUri) {
        File internalProjectsDir = new File(context.getFilesDir(), "projects");
        String extProjectPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/projects/" + projectId;
        File externalProjectDir = new File(extProjectPath);

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            if (internalProjectsDir.exists()) {
                File[] files = internalProjectsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        String name = file.getName();
                        if (name.startsWith(projectId + ".") || name.startsWith(projectId + "_")) {
                            addFileToZip(zos, file, "internal/projects/" + name);
                        }
                    }
                }
            }

            if (externalProjectDir.exists()) {
                addDirectoryToZip(zos, externalProjectDir, "external/projects/" + projectId + "/");
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export project zip: " + e.getMessage());
            return false;
        }
    }
=======
    public boolean exportSingleProjectAsZip(String projectId, Uri outputUri) {
        File internalProjectsDir = new File(context.getFilesDir(), "projects");
        File externalProjectDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/projects/" + projectId);

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            if (internalProjectsDir.exists()) {
                File[] files = internalProjectsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        String name = file.getName();
                        if (name.equals(projectId + ".json") || name.startsWith(projectId + "_")) {
                            addFileToZip(zos, file, "internal/projects/" + name);
                        }
                    }
                }
            }

            if (externalProjectDir.exists()) {
                addDirectoryToZip(zos, externalProjectDir, "external/projects/" + projectId + "/");
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export project zip: " + e.getMessage());
            return false;
        }
    }
>>>>>>> REPLACE
PATCH
patch source/app/src/main/java/sketchweb/gl/ProjectDataManager.java ProjectDataManager.java.patch
