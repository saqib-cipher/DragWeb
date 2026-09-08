import subprocess

out = subprocess.check_output(['git', 'show', 'ee70597:app/src/main/java/sketchweb/gl/LogicBlockActivity.java'], text=True, encoding='utf-8')

# Ensure public static fields
out = out.replace('private String projectId = "";', 'public static String projectId = "";')
out = out.replace('private String pageName = "";', 'public static String pageName = "";')
out = out.replace('public String projectId = "";', 'public static String projectId = "";')
out = out.replace('public String pageName = "";', 'public static String pageName = "";')

with open('app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'w', encoding='utf-8') as f:
    f.write(out)

print("Restored LogicBlockActivity from commit ee70597!")
