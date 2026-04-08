import re

file_path = "source/app/src/main/java/sketchweb/gl/MainActivity.java"
with open(file_path, "r") as f:
    content = f.read()

# Fix potential NullPointerException in saveProject
save_project_old = """	private void saveProject() {
		// Save current page layout
		saveCurrentPageLayout();

		// Save main layout (index page for backwards compat)
		if ("index".equals(pageManager.getCurrentPage())) {
			projectDataManager.saveProject(screen, projectId);
		}"""

save_project_new = """	private void saveProject() {
		// Save current page layout
		saveCurrentPageLayout();

		// Save main layout (index page for backwards compat)
		if (pageManager != null && "index".equals(pageManager.getCurrentPage())) {
			projectDataManager.saveProject(screen, projectId);
		}"""

if save_project_old in content:
    content = content.replace(save_project_old, save_project_new)
    print("Fixed NPE in saveProject()")

with open(file_path, "w") as f:
    f.write(content)
