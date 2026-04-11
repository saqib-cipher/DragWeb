cat << 'PATCH' > PreviewActivity.java.patch
<<<<<<< SEARCH
    private void setupWebView() {
        WebSettings settings = webviewPreview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);
=======
    private void setupWebView() {
        WebSettings settings = webviewPreview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
>>>>>>> REPLACE
<<<<<<< SEARCH
    private void loadCurrentPage() {
        String code = getCurrentCode();
        if (code == null || code.isEmpty()) {
            webviewPreview.loadData("<html><body><p style='padding:20px;color:#666;'>No content to preview</p></body></html>",
                "text/html", "utf-8");
            return;
        }

        String modifiedHtml = code;

        if (currentWidth > 0) {
            // Inject viewport meta tag with specific width
            String viewportMeta = "<meta name=\"viewport\" content=\"width=" + currentWidth + ", initial-scale=1.0\">";
            if (modifiedHtml.contains("<meta name=\"viewport\"")) {
                modifiedHtml = modifiedHtml.replaceAll(
                    "<meta\\s+name=\"viewport\"[^>]*>",
                    viewportMeta
                );
            } else if (modifiedHtml.contains("<head>")) {
                modifiedHtml = modifiedHtml.replace("<head>", "<head>" + viewportMeta);
            }
        }

        webviewPreview.loadDataWithBaseURL("file:///android_asset/", modifiedHtml, "text/html", "utf-8", null);
    }
=======
    private void loadCurrentPage() {
        if (pageNames == null || pageNames.isEmpty() || currentPageIndex >= pageNames.size()) {
            webviewPreview.loadData("<html><body><p style='padding:20px;color:#666;'>No content to preview</p></body></html>",
                "text/html", "utf-8");
            return;
        }

        String pageName = pageNames.get(currentPageIndex);
        String projectName = getIntent().getStringExtra("project_name");
        if (projectName == null) projectName = "Untitled Project";
        String safeProjectName = projectName.replaceAll("[^a-zA-Z0-9._-]", "_");

        java.io.File exportDir = new java.io.File(getFilesDir(), "exports/" + safeProjectName);
        java.io.File pageFile = new java.io.File(exportDir, pageName + ".html");

        if (pageFile.exists()) {
            webviewPreview.loadUrl("file://" + pageFile.getAbsolutePath());
        } else {
            // Fallback if not exported cleanly
            String code = getCurrentCode();
            if (code == null || code.isEmpty()) {
                webviewPreview.loadData("<html><body><p style='padding:20px;color:#666;'>No content to preview</p></body></html>",
                    "text/html", "utf-8");
                return;
            }

            String modifiedHtml = code;
            if (currentWidth > 0) {
                String viewportMeta = "<meta name=\"viewport\" content=\"width=" + currentWidth + ", initial-scale=1.0\">";
                if (modifiedHtml.contains("<meta name=\"viewport\"")) {
                    modifiedHtml = modifiedHtml.replaceAll("<meta\\s+name=\"viewport\"[^>]*>", viewportMeta);
                } else if (modifiedHtml.contains("<head>")) {
                    modifiedHtml = modifiedHtml.replace("<head>", "<head>" + viewportMeta);
                }
            }
            webviewPreview.loadDataWithBaseURL("file://" + exportDir.getAbsolutePath() + "/", modifiedHtml, "text/html", "utf-8", null);
        }
    }
>>>>>>> REPLACE
PATCH
patch source/app/src/main/java/sketchweb/gl/PreviewActivity.java PreviewActivity.java.patch
